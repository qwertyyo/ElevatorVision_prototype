package com.example.elevatorvision.yolo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

/**
 * YOLO11 TFLite 추론 (Ultralytics → onnx2tf 변환본).
 * 입력 [1,640,640,3] float32 RGB 0~1, 출력 [1, 4+클래스수, 8400]:
 * 채널 0~3 = cx,cy,w,h (640 입력 기준 픽셀), 4~ = 클래스 점수(sigmoid 적용됨, objectness 없음), NMS는 앱에서.
 * 자세한 명세: D:\elevator_dataset\runs\rf_v3_yolo11s\TFLITE_SPEC.md
 * 입력은 ImageUtils가 만든 정사각형 크롭이라 레터박스 패딩 없이 리사이즈만 하면 학습 전처리와 같다.
 */
class YoloDetector(context: Context) {

    private val interpreter: Interpreter
    private val inputSize = 640

    private val labels: List<String>

    // 출력 텐서 모양에서 읽는다 (클래스 수가 바뀌어도 코드 수정 불필요)
    private val numChannels: Int
    private val numAnchors: Int
    private val numClasses: Int

    // close() 이후에 들어온 추론 요청은 네이티브 크래시 대신 빈 결과로 처리한다.
    @Volatile
    private var closed = false

    init {
        val modelBuffer = loadModelFile(context, MODEL_FILE)
        interpreter = Interpreter(modelBuffer)

        labels = context.assets.open("labels.txt")
            .bufferedReader()
            .readLines()
            .filter { it.isNotBlank() }

        val shape = interpreter.getOutputTensor(0).shape()   // [1, 4+nc, 8400]
        numChannels = shape[1]
        numAnchors = shape[2]
        numClasses = numChannels - 4
        if (numClasses != labels.size) {
            Log.w("YOLO", "모델 클래스 수($numClasses)와 labels.txt 줄 수(${labels.size})가 다름")
        }

        Log.d("YOLO", "YOLO11 Interpreter initialized: classes=$numClasses anchors=$numAnchors")
    }

    /** YOLO 추론: 반환 타입은 DetectionResult만 */
    @Synchronized
    fun detect(bitmap: Bitmap): List<DetectionResult> {
        if (closed) return emptyList()
        val inputBuffer = bitmapToFloatBuffer(bitmap)

        val output = Array(1) { Array(numChannels) { FloatArray(numAnchors) } }
        interpreter.run(inputBuffer, output)
        val out = output[0]   // out[채널][앵커]

        val raw = mutableListOf<DetectionResult>()

        // 1) score threshold로 1차 필터 (클래스 점수 최댓값 = 신뢰도)
        val scoreThreshold = 0.5f

        for (i in 0 until numAnchors) {
            var score = 0f
            var classId = -1
            for (c in 0 until numClasses) {
                val s = out[4 + c][i]
                if (s > score) {
                    score = s
                    classId = c
                }
            }
            if (score < scoreThreshold) continue

            val cx = out[0][i]
            val cy = out[1][i]
            val w = out[2][i]
            val h = out[3][i]

            var left = cx - w / 2f
            var top = cy - h / 2f
            var right = cx + w / 2f
            var bottom = cy + h / 2f

            // 2) 좌표 클램프 (0~640)
            left = left.coerceIn(0f, inputSize.toFloat())
            top = top.coerceIn(0f, inputSize.toFloat())
            right = right.coerceIn(0f, inputSize.toFloat())
            bottom = bottom.coerceIn(0f, inputSize.toFloat())

            // 너무 찌그러진 박스 제거(선택)
            if (right <= left || bottom <= top) continue

            raw.add(
                DetectionResult(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    confidence = score,
                    classId = classId,
                    className = labels.getOrNull(classId) ?: "Unknown"   // 🌟 추가
                )
            )
        }

        // 3) NMS 적용 (겹치는 박스 제거)
        val nmsThreshold = 0.45f
        val maxDetections = 20

        return nonMaxSuppression(
            boxes = raw,
            iouThreshold = nmsThreshold,
            maxDetections = maxDetections
        )
    }

    /** Bitmap → Float32 입력 버퍼 (1,640,640,3) */
    private fun bitmapToFloatBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(1 * inputSize * inputSize * 3 * 4)
            .order(ByteOrder.nativeOrder())

        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        var idx = 0
        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val pixel = pixels[idx++]
                buffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
                buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
                buffer.putFloat((pixel and 0xFF) / 255f)          // B
            }
        }

        buffer.rewind()
        return buffer
    }
    private fun nonMaxSuppression(
        boxes: List<DetectionResult>,
        iouThreshold: Float,
        maxDetections: Int
    ): List<DetectionResult> {
        // confidence 높은 순
        val sorted = boxes.sortedByDescending { it.confidence }.toMutableList()
        val selected = mutableListOf<DetectionResult>()

        while (sorted.isNotEmpty() && selected.size < maxDetections) {
            val best = sorted.removeAt(0)
            selected.add(best)

            val it = sorted.iterator()
            while (it.hasNext()) {
                val other = it.next()

                // 같은 classId끼리만 NMS(일반적)
                if (other.classId != best.classId) continue

                val iou = iou(best, other)
                if (iou > iouThreshold) {
                    it.remove()
                }
            }
        }
        return selected
    }

    private fun iou(a: DetectionResult, b: DetectionResult): Float {
        val interLeft = maxOf(a.left, b.left)
        val interTop = maxOf(a.top, b.top)
        val interRight = minOf(a.right, b.right)
        val interBottom = minOf(a.bottom, b.bottom)

        val interW = (interRight - interLeft).coerceAtLeast(0f)
        val interH = (interBottom - interTop).coerceAtLeast(0f)
        val interArea = interW * interH

        val areaA = (a.right - a.left).coerceAtLeast(0f) * (a.bottom - a.top).coerceAtLeast(0f)
        val areaB = (b.right - b.left).coerceAtLeast(0f) * (b.bottom - b.top).coerceAtLeast(0f)

        val union = areaA + areaB - interArea
        return if (union <= 0f) 0f else interArea / union
    }

    /** 모델 로딩 */
    private fun loadModelFile(context: Context, filename: String): ByteBuffer {
        val bytes = context.assets.open(filename).readBytes()
        return ByteBuffer
            .allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .apply { rewind() }
    }
    fun getLabelName(classId: Int): String {
        return labels.getOrNull(classId) ?: "Unknown"
    }

    /** 인터프리터의 네이티브 메모리 해제. 진행 중인 detect()가 끝난 뒤에 실행된다(@Synchronized). */
    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        interpreter.close()
    }

    companion object {
        private const val MODEL_FILE = "yolo11s_elevator_v1_fp16.tflite"
    }
}
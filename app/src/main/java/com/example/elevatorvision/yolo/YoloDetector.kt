package com.example.elevatorvision.yolo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max

class YoloDetector(context: Context) {

    private val interpreter: Interpreter
    private val inputSize = 640

    private val labels: List<String>

    init {
        val modelBuffer = loadModelFile(context, "yolov5s-fp16.tflite")
        interpreter = Interpreter(modelBuffer)



        labels = context.assets.open("labels.txt")
            .bufferedReader()
            .readLines()

        Log.d("YOLO", "YOLO Interpreter initialized")
    }

    /** YOLO 추론: 반환 타입은 DetectionResult만 */
    fun detect(bitmap: Bitmap): List<DetectionResult> {
        val inputBuffer = bitmapToFloatBuffer(bitmap)

        val output = Array(1) { Array(25200) { FloatArray(10) } }
        interpreter.run(inputBuffer, output)

        val raw = mutableListOf<DetectionResult>()

        // 1) score threshold로 1차 필터
        val scoreThreshold = 0.6f

        for (i in 0 until 25200) {
            val row = output[0][i]

            val objConf = row[4]
            if (objConf < scoreThreshold) continue

            var maxClassScore = 0f
            var classId = -1
            for (c in 5 until 10) {
                val s = row[c]
                if (s > maxClassScore) {
                    maxClassScore = s
                    classId = c - 5
                }
            }

            val score = objConf * maxClassScore
            if (score < scoreThreshold) continue

            val cx = row[0] * inputSize
            val cy = row[1] * inputSize
            val w = row[2] * inputSize
            val h = row[3] * inputSize

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
                    classId = classId
                )
            )
        }

        // 3) NMS 적용 (겹치는 박스 제거)
        val nmsThreshold = 0.45f
        val maxDetections = 10

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
}
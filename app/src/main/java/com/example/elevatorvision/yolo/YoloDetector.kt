package com.example.elevatorvision.yolo

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YoloDetector(context: Context) {

    private val interpreter: Interpreter

    private val inputSize = 640
    private val inputChannels = 3
    private val inputBuffer: ByteBuffer

    init {
        val modelBuffer = loadModelFile(context, "yolov5s-fp16.tflite")
        interpreter = Interpreter(modelBuffer)

        // 입력 버퍼 (float32)
        inputBuffer = ByteBuffer.allocateDirect(
            4 * inputSize * inputSize * inputChannels
        ).order(ByteOrder.nativeOrder())

        Log.d("YOLO", "YOLO Interpreter initialized")
    }

    private fun loadModelFile(context: Context, filename: String): ByteBuffer {
        val inputStream = context.assets.open(filename)
        val bytes = inputStream.readBytes()
        return ByteBuffer
            .allocateDirect(bytes.size)
            .order(ByteOrder.nativeOrder())
            .put(bytes)
            .apply { rewind() }
    }

    /**
     * 🔹 더미 추론 (모델이 정상 동작하는지 확인용)
     */
    fun runDummyInference() {
        inputBuffer.rewind()

        // 전부 0으로 채움 (검은 이미지)
        repeat(inputSize * inputSize * inputChannels) {
            inputBuffer.putFloat(0f)
        }

        val output = Array(1) { Array(25200) { FloatArray(85) } }

        interpreter.run(inputBuffer, output)

        Log.d("YOLO", "Dummy inference done. First value = ${output[0][0][0]}")
    }

    /**
     * 🔹 이후 실제 카메라 프레임용 (다음 단계에서 사용)
     */
    fun detect(bitmap: Bitmap) {
        val inputBuffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        for (y in 0 until 640) {
            for (x in 0 until 640) {
                val pixel = bitmap.getPixel(x, y)

                inputBuffer.putFloat(((pixel shr 16) and 0xFF) / 255f) // R
                inputBuffer.putFloat(((pixel shr 8) and 0xFF) / 255f)  // G
                inputBuffer.putFloat((pixel and 0xFF) / 255f)         // B
            }
        }

        val output = Array(1) { Array(25200) { FloatArray(85) } }

        interpreter.run(inputBuffer, output)

        // confidence 하나만 로그 찍기
        val conf = output[0][0][4]
        Log.d("YOLO", "confidence = $conf")
    }
}
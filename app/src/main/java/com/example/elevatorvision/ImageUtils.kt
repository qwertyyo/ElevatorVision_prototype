package com.example.elevatorvision

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.core.ImageProxy

// ✅ 모델 입력을 만들 때 "어디를 크롭했는지" 정보까지 같이 들고 다니기 위한 구조체
data class CenterCropInfo(
    val srcW: Int,
    val srcH: Int,
    val cropLeft: Int,
    val cropTop: Int,
    val cropSize: Int,
    val targetSize: Int
)

data class ModelPrep(
    val input: Bitmap,          // 640x640 (모델 입력)
    val cropInfo: CenterCropInfo // 원본(회전 후)에서 어디를 크롭했는지
)

object ImageUtils {

    fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            image.width,
            image.height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 100, out)
        val jpegBytes = out.toByteArray()

        return android.graphics.BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)
    }

    /** ✅ 회전만 (비율 유지) */
    fun rotateBitmap(src: Bitmap, rotationDegrees: Int): Bitmap {
        if (rotationDegrees == 0) return src
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        return Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
    }

    /**
     * ✅ 모델 입력용: (회전된 원본)에서 centerCrop → 정사각 → targetSize 리사이즈
     * 그리고 그 크롭 정보도 같이 반환
     */
    fun prepareModelInputCenterCrop(srcRotated: Bitmap, targetSize: Int): ModelPrep {
        val srcW = srcRotated.width
        val srcH = srcRotated.height
        val cropSize = minOf(srcW, srcH)
        val cropLeft = (srcW - cropSize) / 2
        val cropTop = (srcH - cropSize) / 2

        val cropped = Bitmap.createBitmap(srcRotated, cropLeft, cropTop, cropSize, cropSize)
        val resized = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)

        return ModelPrep(
            input = resized,
            cropInfo = CenterCropInfo(
                srcW = srcW,
                srcH = srcH,
                cropLeft = cropLeft,
                cropTop = cropTop,
                cropSize = cropSize,
                targetSize = targetSize
            )
        )
    }
}

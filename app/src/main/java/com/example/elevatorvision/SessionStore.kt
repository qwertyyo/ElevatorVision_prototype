package com.example.elevatorvision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.example.elevatorvision.kijun.KijunClassMap
import com.example.elevatorvision.yolo.DetectionResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 저장한 검사 한 건 = filesDir/sessions/{id}/image.jpg + meta.json
 * (v1과 같은 형식이라 예전에 저장한 기록도 그대로 열린다. 박스 좌표는 모델 입력 좌표, cropInfo.targetSize 가 그 크기)
 */
data class SavedSession(
    val id: String,
    val timestamp: Long,
    val imageFile: File,
    val cropInfo: CenterCropInfo?,
    val detections: List<DetectionResult>
)

object SessionStore {

    private fun root(context: Context) = File(context.filesDir, "sessions")

    fun save(
        context: Context,
        frame: Bitmap,
        detections: List<DetectionResult>,
        cropInfo: CenterCropInfo?
    ): Boolean = try {
        val sessionId = UUID.randomUUID().toString()
        val dir = File(root(context), sessionId)
        dir.mkdirs()

        FileOutputStream(File(dir, "image.jpg")).use {
            frame.compress(Bitmap.CompressFormat.JPEG, 95, it)
        }

        val json = JSONObject()
        json.put("sessionId", sessionId)
        json.put("timestamp", System.currentTimeMillis())
        cropInfo?.let { ci ->
            json.put("cropInfo", JSONObject().apply {
                put("srcW", ci.srcW)
                put("srcH", ci.srcH)
                put("cropLeft", ci.cropLeft)
                put("cropTop", ci.cropTop)
                put("cropSize", ci.cropSize)
                put("targetSize", ci.targetSize)
                put("extraRotationDegrees", ci.extraRotationDegrees)
            })
        }
        val arr = JSONArray()
        detections.forEach { d ->
            arr.put(JSONObject().apply {
                put("classId", d.classId)
                put("className", d.className ?: "Unknown")
                put("confidence", d.confidence)
                put("left", d.left)
                put("top", d.top)
                put("right", d.right)
                put("bottom", d.bottom)
            })
        }
        json.put("detections", arr)
        File(dir, "meta.json").writeText(json.toString())
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }

    /** 최신순 */
    fun loadAll(context: Context): List<SavedSession> =
        root(context).listFiles()
            ?.mapNotNull { load(it) }
            ?.sortedByDescending { it.timestamp }
            ?: emptyList()

    fun load(context: Context, sessionId: String): SavedSession? = load(File(root(context), sessionId))

    private fun load(dir: File): SavedSession? {
        val meta = File(dir, "meta.json")
        val img = File(dir, "image.jpg")
        if (!meta.exists() || !img.exists()) return null
        return try {
            val json = JSONObject(meta.readText())
            val cropInfo = json.optJSONObject("cropInfo")?.let { ci ->
                CenterCropInfo(
                    srcW = ci.optInt("srcW"),
                    srcH = ci.optInt("srcH"),
                    cropLeft = ci.optInt("cropLeft"),
                    cropTop = ci.optInt("cropTop"),
                    cropSize = ci.optInt("cropSize"),
                    targetSize = ci.optInt("targetSize"),
                    extraRotationDegrees = ci.optInt("extraRotationDegrees", 0)
                )
            }
            val arr = json.optJSONArray("detections") ?: JSONArray()
            val dets = buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        DetectionResult(
                            classId = o.optInt("classId"),
                            className = if (o.has("className")) o.optString("className") else null,
                            confidence = o.optDouble("confidence").toFloat(),
                            left = o.optDouble("left").toFloat(),
                            top = o.optDouble("top").toFloat(),
                            right = o.optDouble("right").toFloat(),
                            bottom = o.optDouble("bottom").toFloat()
                        )
                    )
                }
            }
            SavedSession(
                id = json.optString("sessionId", dir.name),
                timestamp = json.optLong("timestamp", dir.lastModified()),
                imageFile = img,
                cropInfo = cropInfo,
                detections = dets
            )
        } catch (e: Exception) {
            null
        }
    }

    fun delete(context: Context, sessionId: String): Boolean = try {
        val dir = File(root(context), sessionId)
        if (dir.exists()) dir.deleteRecursively() else true
    } catch (e: Exception) {
        false
    }

    fun decodeThumbnail(path: String, reqSize: Int = 520): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)
        var inSampleSize = 1
        while (opts.outHeight / 2 / inSampleSize >= reqSize && opts.outWidth / 2 / inSampleSize >= reqSize) {
            inSampleSize *= 2
        }
        val opts2 = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return runCatching { BitmapFactory.decodeFile(path, opts2) }.getOrNull()
    }
}

/** "권상기 · 제어반 외 2" (신뢰도 높은 순, 같은 부품은 한 번만) */
fun partsSummary(detections: List<DetectionResult>, show: Int = 2): String {
    val names = detections.sortedByDescending { it.confidence }
        .map { KijunClassMap.shortName(it.className ?: "Unknown") }
        .distinct()
    if (names.isEmpty()) return "인식된 부품 없음"
    val head = names.take(show).joinToString(" · ")
    return if (names.size > show) "$head 외 ${names.size - show}" else head
}

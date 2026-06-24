package com.example.elevatorvision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.elevatorvision.ui.BoundingBoxOverlay
import com.example.elevatorvision.ui.theme.ElevatorVisionTheme
import com.example.elevatorvision.yolo.DetectionResult
import com.example.elevatorvision.yolo.YoloDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ElevatorVisionTheme {
                Surface(Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

/* ======================= Simple Nav ======================= */

private sealed class Screen {
    object Camera : Screen()
    object Storage : Screen()
    data class StorageDetail(val sessionId: String) : Screen()
}

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Camera) }

    when (val s = screen) {
        Screen.Camera -> CameraScreen(
            onOpenStorage = { screen = Screen.Storage }
        )
        Screen.Storage -> StorageScreen(
            onBack = { screen = Screen.Camera },
            onOpenDetail = { id -> screen = Screen.StorageDetail(id) }
        )
        is Screen.StorageDetail -> StorageDetailScreen(
            sessionId = s.sessionId,
            onBack = { screen = Screen.Storage }
        )
    }
}

/* ======================= Camera ======================= */

@OptIn(ExperimentalGetImage::class)
@Composable
private fun CameraScreen(
    onOpenStorage: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val labels = remember {
        context.assets.open("labels.txt")
            .bufferedReader()
            .readLines()
    }

    /* ---------- Permission ---------- */
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            hasPermission = it
        }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    /* ---------- YOLO ---------- */
    val yoloDetector = remember { YoloDetector(context) }

    /* ---------- LIVE ---------- */
    var liveFrame by remember { mutableStateOf<Bitmap?>(null) }
    var liveDetections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var liveCropInfo by remember { mutableStateOf<CenterCropInfo?>(null) }

    /* ---------- CAPTURED ---------- */
    var capturedFrame by remember { mutableStateOf<Bitmap?>(null) }
    var capturedDetections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var capturedCropInfo by remember { mutableStateOf<CenterCropInfo?>(null) }

    val isCaptured = capturedFrame != null

    /* ---------- Save ---------- */
    fun saveCaptured(): Boolean {
        return try {
            val sessionId = UUID.randomUUID().toString()
            val dir = File(context.filesDir, "sessions/$sessionId")
            dir.mkdirs()

            FileOutputStream(File(dir, "image.jpg")).use {
                capturedFrame!!.compress(Bitmap.CompressFormat.JPEG, 95, it)
            }

            val root = JSONObject()
            root.put("sessionId", sessionId)
            root.put("timestamp", System.currentTimeMillis())

            capturedCropInfo?.let { ci ->
                root.put("cropInfo", JSONObject().apply {
                    put("srcW", ci.srcW)
                    put("srcH", ci.srcH)
                    put("cropLeft", ci.cropLeft)
                    put("cropTop", ci.cropTop)
                    put("cropSize", ci.cropSize)
                    put("targetSize", ci.targetSize)
                })
            }

            val arr = JSONArray()
            capturedDetections.forEach { d ->
                arr.put(JSONObject().apply {
                    put("classId", d.classId)
                    put("confidence", d.confidence)
                    put("left", d.left)
                    put("top", d.top)
                    put("right", d.right)
                    put("bottom", d.bottom)
                })
            }
            root.put("detections", arr)

            File(dir, "meta.json").writeText(root.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /* ======================= UI ======================= */

    Box(Modifier.fillMaxSize()) {

        if (!hasPermission) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("카메라 권한 요청")
                }
            }
            return@Box
        }

        /* ---------- LIVE ---------- */
        if (!isCaptured) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val provider = ProcessCameraProvider.getInstance(ctx).get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val analysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(ContextCompat.getMainExecutor(ctx)) { image ->
                        try {
                            val bmp = ImageUtils.imageProxyToBitmap(image)
                            val rotated = ImageUtils.rotateBitmap(
                                bmp,
                                image.imageInfo.rotationDegrees
                            )
                            liveFrame = rotated

                            val prep = ImageUtils.prepareModelInputCenterCrop(rotated, 640)
                            liveCropInfo = prep.cropInfo
                            liveDetections = yoloDetector.detect(prep.input)
                        } finally {
                            image.close()
                        }
                    }

                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )

                    previewView
                }
            )

            BoundingBoxOverlay(
                modifier = Modifier.fillMaxSize(),
                detections = liveDetections,
                labels = labels,
                showInfoIcons = false,
                enablePopup = false,
                cropInfo = liveCropInfo

            )

            // 하단 버튼 (저장소 + 촬영)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .windowInsetsPadding(WindowInsets.systemBars),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = onOpenStorage) {
                    Text("저장소")
                }
                FloatingActionButton(
                    onClick = {
                        capturedFrame = liveFrame
                        capturedDetections = liveDetections
                        capturedCropInfo = liveCropInfo
                    }
                ) {
                    Text("●")
                }
            }
        }

        /* ---------- CAPTURED ---------- */
        else {
            Image(
                bitmap = capturedFrame!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            BoundingBoxOverlay(
                modifier = Modifier.fillMaxSize(),
                detections = capturedDetections,
                labels = labels,
                showInfoIcons = true,
                enablePopup = true,
                cropInfo = capturedCropInfo
            )

            // 상단 LIVE 복귀
            TextButton(
                onClick = {
                    capturedFrame = null
                    capturedDetections = emptyList()
                    capturedCropInfo = null
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()   // ✅ 상태바(시계/노치) 영역만큼 아래로 밀기
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    )
            ) {
                Text("< LIVE")
            }

            // 하단 저장 버튼
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp)
                    .windowInsetsPadding(WindowInsets.systemBars),
                contentAlignment = Alignment.BottomCenter
            )  {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            val ok = saveCaptured()
                            Toast.makeText(
                                context,
                                if (ok) "저장 완료" else "저장 실패",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    ) {
                        Text("저장")
                    }

                    Button(
                        onClick = onOpenStorage
                    ) {
                        Text("저장소")
                    }
                }
            }
        }
    }
}

/* ======================= Storage List ======================= */

private data class SessionItem(
    val id: String,
    val timestamp: Long,
    val imageFile: File
)

@Composable
private fun StorageScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val context = LocalContext.current
    var sessions by remember { mutableStateOf(loadSessions(context)) }
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.KOREA) }

    var deleteTarget by remember { mutableStateOf<SessionItem?>(null) }

    fun deleteSession(sessionId: String): Boolean {
        return try {
            val dir = File(context.filesDir, "sessions/$sessionId")
            if (dir.exists()) dir.deleteRecursively() else true
        } catch (e: Exception) {
            false
        }
    }

    fun decodeThumbnail(path: String, reqSize: Int = 520): Bitmap? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, opts)

        var inSampleSize = 1
        val halfH = opts.outHeight / 2
        val halfW = opts.outWidth / 2
        while (halfH / inSampleSize >= reqSize && halfW / inSampleSize >= reqSize) {
            inSampleSize *= 2
        }

        val opts2 = BitmapFactory.Options().apply { this.inSampleSize = inSampleSize }
        return runCatching { BitmapFactory.decodeFile(path, opts2) }.getOrNull()
    }

    Column(Modifier.fillMaxSize()) {

        TextButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            Text("< 카메라")
        }

        Divider()

        LazyColumn(Modifier.fillMaxSize()) {
            items(
                items = sessions,
                key = { it.id } // ✅ 안정화(강추)
            ) { s ->

                val thumb by produceState<Bitmap?>(initialValue = null, key1 = s.imageFile.absolutePath) {
                    value = withContext(Dispatchers.IO) {
                        decodeThumbnail(s.imageFile.absolutePath, reqSize = 520)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onOpenDetail(s.id) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val thumbSize = 140.dp

                        Box(
                            modifier = Modifier
                                .size(thumbSize)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb!!.asImageBitmap(),
                                    contentDescription = "thumb",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("로딩…")
                            }
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "촬영 시간: ${fmt.format(Date(s.timestamp))}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "세션 ID: ${s.id.take(8)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    IconButton(
                        onClick = { deleteTarget = s } // ✅ 여기서 상태 세팅됨
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "delete"
                        )
                    }
                }

                Divider()
            }
        }

        // ✅✅✅ 여기! Column "맨 아래", LazyColumn 밖에 AlertDialog 추가
        if (deleteTarget != null) {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("삭제 확인") },
                text = {
                    Text("이 항목을 삭제할까요?\n삭제하면 복구할 수 없습니다.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = deleteTarget
                            if (target != null) {
                                val ok = deleteSession(target.id)
                                if (ok) {
                                    sessions = loadSessions(context) // ✅ 리스트 갱신
                                    Toast.makeText(context, "삭제 완료", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "삭제 실패", Toast.LENGTH_SHORT).show()
                                }
                            }
                            deleteTarget = null
                        }
                    ) { Text("삭제") }
                },
                dismissButton = {
                    TextButton(onClick = { deleteTarget = null }) { Text("취소") }
                }
            )
        }
    }
}


/* ======================= Storage Detail ======================= */

private data class StoredDetail(
    val sessionId: String,
    val timestamp: Long,
    val imageFile: File,
    val cropInfo: CenterCropInfo?,
    val detections: List<DetectionResult>
)

@Composable
private fun StorageDetailScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val labels = remember {
        context.assets.open("labels.txt")
            .bufferedReader()
            .readLines()
    }

    var detail by remember { mutableStateOf<StoredDetail?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(sessionId) {
        loading = true
        detail = withContext(Dispatchers.IO) { loadDetail(context, sessionId) }
        bitmap = withContext(Dispatchers.IO) {
            detail?.let { BitmapFactory.decodeFile(it.imageFile.absolutePath) }
        }
        loading = false
    }

    Box(Modifier.fillMaxSize()) {
        if (loading) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
            return@Box
        }

        val d = detail
        val bmp = bitmap
        if (d == null || bmp == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("세션을 불러오지 못했습니다.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("뒤로") }
            }
            return@Box
        }

        // 저장된 이미지
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        // ✅ 저장된 탐지 + cropInfo로 오버레이 그대로 복원
        BoundingBoxOverlay(
            modifier = Modifier.fillMaxSize(),
            detections = d.detections,
            labels = labels,
            showInfoIcons = true,
            enablePopup = true,
            cropInfo = d.cropInfo
        )

        // 상단 뒤로
        TextButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .windowInsetsPadding(WindowInsets.systemBars)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
        ) {
            Text("< 저장소")
        }
    }
}

/* ======================= utils ======================= */

private fun loadSessions(context: android.content.Context): List<SessionItem> {
    val root = File(context.filesDir, "sessions")
    if (!root.exists()) return emptyList()

    return root.listFiles()
        ?.mapNotNull { dir ->
            val meta = File(dir, "meta.json")
            val img = File(dir, "image.jpg")
            if (!meta.exists() || !img.exists()) return@mapNotNull null

            try {
                val json = JSONObject(meta.readText())
                SessionItem(
                    id = json.getString("sessionId"),
                    timestamp = json.getLong("timestamp"),
                    imageFile = img
                )
            } catch (e: Exception) {
                null
            }
        }
        ?.sortedByDescending { it.timestamp }
        ?: emptyList()
}

private fun loadDetail(context: android.content.Context, sessionId: String): StoredDetail? {
    val dir = File(context.filesDir, "sessions/$sessionId")
    val meta = File(dir, "meta.json")
    val img = File(dir, "image.jpg")
    if (!meta.exists() || !img.exists()) return null

    return try {
        val json = JSONObject(meta.readText())
        val ts = json.optLong("timestamp", 0L)

        val cropInfo = json.optJSONObject("cropInfo")?.let { ci ->
            CenterCropInfo(
                srcW = ci.optInt("srcW"),
                srcH = ci.optInt("srcH"),
                cropLeft = ci.optInt("cropLeft"),
                cropTop = ci.optInt("cropTop"),
                cropSize = ci.optInt("cropSize"),
                targetSize = ci.optInt("targetSize")
            )
        }

        val arr = json.optJSONArray("detections") ?: JSONArray()
        val dets = buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                add(
                    DetectionResult(
                        classId = o.optInt("classId"),
                        confidence = o.optDouble("confidence").toFloat(),
                        left = o.optDouble("left").toFloat(),
                        top = o.optDouble("top").toFloat(),
                        right = o.optDouble("right").toFloat(),
                        bottom = o.optDouble("bottom").toFloat()
                    )
                )
            }
        }

        StoredDetail(
            sessionId = sessionId,
            timestamp = ts,
            imageFile = img,
            cropInfo = cropInfo,
            detections = dets
        )
    } catch (e: Exception) {
        null
    }
}

package com.example.elevatorvision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.elevatorvision.ui.BoundingBoxOverlay
import com.example.elevatorvision.ui.CircleIconButton
import com.example.elevatorvision.ui.HomeIndicatorBar
import com.example.elevatorvision.ui.ShutterButton
import com.example.elevatorvision.ui.StatusPill
import com.example.elevatorvision.ui.theme.BrandBlue
import com.example.elevatorvision.ui.theme.BrandGreen
import com.example.elevatorvision.ui.theme.BrandOrange
import com.example.elevatorvision.ui.theme.DangerRed
import com.example.elevatorvision.ui.theme.DetectionAccent
import com.example.elevatorvision.ui.theme.ElevatorVisionTheme
import com.example.elevatorvision.ui.theme.OutlineDark
import com.example.elevatorvision.ui.theme.SurfaceDark
import com.example.elevatorvision.ui.theme.SurfaceVariantDark
import com.example.elevatorvision.ui.theme.TextSecondary
import com.example.elevatorvision.ui.theme.TextTertiary
import com.example.elevatorvision.yolo.DetectionResult
import com.example.elevatorvision.yolo.YoloDetector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 다크 테마 전용 앱이므로 상태바/내비게이션바 아이콘을 항상 밝은 색으로 고정
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        // 카메라 프리뷰 등 콘텐츠가 상태바/내비게이션바 뒤까지 완전히 채워지도록 엣지투엣지 활성화
        WindowCompat.setDecorFitsSystemWindows(window, false)

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
    object Start : Screen()
    object Camera : Screen()
    object Storage : Screen()
    data class StorageDetail(val sessionId: String) : Screen()
}

@Composable
private fun AppRoot() {
    var screen by remember { mutableStateOf<Screen>(Screen.Start) }

    BackHandler(enabled = screen != Screen.Start) {
        screen = when (val s = screen) {
            is Screen.StorageDetail -> Screen.Storage
            Screen.Storage -> Screen.Camera
            Screen.Camera -> Screen.Start
            Screen.Start -> Screen.Start
        }
    }

    when (val s = screen) {
        Screen.Start -> StartScreen(
            onStartInspection = { screen = Screen.Camera }
        )
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

/* ======================= Start ======================= */

@Composable
private fun StartScreen(
    onStartInspection: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .background(SurfaceDark)
                    .border(1.dp, OutlineDark, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo_icon),
                    contentDescription = "한국승강기안전공단 로고",
                    modifier = Modifier.size(60.dp)
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = "ElevatorVision",
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "AI · AR 승강기 검사 도우미",
                color = BrandBlue,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(48.dp))

            Text(
                text = "한국승강기안전공단",
                color = TextSecondary,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
            Text(
                text = "KoELSA (Korea Elevator Safety Agency)",
                color = TextTertiary,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(29.dp),
                        spotColor = BrandBlue,
                        ambientColor = BrandBlue
                    )
                    .clip(RoundedCornerShape(29.dp))
                    .background(BrandBlue)
                    .clickable(onClick = onStartInspection),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "검사 시작하기",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("›", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(14.dp))
            HomeIndicatorBar()
        }
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

    /* ---------- 카메라 컨트롤 (손전등, 줌, 포커스용) ---------- */
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var flashOn by remember { mutableStateOf(false) }
    var zoomRatio by remember { mutableStateOf(1f) }
    var minZoomRatio by remember { mutableStateOf(1f) }
    var maxZoomRatio by remember { mutableStateOf(1f) }
    var focusMarker by remember { mutableStateOf<Offset?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // YOLO 추론은 무거운 작업이라 메인 스레드에서 돌리면 화면 렌더링 자체가 끊긴다.
    // 전용 백그라운드 스레드에서 실행해서 메인 스레드(=Compose 렌더링, 제스처 처리)를 막지 않게 한다.
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { analysisExecutor.shutdown() }
    }

    // 화면(UI)은 세로로 완전히 고정한다(매니페스트 screenOrientation="portrait").
    // Preview/오버레이 좌표계는 절대 안 건드리고, 대신 폰을 물리적으로 얼마나 기울여
    // 들고 있는지만 가속도계로 추적해서, 모델에 넣을 크롭과 라벨 텍스트 방향을
    // 보정하는 데 쓴다.
    var physicalRotation by remember { mutableStateOf(Surface.ROTATION_0) }
    DisposableEffect(Unit) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                physicalRotation = when (orientation) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
            }
        }
        if (listener.canDetectOrientation()) listener.enable()
        onDispose { listener.disable() }
    }

    /* ---------- LIVE ---------- */
    var liveFrame by remember { mutableStateOf<Bitmap?>(null) }
    var liveDetections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var liveCropInfo by remember { mutableStateOf<CenterCropInfo?>(null) }

    /* ---------- CAPTURED ---------- */
    var capturedFrame by remember { mutableStateOf<Bitmap?>(null) }
    var capturedDetections by remember { mutableStateOf<List<DetectionResult>>(emptyList()) }
    var capturedCropInfo by remember { mutableStateOf<CenterCropInfo?>(null) }

    val isCaptured = capturedFrame != null

    // 부품 클릭 시 뜨는 검사기준/표준화 바텀시트가 촬영 UI(줌/셔터/손전등 등)와 겹쳐 보이지
    // 않도록, 시트가 떠 있는 동안은 촬영 UI를 숨긴다.
    var detailSheetVisible by remember { mutableStateOf(false) }

    BackHandler(enabled = isCaptured) {
        capturedFrame = null
        capturedDetections = emptyList()
        capturedCropInfo = null
        detailSheetVisible = false
    }

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
                    put("extraRotationDegrees", ci.extraRotationDegrees)
                })
            }

            val arr = JSONArray()
            capturedDetections.forEach { d ->
                arr.put(JSONObject().apply {
                    put("classId", d.classId)
                    put("className", d.className ?: labels.getOrNull(d.classId) ?: "Unknown")
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

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

                    analysis.setAnalyzer(analysisExecutor) { image ->
                        try {
                            val bmp = ImageUtils.imageProxyToBitmap(image)
                            val baseRotationDegrees = image.imageInfo.rotationDegrees
                            val rotated = ImageUtils.rotateBitmap(bmp, baseRotationDegrees)
                            liveFrame = rotated

                            // 화면(프리뷰/오버레이)이 실제로 그려지는 기준(baseRotationDegrees)은
                            // 그대로 두고, "지금 폰을 물리적으로 얼마나 더 돌려 잡고 있는지"만큼만
                            // 모델 입력용 크롭에 추가로 적용한다. CameraInfo.getSensorRotationDegrees()는
                            // targetRotation을 실제로 바꾸지 않고도 그 차이를 계산해준다.
                            val sensorRotationForPhysical = camera?.cameraInfo
                                ?.getSensorRotationDegrees(physicalRotation)
                            val extraRotationDegrees = if (sensorRotationForPhysical != null) {
                                ((sensorRotationForPhysical - baseRotationDegrees) % 360 + 360) % 360
                            } else 0

                            val prep = ImageUtils.prepareModelInputCenterCrop(
                                rotated,
                                640,
                                extraRotationDegrees
                            )
                            liveCropInfo = prep.cropInfo
                            liveDetections = yoloDetector.detect(prep.input)
                        } finally {
                            image.close()
                        }
                    }

                    provider.unbindAll()
                    camera = provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                    )

                    camera?.cameraInfo?.zoomState?.value?.let { zs ->
                        minZoomRatio = zs.minZoomRatio
                        maxZoomRatio = zs.maxZoomRatio
                        zoomRatio = zs.zoomRatio
                    }
                    previewViewRef = previewView

                    previewView
                }
            )

            // 핀치 줌(2손가락) + 탭 포커스 제스처 레이어 — 카메라 프리뷰 위, 인식 박스 아래
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(camera) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        val newRatio = (zoomRatio * zoomChange)
                                            .coerceIn(minZoomRatio, maxZoomRatio)
                                        zoomRatio = newRatio
                                        camera?.cameraControl?.setZoomRatio(newRatio)
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(camera, previewViewRef) {
                        detectTapGestures { offset ->
                            val pv = previewViewRef ?: return@detectTapGestures
                            val point = pv.meteringPointFactory.createPoint(offset.x, offset.y)
                            val action = FocusMeteringAction.Builder(
                                point,
                                FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                            ).setAutoCancelDuration(3, TimeUnit.SECONDS).build()
                            camera?.cameraControl?.startFocusAndMetering(action)

                            focusMarker = offset
                            coroutineScope.launch {
                                delay(700)
                                focusMarker = null
                            }
                        }
                    }
            ) {
                val marker = focusMarker
                if (marker != null) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(
                            color = DetectionAccent,
                            radius = 38f,
                            center = marker,
                            style = Stroke(width = 3f)
                        )
                    }
                }
            }

            BoundingBoxOverlay(
                modifier = Modifier.fillMaxSize(),
                detections = liveDetections,
                labels = labels,
                showInfoIcons = false,
                enablePopup = false,
                cropInfo = liveCropInfo,
                onSheetVisibleChanged = { detailSheetVisible = it }
            )

            // 상단 레터박스 바: LIVE 상태 표시
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(BrandGreen)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "AI LIVE DETECTING",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            // 하단 레터박스 바: 상태 배지 줄 + 저장소/셔터/손전등 버튼 줄
            // 부품 클릭으로 뜬 바텀시트와 겹치지 않도록, 시트가 떠 있는 동안은 숨긴다.
            if (!detailSheetVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Zoom: %.1fx".format(zoomRatio), color = TextSecondary, fontSize = 12.sp)
                        StatusPill(
                            text = if (flashOn) "FL-ON" else "FL-OFF",
                            icon = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            color = if (flashOn) BrandOrange else TextSecondary
                        )
                    }

                    Spacer(Modifier.height(18.dp))

                    Box(modifier = Modifier.fillMaxWidth()) {
                        CircleIconButton(
                            icon = Icons.Filled.PhotoLibrary,
                            contentDescription = "저장소",
                            modifier = Modifier.align(Alignment.CenterStart),
                            onClick = onOpenStorage
                        )

                        ShutterButton(
                            modifier = Modifier.align(Alignment.Center),
                            onClick = {
                                capturedFrame = liveFrame
                                capturedDetections = liveDetections
                                capturedCropInfo = liveCropInfo
                            }
                        )

                        CircleIconButton(
                            icon = if (flashOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                            contentDescription = "손전등",
                            tint = if (flashOn) BrandOrange else Color.White,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onClick = {
                                flashOn = !flashOn
                                camera?.cameraControl?.enableTorch(flashOn)
                            }
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HomeIndicatorBar()
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
                cropInfo = capturedCropInfo,
                onSheetVisibleChanged = { detailSheetVisible = it }
            )

            // 상단 레터박스 바: 뒤로가기 + 타이틀 + FREEZE 배지
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black)
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircleIconButton(
                        icon = Icons.Filled.ArrowBack,
                        contentDescription = "라이브로 돌아가기",
                        size = 40.dp,
                        onClick = {
                            capturedFrame = null
                            capturedDetections = emptyList()
                            capturedCropInfo = null
                            detailSheetVisible = false
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("캡처된 이미지", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                StatusPill(text = "FREEZE", color = DangerRed)
            }

            // 하단 레터박스 바: 저장 버튼 2종
            // 부품 클릭으로 뜬 바텀시트와 겹치지 않도록, 시트가 떠 있는 동안은 숨긴다.
            if (!detailSheetVisible) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black)
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenStorage,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceDark,
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDark)
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("저장소 보기", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }

                        Button(
                            onClick = {
                                val ok = saveCaptured()
                                Toast.makeText(
                                    context,
                                    if (ok) "저장 완료" else "저장 실패",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = BrandBlue)
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("검사항목 저장", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HomeIndicatorBar()
                }
            }
        }
    }
}

/* ======================= Storage List ======================= */

private data class SessionItem(
    val id: String,
    val timestamp: Long,
    val imageFile: File,
    val detectionCount: Int
)

@Composable
private fun StorageScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val context = LocalContext.current
    var newestFirst by remember { mutableStateOf(true) }
    var sessions by remember { mutableStateOf(loadSessions(context)) }
    val fmt = remember { SimpleDateFormat("yyyy.MM.dd (E) HH:mm", Locale.KOREA) }

    val displayedSessions = remember(sessions, newestFirst) {
        if (newestFirst) sessions else sessions.reversed()
    }

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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "뒤로",
                size = 40.dp,
                onClick = onBack
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "저장소",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            StatusPill(
                text = if (newestFirst) "최신순" else "오래된순",
                icon = Icons.Filled.SwapVert,
                color = TextSecondary,
                onClick = { newestFirst = !newestFirst }
            )
        }

        Divider(color = OutlineDark, thickness = 1.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(
                items = displayedSessions,
                key = { it.id }
            ) { s ->

                val thumb by produceState<Bitmap?>(initialValue = null, key1 = s.imageFile.absolutePath) {
                    value = withContext(Dispatchers.IO) {
                        decodeThumbnail(s.imageFile.absolutePath, reqSize = 520)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceDark)
                        .clickable { onOpenDetail(s.id) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariantDark),
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
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = fmt.format(Date(s.timestamp)),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(6.dp))
                        StatusPill(
                            text = "${s.detectionCount}건 인식됨",
                            icon = Icons.Filled.Bolt,
                            color = BrandBlue
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    CircleIconButton(
                        icon = Icons.Filled.Delete,
                        contentDescription = "delete",
                        size = 38.dp,
                        tint = DangerRed,
                        onClick = { deleteTarget = s }
                    )
                }
            }
        }

        if (deleteTarget != null) {
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text("삭제 확인") },
                text = { Text("이 항목을 삭제할까요?\n삭제하면 복구할 수 없습니다.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val target = deleteTarget
                            if (target != null) {
                                val ok = deleteSession(target.id)
                                if (ok) {
                                    sessions = loadSessions(context)
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

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
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
                Text("세션을 불러오지 못했습니다.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = onBack) { Text("뒤로") }
            }
            return@Box
        }

        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )

        BoundingBoxOverlay(
            modifier = Modifier.fillMaxSize(),
            detections = d.detections,
            labels = labels,
            showInfoIcons = true,
            enablePopup = true,
            cropInfo = d.cropInfo
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircleIconButton(
                icon = Icons.Filled.ArrowBack,
                contentDescription = "저장소로 돌아가기",
                size = 40.dp,
                onClick = onBack
            )
            Spacer(Modifier.width(10.dp))
            Text("검사 기록", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
                val count = json.optJSONArray("detections")?.length() ?: 0
                SessionItem(
                    id = json.getString("sessionId"),
                    timestamp = json.getLong("timestamp"),
                    imageFile = img,
                    detectionCount = count
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

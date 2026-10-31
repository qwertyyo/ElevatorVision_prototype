package com.example.elevatorvision

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.util.Size
import android.view.OrientationEventListener
import android.view.Surface
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.elevatorvision.kijun.KijunClassMap
import com.example.elevatorvision.ui.BackCircleButton
import com.example.elevatorvision.ui.BoundingBoxOverlay
import com.example.elevatorvision.ui.InfoChip
import com.example.elevatorvision.ui.KijunSheet
import com.example.elevatorvision.ui.PillChip
import com.example.elevatorvision.ui.ShutterButton
import com.example.elevatorvision.ui.theme.AccentCard
import com.example.elevatorvision.ui.theme.BackgroundDark
import com.example.elevatorvision.ui.theme.BrandGreen
import com.example.elevatorvision.ui.theme.BrandOrange
import com.example.elevatorvision.ui.theme.DetectionAccent
import com.example.elevatorvision.ui.theme.FreezeBg
import com.example.elevatorvision.ui.theme.FreezeFg
import com.example.elevatorvision.ui.theme.OutlineDark
import com.example.elevatorvision.ui.theme.SheetDark
import com.example.elevatorvision.ui.theme.SheetHandle
import com.example.elevatorvision.ui.theme.SurfaceDark
import com.example.elevatorvision.ui.theme.SurfaceVariantDark
import com.example.elevatorvision.ui.theme.TextMuted
import com.example.elevatorvision.ui.theme.TextPrimary
import com.example.elevatorvision.ui.theme.TextSecondary
import com.example.elevatorvision.yolo.DetectionResult
import com.example.elevatorvision.yolo.YoloDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// 한 프레임의 사진·인식 결과·크롭 정보를 한 덩어리로 묶어 둔다. 셔터는 항상 같은 프레임의 세 값을 함께 가져간다.
private data class LiveResult(
    val frame: Bitmap,
    val detections: List<DetectionResult>,
    val cropInfo: CenterCropInfo
)

// "지금 보이는 부품" 칩: 프레임마다 순서가 바뀌면 누르기 어려워서 처음 보인 순서를 유지하고,
// 이 시간 동안 안 보이면 뺀다.
private const val PART_CHIP_HOLD_MS = 2500L

private val ZOOM_PRESETS = listOf(1f, 2f, 5f)

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onOpenStorage: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var imageAnalysis by remember { mutableStateOf<ImageAnalysis?>(null) }
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
        onDispose {
            // 카메라 화면에 들어올 때마다 모델을 새로 로드하므로, 떠날 때 닫지 않으면 네이티브
            // 메모리가 계속 쌓인다. 진행 중인 추론이 끝난 뒤 닫히도록 같은 분석 스레드에 넣는다.
            analysisExecutor.execute { yoloDetector.close() }
            analysisExecutor.shutdown()
        }
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
    var liveResult by remember { mutableStateOf<LiveResult?>(null) }
    val liveDetections = remember(liveResult) {
        liveResult?.detections?.sortedByDescending { it.confidence } ?: emptyList()
    }
    var visibleParts by remember { mutableStateOf(listOf<Pair<String, Long>>()) }
    LaunchedEffect(liveResult) {
        val now = System.currentTimeMillis()
        val seen = liveDetections.mapNotNull { it.className }.toSet()
        val kept = visibleParts
            .map { (name, t) -> if (name in seen) name to now else name to t }
            .filter { (_, t) -> now - t < PART_CHIP_HOLD_MS }
        val known = kept.map { it.first }.toSet()
        visibleParts = kept + seen.filter { it !in known }.map { it to now }
    }
    var liveSheetClass by remember { mutableStateOf<String?>(null) }

    /* ---------- CAPTURED ---------- */
    var captured by remember { mutableStateOf<LiveResult?>(null) }

    BackHandler(enabled = captured != null) {
        captured = null
    }

    /* ======================= UI ======================= */

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {

        if (!hasPermission) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("부품을 인식하려면 카메라 권한이 필요합니다.", color = TextPrimary, fontSize = 15.sp)
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentCard)
                ) {
                    Text("카메라 권한 요청")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBack) { Text("첫 화면으로", color = TextSecondary) }
            }
            return@Box
        }

        val shot = captured

        /* ---------- LIVE ---------- */
        if (shot == null) {

            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val provider = ProcessCameraProvider.getInstance(ctx).get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    // 기본 분석 해상도(640x480)면 정사각형 크롭이 480px라 모델 입력(800)보다 작다.
                    // 1280x960(미리보기와 같은 4:3)을 받아서 크롭이 모델 입력보다 크게 한다.
                    val analysis = ImageAnalysis.Builder()
                        .setResolutionSelector(
                            ResolutionSelector.Builder()
                                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        Size(1280, 960),
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                    )
                                )
                                .build()
                        )
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    analysis.setAnalyzer(analysisExecutor) { image ->
                        try {
                            val bmp = ImageUtils.imageProxyToBitmap(image)
                            val baseRotationDegrees = image.imageInfo.rotationDegrees
                            val rotated = ImageUtils.rotateBitmap(bmp, baseRotationDegrees)

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
                                yoloDetector.inputSize,
                                extraRotationDegrees
                            )
                            val detections = yoloDetector.detect(prep.input)
                            // 사진·박스·크롭 정보를 추론이 끝난 뒤 한 번에 갱신한다. 따로 갱신하면
                            // 추론 도중 셔터를 눌렀을 때 "새 프레임 사진 + 이전 프레임 박스"가 저장된다.
                            liveResult = LiveResult(rotated, detections, prep.cropInfo)
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

                    cameraProvider = provider
                    imageAnalysis = analysis

                    // 캡처 화면에서 돌아오면 카메라를 다시 연결하면서 손전등이 꺼지므로,
                    // 켜둔 상태였다면 다시 켜서 화면 표시와 맞춘다.
                    if (flashOn) camera?.cameraControl?.enableTorch(true)

                    camera?.cameraInfo?.zoomState?.value?.let { zs ->
                        minZoomRatio = zs.minZoomRatio
                        maxZoomRatio = zs.maxZoomRatio
                    }
                    // 다시 연결하면 줌이 1배로 돌아가므로 쓰던 배율을 다시 건다
                    camera?.cameraControl?.setZoomRatio(zoomRatio)
                    previewViewRef = previewView

                    previewView
                }
            )

            // 카메라는 Activity 수명에 묶여 있어서, 직접 연결을 끊지 않으면 캡처 화면이나
            // 저장소 화면으로 넘어가도 카메라와 YOLO 분석이 뒤에서 계속 돈다.
            DisposableEffect(Unit) {
                onDispose {
                    imageAnalysis?.clearAnalyzer()
                    cameraProvider?.unbindAll()
                    imageAnalysis = null
                    camera = null
                    previewViewRef = null
                }
            }

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
                cropInfo = liveResult?.cropInfo,
                labelTopInset = topBarHeight(),
                onBoxClick = { i -> liveSheetClass = liveDetections.getOrNull(i)?.className }
            )

            // 상단 바: 뒤로 + 인식 상태 + 부품 수
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackCircleButton(
                    contentDescription = "첫 화면으로",
                    background = Color.White.copy(alpha = 0.12f),
                    onClick = onBack
                )
                Spacer(Modifier.width(12.dp))
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BrandGreen)
                )
                Spacer(Modifier.width(7.dp))
                Text("AI 인식 중", color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("부품 ${liveDetections.size}개", color = TextMuted, fontSize = 13.sp)
            }

            // 하단 패널: 지금 보이는 부품 칩 + 줌 + 저장소/셔터/손전등
            // 검사기준 시트가 떠 있는 동안은 겹치지 않게 숨긴다.
            if (liveSheetClass == null) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.86f))
                        .navigationBarsPadding()
                        .padding(top = 14.dp, bottom = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("지금 보이는 부품 · 누르면 검사기준", color = TextMuted, fontSize = 12.sp)
                        if (visibleParts.isEmpty()) {
                            Text(
                                "부품을 화면 가운데에 오도록 비춰 보세요",
                                color = TextSecondary,
                                fontSize = 14.sp,
                                modifier = Modifier.height(40.dp).wrapContentHeight(Alignment.CenterVertically)
                            )
                        } else {
                            Row(
                                modifier = Modifier.horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                visibleParts.forEach { (name, _) ->
                                    PillChip(KijunClassMap.fullName(name), onClick = { liveSheetClass = name })
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                    ) {
                        ZOOM_PRESETS.filter { it <= maxZoomRatio + 0.01f }.forEach { z ->
                            val selected = kotlin.math.abs(zoomRatio - z) < 0.05f
                            Box(
                                modifier = Modifier
                                    .height(32.dp)
                                    .widthIn(min = 44.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (selected) TextPrimary else Color.White.copy(alpha = 0.14f))
                                    .clickable {
                                        zoomRatio = z.coerceIn(minZoomRatio, maxZoomRatio)
                                        camera?.cameraControl?.setZoomRatio(zoomRatio)
                                    }
                                    .padding(horizontal = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "${z.toInt()}×",
                                    color = if (selected) BackgroundDark else TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp)
                    ) {
                        TileButton(
                            icon = Icons.Filled.PhotoLibrary,
                            label = "저장소",
                            modifier = Modifier.align(Alignment.CenterStart),
                            onClick = onOpenStorage
                        )
                        ShutterButton(
                            modifier = Modifier.align(Alignment.Center),
                            onClick = { liveResult?.let { captured = it } }
                        )
                        TileButton(
                            icon = Icons.Filled.FlashOn,
                            label = if (flashOn) "손전등 끔" else "손전등 켬",
                            tint = BrandOrange,
                            active = flashOn,
                            modifier = Modifier.align(Alignment.CenterEnd),
                            onClick = {
                                flashOn = !flashOn
                                camera?.cameraControl?.enableTorch(flashOn)
                            }
                        )
                    }
                }
            }

            liveSheetClass?.let { name ->
                KijunSheet(className = name, onDismiss = { liveSheetClass = null })
            }
            // 시트는 직접 그린 오버레이라 뒤로가기를 스스로 소비하지 않는다. 화면 전환 BackHandler보다
            // 나중에 등록해야 먼저 받는다.
            BackHandler(enabled = liveSheetClass != null) { liveSheetClass = null }
        }

        /* ---------- CAPTURED ---------- */
        else {
            CapturedView(
                bitmap = shot.frame,
                detections = shot.detections,
                cropInfo = shot.cropInfo,
                topBar = {
                    Row(
                        modifier = Modifier
                            .height(44.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable { captured = null }
                            .padding(start = 10.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(4.dp))
                        Text("다시 촬영", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.weight(1f))
                    InfoChip("정지 화면", background = FreezeBg, color = FreezeFg, bold = true)
                },
                actions = {
                    OutlinedButton(
                        onClick = onOpenStorage,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = SurfaceDark,
                            contentColor = TextPrimary
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, OutlineDark)
                    ) {
                        Text("저장소 보기", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = {
                            val ok = SessionStore.save(context, shot.frame, shot.detections, shot.cropInfo)
                            Toast.makeText(context, if (ok) "저장했습니다" else "저장하지 못했습니다", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCard)
                    ) {
                        Text("이 사진 저장", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

/** 카메라·사진 화면 위쪽 반투명 바 높이 (상태바 + 위 8dp + 버튼 44dp + 아래 14dp) */
@Composable
private fun topBarHeight(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 66.dp

/** 라이브 화면 하단의 네모 아이콘 버튼 + 아래 글자 (저장소, 손전등) */
@Composable
private fun TileButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = TextPrimary,
    active: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (active) tint.copy(alpha = 0.22f) else SurfaceVariantDark)
                .border(1.dp, if (active) tint else OutlineDark, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Text(label, color = TextMuted, fontSize = 12.sp)
    }
}

/**
 * 촬영한 사진(또는 저장한 기록) 화면: 위에 사진 + 번호 박스, 아래에 인식된 부품 목록.
 * 박스나 목록을 누르면 그 부품의 검사기준 시트가 뜨고, 시트 안에서 이 사진의 다른 부품으로 바꿀 수 있다.
 */
@Composable
fun CapturedView(
    bitmap: Bitmap,
    detections: List<DetectionResult>,
    cropInfo: CenterCropInfo?,
    topBar: @Composable RowScope.() -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val ordered = remember(detections) { detections.sortedByDescending { it.confidence }.take(20) }
    var selected by remember { mutableStateOf(-1) }
    val density = LocalDensity.current
    val overlapPx = with(density) { 22.dp.roundToPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 사진은 아래 목록 패널의 둥근 모서리 밑까지(22dp) 이어지게 깐다
        Layout(
            modifier = Modifier.fillMaxSize(),
            content = {
                Box {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    BoundingBoxOverlay(
                        modifier = Modifier.fillMaxSize(),
                        detections = ordered,
                        cropInfo = cropInfo,
                        numbered = true,
                        labelTopInset = topBarHeight(),
                        onBoxClick = { selected = it }
                    )
                }
                CapturedPartsPanel(ordered, actions, onSelect = { selected = it })
            }
        ) { measurables, constraints ->
            val w = constraints.maxWidth
            val h = constraints.maxHeight
            val panel = measurables[1].measure(Constraints(minWidth = w, maxWidth = w, maxHeight = (h * 0.5f).toInt()))
            val photoH = (h - panel.height + overlapPx).coerceIn(0, h)
            val photo = measurables[0].measure(Constraints.fixed(w, photoH))
            layout(w, h) {
                photo.place(0, 0)
                panel.place(0, h - panel.height)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.72f))
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = topBar
        )

        val current = ordered.getOrNull(selected)
        if (current != null) {
            KijunSheet(
                className = current.className ?: "Unknown",
                onDismiss = { selected = -1 },
                siblings = ordered.mapIndexed { i, d -> "${i + 1} ${KijunClassMap.shortName(d.className ?: "Unknown")}" },
                selectedSibling = selected,
                onSelectSibling = { selected = it }
            )
        }
        BackHandler(enabled = current != null) { selected = -1 }
    }
}

@Composable
private fun CapturedPartsPanel(
    ordered: List<DetectionResult>,
    actions: (@Composable RowScope.() -> Unit)?,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(SheetDark)
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SheetHandle)
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "인식된 부품 ${ordered.size}개",
                color = TextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (ordered.isNotEmpty()) Text("눌러서 검사기준 보기", color = TextSecondary, fontSize = 12.sp)
        }
        if (ordered.isEmpty()) {
            Text(
                "이 사진에서는 부품을 찾지 못했습니다. 부품이 화면 가운데에 오게 다시 찍어 보세요.",
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(ordered) { i, d ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariantDark)
                            .clickable { onSelect(i) }
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(DetectionAccent),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("${i + 1}", color = BackgroundDark, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            KijunClassMap.fullName(d.className ?: "Unknown"),
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        Text("${(d.confidence * 100).toInt()}%", color = TextSecondary, fontSize = 13.sp)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        if (actions != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                content = actions
            )
        }
    }
}

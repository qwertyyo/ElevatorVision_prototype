package com.example.elevatorvision.ui

import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import com.example.elevatorvision.StandardsRepository
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.elevatorvision.CenterCropInfo
import com.example.elevatorvision.ui.theme.BrandBlue
import com.example.elevatorvision.ui.theme.BrandGreen
import com.example.elevatorvision.ui.theme.DetectionAccent
import com.example.elevatorvision.yolo.DetectionResult
import kotlin.math.max

private data class OverlayItem(
    val det: DetectionResult,
    val mapped: RectF,
    val label: String
)

// 🌟 알림창 팝업에 보여줄 글자들을 임시로 저장해두는 바구니 데이터 클래스
private data class DialogContent(
    val title: String,
    val message: String
)

@Composable
fun BoundingBoxOverlay(
    modifier: Modifier = Modifier,
    detections: List<DetectionResult>,
    labels: List<String>,
    modelInputSize: Int = 640,
    maxShow: Int = 20,
    showInfoIcons: Boolean,
    enablePopup: Boolean,
    cropInfo: CenterCropInfo? = null,
    onSheetVisibleChanged: (Boolean) -> Unit = {}
) {
    BoxWithConstraints(modifier = modifier) {
        val context = LocalContext.current
        LaunchedEffect(Unit) { StandardsRepository.load(context) }

        val density = LocalDensity.current
        val dstWpx = with(density) { maxWidth.toPx() }
        val dstHpx = with(density) { maxHeight.toPx() }

        fun mapRectFrameToScreen(r: RectF, srcW: Float, srcH: Float): RectF {
            val scale = max(dstWpx / srcW, dstHpx / srcH)
            val dx = (dstWpx - srcW * scale) / 2f
            val dy = (dstHpx - srcH * scale) / 2f
            return RectF(
                r.left * scale + dx,
                r.top * scale + dy,
                r.right * scale + dx,
                r.bottom * scale + dy
            )
        }

        // 모델 입력을 만들 때 폰의 물리적 방향에 맞춰 정사각형 크롭을 추가로 돌렸다면(extraRotationDegrees),
        // 박스 좌표는 그 회전이 적용된 640x640 좌표계로 나온다. 화면 매핑은 회전 전 좌표계(크롭 좌표계)
        // 기준이므로, 먼저 이 회전을 역으로 되돌려야 박스가 화면과 어긋나지 않는다.
        fun unrotateInSquare(r: RectF, size: Float, degrees: Int): RectF {
            val norm = ((degrees % 360) + 360) % 360
            if (norm == 0) return r
            fun inv(x: Float, y: Float): Offset = when (norm) {
                90 -> Offset(y, size - x)
                180 -> Offset(size - x, size - y)
                270 -> Offset(size - y, x)
                else -> Offset(x, y)
            }
            val p1 = inv(r.left, r.top)
            val p2 = inv(r.right, r.top)
            val p3 = inv(r.right, r.bottom)
            val p4 = inv(r.left, r.bottom)
            val xs = floatArrayOf(p1.x, p2.x, p3.x, p4.x)
            val ys = floatArrayOf(p1.y, p2.y, p3.y, p4.y)
            return RectF(xs.min(), ys.min(), xs.max(), ys.max())
        }

        fun modelRectToFrameRect(r: RectF): RectF {
            val info = cropInfo ?: return r
            val unrotated = unrotateInSquare(r, info.targetSize.toFloat(), info.extraRotationDegrees)
            val scale = info.cropSize.toFloat() / info.targetSize
            return RectF(
                unrotated.left * scale + info.cropLeft,
                unrotated.top * scale + info.cropTop,
                unrotated.right * scale + info.cropLeft,
                unrotated.bottom * scale + info.cropTop
            )
        }

        val namePaint = remember {
            Paint().apply {
                isAntiAlias = true
                textSize = 32f
                color = android.graphics.Color.WHITE
                isFakeBoldText = true
            }
        }
        val confPaint = remember {
            Paint().apply {
                isAntiAlias = true
                textSize = 32f
                color = android.graphics.Color.parseColor("#29E0E8")
            }
        }
        val bgPaint = remember {
            Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(200, 10, 16, 22)
            }
        }
        val borderPaint = remember {
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = android.graphics.Color.parseColor("#4029E0E8")
            }
        }
        val dotPaint = remember {
            Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.parseColor("#29E0E8")
            }
        }

        val fm = namePaint.fontMetrics
        val textHeight = fm.bottom - fm.top

        val items = detections
            .sortedByDescending { it.confidence }
            .take(maxShow)
            .map { d ->
                val rModel = RectF(d.left, d.top, d.right, d.bottom)
                val rFrame = modelRectToFrameRect(rModel)
                val mapped = if (cropInfo != null) {
                    mapRectFrameToScreen(rFrame, cropInfo.srcW.toFloat(), cropInfo.srcH.toFloat())
                } else {
                    mapRectFrameToScreen(rModel, modelInputSize.toFloat(), modelInputSize.toFloat())
                }
                val name = d.className ?: labels.getOrNull(d.classId) ?: "Unknown"
                OverlayItem(d, mapped, name)
            }

        var selected by remember { mutableStateOf<OverlayItem?>(null) }
        var showStandardsFor by remember { mutableStateOf<String?>(null) }
        var showLawFor by remember { mutableStateOf<String?>(null) }
        var alertDialogContent by remember { mutableStateOf<DialogContent?>(null) }

        // 부품 선택/검사기준/표준화 바텀시트가 하나라도 떠 있으면 촬영 UI(줌/셔터/손전등 등)와
        // 겹쳐 보이므로, 호출한 쪽(CameraScreen)이 그 UI를 숨길 수 있게 알려준다.
        val anySheetVisible = selected != null || showStandardsFor != null || showLawFor != null
        LaunchedEffect(anySheetVisible) { onSheetVisibleChanged(anySheetVisible) }

        // 이 시트들은 실제 Dialog가 아니라 직접 그린 오버레이라 시스템 뒤로가기를 스스로
        // 소비하지 않는다. 그대로 두면 화면 자체를 전환하는 상위 BackHandler(AppRoot)가
        // 바로 발동해서 시트를 건너뛰고 화면이 통째로 튕겨버린다.
        // 아래(부품 선택 시트)를 먼저 등록하고 위(검사기준/표준화 시트)를 나중에 등록하면,
        // OnBackPressedDispatcher가 나중에 등록된 콜백을 우선 호출하므로
        // "위 레이어부터 하나씩" 닫히는 순서가 보장된다.
        BackHandler(enabled = selected != null) {
            selected = null
        }
        BackHandler(enabled = showStandardsFor != null || showLawFor != null) {
            if (showLawFor != null) showLawFor = null else showStandardsFor = null
        }

        // 1. 인식 박스: 모서리 브라켓 + 라벨 필
        Canvas(Modifier.matchParentSize()) {
            val strokePx = 3f
            val bracketLen = 18f
            val inset = 4f

            items.forEach { item ->
                val r = item.mapped

                fun corner(cx: Float, cy: Float, dx: Int, dy: Int) {
                    drawLine(
                        color = DetectionAccent,
                        start = Offset(cx, cy),
                        end = Offset(cx + bracketLen * dx, cy),
                        strokeWidth = strokePx
                    )
                    drawLine(
                        color = DetectionAccent,
                        start = Offset(cx, cy),
                        end = Offset(cx, cy + bracketLen * dy),
                        strokeWidth = strokePx
                    )
                }
                corner(r.left - inset, r.top - inset, 1, 1)
                corner(r.right + inset, r.top - inset, -1, 1)
                corner(r.left - inset, r.bottom + inset, 1, -1)
                corner(r.right + inset, r.bottom + inset, -1, -1)

                // 라벨 필: [●] 이름  신뢰도%
                val confText = "${(item.det.confidence * 100).let { "%.1f".format(it) }}%"
                val dotRadius = 5f
                val gap = 8f
                val paddingX = 12f
                val paddingY = 8f

                val nameW = namePaint.measureText(item.label)
                val confW = confPaint.measureText(confText)
                val pillW = paddingX * 2 + dotRadius * 2 + gap + nameW + gap + confW
                val pillH = textHeight + paddingY * 2

                val pillLeft = r.left.coerceAtLeast(0f)
                val pillTop = if (r.top - pillH - 8f >= 0f) r.top - pillH - 8f else r.bottom + 8f

                // 화면 자체는 세로로 고정돼 있지만, 폰을 기울여 들면 글자만 화면에 그대로
                // 붙어 있어 사용자 눈에는 돌아간 것처럼 보인다. 라벨 필만 자기 중심을 축으로
                // 반대로 돌려 그려서, 폰을 어느 방향으로 들어도 항상 똑바로 읽히게 한다.
                // extraRotationDegrees는 모델 입력을 "바로 세우기 위해" 크롭에 적용한 정방향
                // 회전이므로, 화면에 그대로 붙어있는 텍스트를 사람이 보기 좋게 하려면
                // 그 반대(역방향)로 돌려야 한다.
                val labelRotation = -(cropInfo?.extraRotationDegrees ?: 0).toFloat()

                drawContext.canvas.nativeCanvas.apply {
                    val pivotX = pillLeft + pillW / 2f
                    val pivotY = pillTop + pillH / 2f
                    val rotated = labelRotation != 0f
                    if (rotated) {
                        save()
                        rotate(labelRotation, pivotX, pivotY)
                    }

                    val rr = android.graphics.RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
                    drawRoundRect(rr, 8f, 8f, bgPaint)
                    drawRoundRect(rr, 8f, 8f, borderPaint)

                    val textBaseline = pillTop + pillH - paddingY - fm.bottom
                    var x = pillLeft + paddingX
                    drawCircle(x + dotRadius, textBaseline - textHeight / 2.5f, dotRadius, dotPaint)
                    x += dotRadius * 2 + gap
                    drawText(item.label, x, textBaseline, namePaint)
                    x += nameW + gap
                    drawText(confText, x, textBaseline, confPaint)

                    if (rotated) restore()
                }
            }
        }

        // 2. 박스 클릭 감지 패널
        items.forEach { item ->
            val boxLeftDp = with(density) { item.mapped.left.toDp() }
            val boxTopDp = with(density) { item.mapped.top.toDp() }
            val boxWidthDp = with(density) { item.mapped.width().toDp() }
            val boxHeightDp = with(density) { item.mapped.height().toDp() }

            Box(
                modifier = Modifier
                    .offset(x = boxLeftDp, y = boxTopDp)
                    .size(width = boxWidthDp, height = boxHeightDp)
                    .clickable { selected = item }
            )
        }

        // 3. 부품 선택 바텀시트: 검사기준 / 표준화 / 검사가이드
        val currentSelected = selected
        if (currentSelected != null) {
            val name = currentSelected.det.className
                ?: labels.getOrNull(currentSelected.det.classId)
                ?: "Unknown"

            BottomSheetScaffold(
                visible = true,
                onDismiss = { selected = null },
                eyebrow = "AI 인식 부품",
                title = name,
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                SheetMenuRow(
                    icon = Icons.Filled.Gavel,
                    label = "검사기준",
                    accentColor = BrandBlue,
                    onClick = { showLawFor = name }
                )
                SheetMenuRow(
                    icon = Icons.Filled.FactCheck,
                    label = "표준화",
                    accentColor = BrandBlue,
                    onClick = { showStandardsFor = name }
                )
                SheetMenuRow(
                    icon = Icons.Filled.MenuBook,
                    label = "검사 가이드",
                    accentColor = BrandBlue,
                    onClick = {
                        alertDialogContent = DialogContent(
                            title = "$name - 검사 가이드",
                            message = "검사 가이드 내용"
                        )
                    }
                )
            }
        }

        // 4. 검사가이드 안내 (임시 알림)
        if (alertDialogContent != null) {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { alertDialogContent = null },
                title = {
                    androidx.compose.material3.Text(
                        text = alertDialogContent!!.title,
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                text = {
                    androidx.compose.material3.Text(
                        text = alertDialogContent!!.message,
                        style = MaterialTheme.typography.bodyLarge
                    )
                },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = { alertDialogContent = null }) {
                        androidx.compose.material3.Text("확인")
                    }
                }
            )
        }

        if (showStandardsFor != null) {
            val name = showStandardsFor!!
            StandardsListDialog(
                partName = name,
                dialogTitle = "표준화 안내",
                entries = StandardsRepository.getByClassName(name).map { it.toListDialogEntry() },
                onDismiss = { showStandardsFor = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (showLawFor != null) {
            val name = showLawFor!!
            StandardsListDialog(
                partName = name,
                dialogTitle = "검사기준 안내",
                entries = StandardsRepository.getLawByClassName(name).map { it.toListDialogEntry() },
                onDismiss = { showLawFor = null },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

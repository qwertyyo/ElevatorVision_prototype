package com.example.elevatorvision.ui

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
    cropInfo: CenterCropInfo? = null
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

        fun modelRectToFrameRect(r: RectF): RectF {
            val info = cropInfo ?: return r
            val scale = info.cropSize.toFloat() / info.targetSize
            return RectF(
                r.left * scale + info.cropLeft,
                r.top * scale + info.cropTop,
                r.right * scale + info.cropLeft,
                r.bottom * scale + info.cropTop
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

                drawContext.canvas.nativeCanvas.apply {
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

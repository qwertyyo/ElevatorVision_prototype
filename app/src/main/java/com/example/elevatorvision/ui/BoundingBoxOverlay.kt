package com.example.elevatorvision.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.elevatorvision.CenterCropInfo
//import com.example.elevatorvision.yolo.CocoLabels
import com.example.elevatorvision.yolo.DetectionResult
import kotlin.math.max
import kotlin.math.roundToInt

private data class OverlayItem(
    val det: DetectionResult,
    val mapped: RectF,
    val label: String,
    val labelX: Float,
    val labelBaselineY: Float,
    val textWidth: Float,
    val textHeight: Float
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

        val textPaint = remember {
            Paint().apply {
                isAntiAlias = true
                textSize = 36f
                color = android.graphics.Color.WHITE
            }
        }

        val bgPaint = remember {
            Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(180, 0, 0, 0)
            }
        }

        val fm = textPaint.fontMetrics
        val textHeight = fm.bottom - fm.top
        val paddingX = 10f
        val paddingY = 8f

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

                //val label = "${CocoLabels.nameOf(d.classId)} ${(d.confidence * 100).toInt()}%"
                val name = labels.getOrNull(d.classId) ?: "Unknown"
                val label = "$name ${(d.confidence * 100).toInt()}%"
                val tw = textPaint.measureText(label)
                val x = mapped.left.coerceAtLeast(0f)
                val yTop = mapped.top
                val baselineY =
                    if (yTop - textHeight - 10f >= 0f) yTop else yTop + textHeight + 10f

                OverlayItem(d, mapped, label, x, baselineY, tw, textHeight)
            }

        var selected by remember { mutableStateOf<OverlayItem?>(null) }
        var popupPos by remember { mutableStateOf(Offset.Zero) }

        Canvas(Modifier.matchParentSize()) {
            items.forEach {
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(it.mapped.left, it.mapped.top),
                    size = Size(it.mapped.width(), it.mapped.height()),
                    style = Stroke(3f)
                )

                val bgLeft = it.labelX
                val bgTop = it.labelBaselineY - it.textHeight
                val bgRight = it.labelX + it.textWidth + paddingX * 2
                val bgBottom = it.labelBaselineY + paddingY

                drawContext.canvas.nativeCanvas.drawRoundRect(
                    bgLeft, bgTop, bgRight, bgBottom, 8f, 8f, bgPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    it.label, it.labelX + paddingX, it.labelBaselineY, textPaint
                )
            }
        }

        if (showInfoIcons) {
            val iconSizePx = with(density) { 28.dp.toPx() }
            val gapPx = with(density) { 4.dp.toPx() } // ✅ 라벨에 더 바싹 붙게 약간 줄임

            items.forEach { item ->
                // ✅ 라벨 오른쪽 끝(배경 패딩 포함) 기준으로 i 위치 계산
                val labelRightPx = item.labelX + paddingX + item.textWidth + paddingX

                var iconX = labelRightPx + gapPx
                var iconY = (item.labelBaselineY - item.textHeight).coerceAtLeast(0f)

                // 화면 오른쪽 밖으로 나가면 라벨 왼쪽으로 이동
                if (iconX + iconSizePx > dstWpx) {
                    iconX = (item.labelX - gapPx - iconSizePx).coerceAtLeast(0f)
                }
                iconY = iconY.coerceIn(0f, dstHpx - iconSizePx)

                // ✅ 배경이 확실히 보이도록 Box + TextButton(투명) 조합
                Box(
                    modifier = Modifier
                        .offset { IntOffset(iconX.roundToInt(), iconY.roundToInt()) }
                        .size(28.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(6.dp))
                ) {
                    TextButton(
                        onClick = {
                            if (!enablePopup) return@TextButton
                            selected = item
                            popupPos = Offset(iconX + iconSizePx + gapPx, iconY)
                        },
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color.Transparent,
                            contentColor = Color.White
                        )
                    ) {
                        Text("i")
                    }
                }
            }
        } else {
            selected = null
        }
        if (enablePopup && selected != null) {
            val d = selected!!.det
            val name = labels.getOrNull(d.classId) ?: "Unknown"

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(popupPos.x.roundToInt(), popupPos.y.roundToInt()),
                onDismissRequest = { selected = null },
                properties = PopupProperties(focusable = true)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "$name ${(d.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(onClick = { selected = null }) {
                                Text("X")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("이것은 $name 입니다.")
                    }
                }
            }
        }

//        if (enablePopup && selected != null) {
//            val d = selected!!.det
//            Popup(
//                alignment = Alignment.TopStart,
//                offset = IntOffset(popupPos.x.roundToInt(), popupPos.y.roundToInt()),
//                onDismissRequest = { selected = null },
//                properties = PopupProperties(focusable = true)
//            ) {
//                Surface(
//                    shape = RoundedCornerShape(12.dp),
//                    tonalElevation = 6.dp
//                ) {
//                    Column(Modifier.padding(12.dp)) {
//                        Row(
//                            Modifier.fillMaxWidth(),
//                            horizontalArrangement = Arrangement.SpaceBetween
//                        ) {
//                            Text(
//                                "${CocoLabels.nameOf(d.classId)} ${(d.confidence * 100).toInt()}%",
//                                style = MaterialTheme.typography.titleMedium
//                            )
//                            TextButton(onClick = { selected = null }) {
//                                Text("X")
//                            }
//                        }
//                        Spacer(Modifier.height(8.dp))
//                        Text("설명(미구현)")
//                    }
//                }
//            }
//        }
    }
}

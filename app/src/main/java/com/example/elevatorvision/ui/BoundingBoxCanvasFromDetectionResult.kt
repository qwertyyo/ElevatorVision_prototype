/*package com.example.elevatorvision.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.elevatorvision.yolo.CocoLabels
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
fun BoundingBoxCanvasFromDetectionResult(
    modifier: Modifier = Modifier,
    detections: List<DetectionResult>,
    modelInputSize: Int = 640,
    maxShow: Int = 10
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val dstWpx = with(density) { maxWidth.toPx() }
        val dstHpx = with(density) { maxHeight.toPx() }
        val srcW = modelInputSize.toFloat()
        val srcH = modelInputSize.toFloat()

        fun mapRectCenterCrop(r: RectF): RectF {
            val scale = max(dstWpx / srcW, dstHpx / srcH)
            val scaledW = srcW * scale
            val scaledH = srcH * scale
            val dx = (dstWpx - scaledW) / 2f
            val dy = (dstHpx - scaledH) / 2f
            return RectF(
                r.left * scale + dx,
                r.top * scale + dy,
                r.right * scale + dx,
                r.bottom * scale + dy
            )
        }

        // 텍스트 측정용 Paint (Canvas 밖에서도 재사용)
        val textPaint = remember {
            Paint().apply {
                isAntiAlias = true
                textSize = 36f
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
        }
        val bgPaint = remember {
            Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(180, 0, 0, 0)
                style = Paint.Style.FILL
            }
        }

        val fm = textPaint.fontMetrics
        val textHeight = fm.bottom - fm.top

        val topDetections: List<DetectionResult> =
            detections.sortedByDescending(DetectionResult::confidence).take(maxShow)

        // 라벨 위치/폭을 “미리 계산”해서 Canvas와 IconButton이 같은 값을 쓰게 함
        val paddingX = 10f
        val paddingY = 8f
        val items: List<OverlayItem> = topDetections.map { d ->
            val mapped = mapRectCenterCrop(RectF(d.left, d.top, d.right, d.bottom))
            val label = "${CocoLabels.nameOf(d.classId)} ${(d.confidence * 100).toInt()}%"
            val tw = textPaint.measureText(label)

            val x = mapped.left.coerceAtLeast(0f)
            val yTop = mapped.top
            val baselineY = if (yTop - textHeight - 10f >= 0f) yTop else (yTop + textHeight + 10f)

            OverlayItem(
                det = d,
                mapped = mapped,
                label = label,
                labelX = x,
                labelBaselineY = baselineY,
                textWidth = tw,
                textHeight = textHeight
            )
        }

        // ---------- 1) Canvas: 박스 + 라벨 ----------
        Canvas(modifier = Modifier.matchParentSize()) {
            items.forEach { it ->
                val m = it.mapped

                // 박스
                drawRect(
                    color = Color.Green,
                    topLeft = Offset(m.left, m.top),
                    size = Size(m.width(), m.height()),
                    style = Stroke(width = 3f)
                )

                // 라벨 배경
                val bgLeft = it.labelX
                val bgTop = it.labelBaselineY - it.textHeight
                val bgRight = it.labelX + it.textWidth + paddingX * 2
                val bgBottom = it.labelBaselineY + paddingY

                drawContext.canvas.nativeCanvas.drawRoundRect(
                    bgLeft, bgTop, bgRight, bgBottom,
                    8f, 8f, bgPaint
                )

                // 라벨 텍스트
                drawContext.canvas.nativeCanvas.drawText(
                    it.label,
                    it.labelX + paddingX,
                    it.labelBaselineY,
                    textPaint
                )
            }
        }

        // ---------- 2) UI: “라벨 바로 옆”에 i 아이콘 ----------
        val iconSizePx = with(density) { 28.dp.toPx() }
        val gapPx = with(density) { 6.dp.toPx() }

        items.forEach { it ->
            // 라벨 오른쪽 끝(텍스트 끝 + 패딩) 기준으로 아이콘을 붙임
            val labelRightPx = it.labelX + paddingX + it.textWidth + paddingX

            // 기본은 라벨 오른쪽에 붙이기
            var iconX = labelRightPx + gapPx
            // 아이콘 Y는 라벨 “박스” 위쪽 기준(텍스트 영역 상단에 맞춰줌)
            var iconY = (it.labelBaselineY - it.textHeight).coerceAtLeast(0f)

            // ✅ 화면 오른쪽으로 밀리면 라벨 “왼쪽”으로 붙이기
            if (iconX + iconSizePx > dstWpx) {
                iconX = (it.labelX - gapPx - iconSizePx).coerceAtLeast(0f)
            }

            // 아래로도 튀어나오면 클램프
            iconY = iconY.coerceIn(0f, dstHpx - iconSizePx)

            IconButton(
                onClick = { /* 다음 단계: Popup 연결 */ },
                modifier = Modifier
                    .size(28.dp)
                    .background(Color(0xAA000000))
                    .offset { IntOffset(iconX.roundToInt(), iconY.roundToInt()) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Info,
                    contentDescription = "info",
                    tint = Color.White
                )
            }
        }
    }
}*/
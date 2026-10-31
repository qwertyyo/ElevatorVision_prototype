package com.example.elevatorvision.ui

import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elevatorvision.CenterCropInfo
import com.example.elevatorvision.kijun.KijunClassMap
import com.example.elevatorvision.ui.theme.BackgroundDark
import com.example.elevatorvision.ui.theme.DetectionAccent
import com.example.elevatorvision.yolo.DetectionResult
import kotlin.math.max

private data class OverlayItem(
    val det: DetectionResult,
    val mapped: RectF,
    val label: String
)

/**
 * 인식 박스 그리기.
 * - detections 순서가 곧 번호 순서다 (호출 쪽에서 신뢰도 순으로 정렬해서 넘긴다).
 * - numbered=false(라이브): 박스 위에 [● 부품이름 92%] 라벨
 * - numbered=true(촬영·저장 화면): 박스 왼쪽 위에 번호 배지, 이름은 아래 목록에서 본다
 * - onBoxClick 이 있으면 박스를 눌렀을 때 그 박스 번호(0부터)를 알려 준다. 검사기준 시트는 화면 쪽에서 띄운다.
 * - labelTopInset: 화면 위쪽 반투명 바 높이. 박스가 맨 위에 붙어도 번호·라벨은 그 아래에 그려서 가려지지 않게 한다.
 */
@Composable
fun BoundingBoxOverlay(
    modifier: Modifier = Modifier,
    detections: List<DetectionResult>,
    cropInfo: CenterCropInfo? = null,
    modelInputSize: Int = 640,
    numbered: Boolean = false,
    labelTopInset: Dp = 0.dp,
    onBoxClick: ((Int) -> Unit)? = null
) {
    val context = LocalContext.current
    remember { KijunClassMap.load(context) }

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

        // 모델 입력을 만들 때 폰의 물리적 방향에 맞춰 정사각형 크롭을 추가로 돌렸다면(extraRotationDegrees),
        // 박스 좌표는 그 회전이 적용된 정사각형 좌표계로 나온다. 화면 매핑은 회전 전 좌표계(크롭 좌표계)
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

        // 저장된 예전 기록(v1, 640 입력)도 cropInfo.targetSize 로 자기 크기를 알고 있어서 그대로 맞는다.
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

        val namePaint = remember(density) {
            Paint().apply {
                isAntiAlias = true
                textSize = with(density) { 13.sp.toPx() }
                color = android.graphics.Color.WHITE
                isFakeBoldText = true
            }
        }
        val confPaint = remember(density) {
            Paint().apply {
                isAntiAlias = true
                textSize = with(density) { 12.sp.toPx() }
                color = DetectionAccent.toArgb()
            }
        }
        val bgPaint = remember {
            Paint().apply {
                isAntiAlias = true
                color = android.graphics.Color.argb(219, 10, 16, 22)
            }
        }
        val borderPaint = remember(density) {
            Paint().apply {
                isAntiAlias = true
                style = Paint.Style.STROKE
                strokeWidth = with(density) { 1.dp.toPx() }
                color = DetectionAccent.copy(alpha = 0.35f).toArgb()
            }
        }
        val accentPaint = remember {
            Paint().apply {
                isAntiAlias = true
                color = DetectionAccent.toArgb()
            }
        }
        val badgeTextPaint = remember(density) {
            Paint().apply {
                isAntiAlias = true
                textSize = with(density) { 14.sp.toPx() }
                color = BackgroundDark.toArgb()
                isFakeBoldText = true
                textAlign = Paint.Align.CENTER
            }
        }

        val items = detections.map { d ->
            val rModel = RectF(d.left, d.top, d.right, d.bottom)
            val mapped = if (cropInfo != null) {
                mapRectFrameToScreen(modelRectToFrameRect(rModel), cropInfo.srcW.toFloat(), cropInfo.srcH.toFloat())
            } else {
                mapRectFrameToScreen(rModel, modelInputSize.toFloat(), modelInputSize.toFloat())
            }
            OverlayItem(d, mapped, KijunClassMap.shortName(d.className ?: "Unknown"))
        }

        // 화면 자체는 세로로 고정돼 있지만, 폰을 기울여 들면 글자만 화면에 그대로 붙어 있어
        // 사용자 눈에는 돌아간 것처럼 보인다. 라벨·번호만 자기 중심을 축으로 반대로 돌려 그려서,
        // 폰을 어느 방향으로 들어도 항상 똑바로 읽히게 한다. extraRotationDegrees는 모델 입력을
        // "바로 세우기 위해" 크롭에 적용한 정방향 회전이므로 그 반대(역방향)로 돌린다.
        val labelRotation = -(cropInfo?.extraRotationDegrees ?: 0).toFloat()

        Canvas(Modifier.matchParentSize()) {
            val strokePx = 2.dp.toPx()
            val minTop = labelTopInset.toPx()
            val radiusPx = 6.dp.toPx()
            val fm = namePaint.fontMetrics
            val textHeight = fm.bottom - fm.top

            items.forEachIndexed { index, item ->
                val r = item.mapped
                drawRoundRect(
                    color = DetectionAccent,
                    topLeft = Offset(r.left, r.top),
                    size = Size(r.width(), r.height()),
                    cornerRadius = CornerRadius(radiusPx),
                    style = Stroke(width = strokePx)
                )

                drawContext.canvas.nativeCanvas.apply {
                    if (numbered) {
                        // 번호 배지: 박스 왼쪽 위 모서리에 걸친 동그라미
                        val cx = r.left + 3.dp.toPx()
                        val cy = (r.top + 3.dp.toPx()).coerceAtLeast(minTop + 17.dp.toPx())
                        val rotated = labelRotation != 0f
                        if (rotated) {
                            save()
                            rotate(labelRotation, cx, cy)
                        }
                        drawCircle(cx, cy, 13.dp.toPx(), accentPaint)
                        val bfm = badgeTextPaint.fontMetrics
                        drawText("${index + 1}", cx, cy - (bfm.ascent + bfm.descent) / 2f, badgeTextPaint)
                        if (rotated) restore()
                    } else {
                        // 라벨 필: [●] 이름  신뢰도%
                        val confText = "${(item.det.confidence * 100).toInt()}%"
                        val dotRadius = 3.5.dp.toPx()
                        val gap = 6.dp.toPx()
                        val paddingX = 10.dp.toPx()
                        val paddingY = 5.dp.toPx()

                        val nameW = namePaint.measureText(item.label)
                        val confW = confPaint.measureText(confText)
                        val pillW = paddingX * 2 + dotRadius * 2 + gap + nameW + gap + confW
                        val pillH = textHeight + paddingY * 2

                        val pillLeft = r.left.coerceIn(0f, (size.width - pillW).coerceAtLeast(0f))
                        val pillTop = if (r.top - pillH - 4.dp.toPx() >= minTop) r.top - pillH - 4.dp.toPx()
                        else (r.top + 4.dp.toPx()).coerceAtLeast(minTop + 4.dp.toPx())

                        val pivotX = pillLeft + pillW / 2f
                        val pivotY = pillTop + pillH / 2f
                        val rotated = labelRotation != 0f
                        if (rotated) {
                            save()
                            rotate(labelRotation, pivotX, pivotY)
                        }

                        val rr = RectF(pillLeft, pillTop, pillLeft + pillW, pillTop + pillH)
                        val corner = 8.dp.toPx()
                        drawRoundRect(rr, corner, corner, bgPaint)
                        drawRoundRect(rr, corner, corner, borderPaint)

                        val textBaseline = pillTop + paddingY - fm.top
                        var x = pillLeft + paddingX
                        drawCircle(x + dotRadius, pillTop + pillH / 2f, dotRadius, accentPaint)
                        x += dotRadius * 2 + gap
                        drawText(item.label, x, textBaseline, namePaint)
                        x += nameW + gap
                        drawText(confText, x, textBaseline, confPaint)

                        if (rotated) restore()
                    }
                }
            }
        }

        // 박스 클릭 감지 패널
        if (onBoxClick != null) {
            items.forEachIndexed { index, item ->
                Box(
                    modifier = Modifier
                        .offset(
                            x = with(density) { item.mapped.left.toDp() },
                            y = with(density) { item.mapped.top.toDp() }
                        )
                        .size(
                            width = with(density) { item.mapped.width().toDp() },
                            height = with(density) { item.mapped.height().toDp() }
                        )
                        .clickable { onBoxClick(index) }
                )
            }
        }
    }
}

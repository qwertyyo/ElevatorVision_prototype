package com.example.elevatorvision.ui

import androidx.compose.ui.platform.LocalContext
import com.example.elevatorvision.StandardsRepository
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

        // 🌟 [추가] 현재 화면에 상세 안내 팝업창(AlertDialog)을 띄울지 말지 결정하는 상태 변수
        var alertDialogContent by remember { mutableStateOf<DialogContent?>(null) }
        var showStandardsFor by remember { mutableStateOf<String?>(null) }
        var showLawFor by remember { mutableStateOf<String?>(null) }

        // 1. 화면에 초록색 사각형 박스 그리기
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

        // 2. 초록색 사각형 안쪽 영역 클릭 감지 패널
        items.forEach { item ->
            val boxLeftDp = with(density) { item.mapped.left.toDp() }
            val boxTopDp = with(density) { item.mapped.top.toDp() }
            val boxWidthDp = with(density) { item.mapped.width().toDp() }
            val boxHeightDp = with(density) { item.mapped.height().toDp() }

            Box(
                modifier = Modifier
                    .offset(x = boxLeftDp, y = boxTopDp)
                    .size(width = boxWidthDp, height = boxHeightDp)
                    .clickable {
                        selected = item
                        popupPos = Offset(item.mapped.left, item.mapped.bottom)
                    }
            )
        }

        // 3. 초록색 사각형 터치 시 나타나는 세로형 메뉴 팝업
        if (selected != null) {
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
                    Column(Modifier.padding(12.dp).width(160.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(
                                onClick = { selected = null },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text("X")
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        // 🌟 버튼 1: 검사기준 클릭 시 알림창 띄우기
                        Button(
                            onClick = {
                                showLawFor = name
                                selected = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2196F3))
                        ) {
                            Text("검사기준", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }

                        Spacer(Modifier.height(6.dp))

                        // 🌟 버튼 2: 표준화 클릭 시 알림창 띄우기
                        Button(
                            onClick = {
                                showStandardsFor = name
                                selected = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) {
                            Text("표준화", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }

                        Spacer(Modifier.height(6.dp))

                        // 🌟 버튼 3: 검사 가이드 클릭 시 알림창 띄우기
                        Button(
                            onClick = {
                                alertDialogContent = DialogContent(
                                    title = "$name - 검사 가이드",
                                    message = "검사 가이드 내용"
                                )
                                selected = null // 메뉴 팝업은 닫아줍니다.
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 6.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text("검사 가이드", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                        }
                    }
                }
            }
        }

        // 4. 🌟 [새로 추가된 구역] 버튼을 눌렀을 때 화면 중앙에 뜨는 실제 안내창(AlertDialog)
        if (alertDialogContent != null) {
            AlertDialog(
                onDismissRequest = { alertDialogContent = null }, // 바깥을 누르면 닫힘
                title = {
                    Text(text = alertDialogContent!!.title, style = MaterialTheme.typography.titleLarge)
                },
                text = {
                    Text(text = alertDialogContent!!.message, style = MaterialTheme.typography.bodyLarge)
                },
                confirmButton = {
                    Button(
                        onClick = { alertDialogContent = null } // '확인' 버튼을 누르면 닫힘
                    ) {
                        Text("확인")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
        if (showStandardsFor != null) {
            val name = showStandardsFor!!
            StandardsListDialog(
                partName = name,
                dialogTitle = "표준화 안내",
                entries = StandardsRepository.getByClassName(name).map { it.toListDialogEntry() },
                onDismiss = { showStandardsFor = null }
            )
        }

        if (showLawFor != null) {
            val name = showLawFor!!
            StandardsListDialog(
                partName = name,
                dialogTitle = "검사기준 안내",
                entries = StandardsRepository.getLawByClassName(name).map { it.toListDialogEntry() },
                onDismiss = { showLawFor = null }
            )
        }
    }
}
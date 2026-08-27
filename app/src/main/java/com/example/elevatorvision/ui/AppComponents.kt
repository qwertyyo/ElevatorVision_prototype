package com.example.elevatorvision.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elevatorvision.ui.theme.OutlineDark
import com.example.elevatorvision.ui.theme.SheetDark
import com.example.elevatorvision.ui.theme.TextSecondary
import com.example.elevatorvision.ui.theme.TextTertiary

/**
 * 상태/정보를 알려주는 작은 필(pill) 배지.
 * 예: "FREEZE", "5건 인식됨", "FL-ON", 세션 태그 등.
 */
@Composable
fun StatusPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = TextSecondary,
    filled: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val bg = if (filled) color.copy(alpha = 0.18f) else Color.Transparent
    val border = if (filled) null else color.copy(alpha = 0.6f)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .let { m ->
                if (border != null) m.border(1.dp, border, RoundedCornerShape(8.dp)) else m
            }
            .background(bg)
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m }
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp
        )
    }
}

/**
 * 카메라 화면 전반에서 쓰이는 원형 오버레이 아이콘 버튼 (글래스 느낌).
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.42f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(size * 0.42f)
        )
    }
}

/**
 * 카메라 셔터 버튼. 눌리면 살짝 축소되는 애니메이션.
 */
@Composable
fun ShutterButton(
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.88f else 1f, label = "shutterScale")

    Box(
        modifier = modifier
            .size(76.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .border(3.dp, Color.White, CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

/**
 * 화면 하단에서 올라오는 바텀시트 공용 틀.
 * 반투명 스크림 + 상단 둥근 모서리 시트 + 드래그 핸들 + (선택) 헤더.
 * 드래그 핸들은 항상 실제로 끌어서 시트를 확장/축소할 수 있다.
 */
@Composable
fun BottomSheetScaffold(
    visible: Boolean,
    onDismiss: () -> Unit,
    eyebrow: String? = null,
    title: String,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = onDismiss,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val peekHeightPx = remember(fullHeightPx) { fullHeightPx * 0.55f }
        val expandedHeightPx = remember(fullHeightPx) { fullHeightPx * 0.92f }

        var sheetHeightPx by remember { mutableStateOf(peekHeightPx) }
        val animatedHeightPx by animateFloatAsState(targetValue = sheetHeightPx, label = "sheetHeight")

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .height(with(density) { animatedHeightPx.toDp() })
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(SheetDark)
                    .navigationBarsPadding()
                    .padding(bottom = 20.dp)
            ) {
                // 드래그 핸들 — 정확히 막대를 잡지 않아도 주변을 넉넉하게 잡으면 끌림
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .pointerInput(peekHeightPx, expandedHeightPx) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    val mid = (peekHeightPx + expandedHeightPx) / 2f
                                    sheetHeightPx =
                                        if (sheetHeightPx > mid) expandedHeightPx else peekHeightPx
                                },
                                onVerticalDrag = { change, dragAmount ->
                                    change.consume()
                                    sheetHeightPx = (sheetHeightPx - dragAmount)
                                        .coerceIn(peekHeightPx * 0.85f, expandedHeightPx)
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 36.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(OutlineDark)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 12.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(Modifier.weight(1f)) {
                        if (eyebrow != null) {
                            Text(
                                eyebrow,
                                color = TextTertiary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "닫기",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    content()
                }
            }
        }
    }
}

/**
 * 바텀시트 안에서 쓰는 리스트형 메뉴 행 (아이콘 뱃지 + 라벨 + 화살표).
 * 검사기준 / 표준화 / 검사가이드 선택, 향후 조작반·헤더 하위부품 선택에도 재사용.
 */
@Composable
fun SheetMenuRow(
    icon: ImageVector,
    label: String,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accentColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
    Spacer(Modifier.height(8.dp))
}

/**
 * 화면 하단의 장식용 홈 인디케이터 바 (레퍼런스 디자인의 통일된 룩을 위한 장식 요소).
 */
@Composable
fun HomeIndicatorBar(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(bottom = 10.dp)
            .size(width = 100.dp, height = 4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color.White.copy(alpha = 0.18f))
    )
}

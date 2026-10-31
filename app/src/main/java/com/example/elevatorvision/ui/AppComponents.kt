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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elevatorvision.ui.theme.OutlineDark
import com.example.elevatorvision.ui.theme.SheetDark
import com.example.elevatorvision.ui.theme.SheetHandle
import com.example.elevatorvision.ui.theme.SurfaceVariantDark
import com.example.elevatorvision.ui.theme.TextPrimary
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
    interactionSource: MutableInteractionSource? = null,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
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
 * 아래에 짧은 라벨이 붙는 CircleIconButton (예: 라이브 화면의 "저장소").
 * 라벨은 레이아웃 크기에 넣지 않고 버튼 아래로만 그려서, 같은 줄의 셔터·손전등 버튼과
 * 아이콘 중심 높이가 그대로 맞는다. (Compose는 부모 영역 밖에 그려진 자식도 터치를 받으므로
 * 라벨을 눌러도 동작한다.) 라벨을 누르면 버튼과 같은 interactionSource로 동그란 아이콘에
 * 눌림 효과가 나서 하나의 버튼처럼 느껴진다.
 */
@Composable
fun LabeledCircleIconButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Layout(
        modifier = modifier,
        content = {
            CircleIconButton(
                icon = icon,
                contentDescription = label,
                size = size,
                tint = tint,
                interactionSource = interactionSource,
                onClick = onClick
            )
            Text(
                label,
                color = TextSecondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.3.sp,
                // 글자만으로는 터치 영역이 너무 작아서 안쪽 여백으로 눌리는 범위를 넓힌다.
                modifier = Modifier
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val button = measurables[0].measure(loose)
        val text = measurables[1].measure(loose)
        // 라벨의 위쪽 안쪽 여백(4dp)이 그대로 버튼과 글자 사이 간격이 된다.
        layout(button.width, button.height) {
            button.placeRelative(0, 0)
            text.placeRelative((button.width - text.width) / 2, button.height)
        }
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
    eyebrowColor: Color = TextTertiary,
    onClose: () -> Unit = onDismiss,
    startExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullHeightPx = with(density) { maxHeight.toPx() }
        val peekHeightPx = remember(fullHeightPx) { fullHeightPx * 0.55f }
        val expandedHeightPx = remember(fullHeightPx) { fullHeightPx * 0.92f }

        var sheetHeightPx by remember { mutableStateOf(if (startExpanded) expandedHeightPx else peekHeightPx) }
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
                            .size(width = 40.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SheetHandle)
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
                                color = eyebrowColor,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            title,
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(SurfaceVariantDark)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "닫기",
                            tint = TextPrimary,
                            modifier = Modifier.size(20.dp)
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


/** 작은 정보 배지 (예: "검색어 ‘권상기’", "오프라인 저장본", "정지 화면"). */
@Composable
fun InfoChip(
    text: String,
    modifier: Modifier = Modifier,
    background: Color = SurfaceVariantDark,
    color: Color = Color(0xFFB7C2CE),
    bold: Boolean = false
) {
    Text(
        text,
        color = color,
        fontSize = 12.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

/** 누를 수 있는 둥근 칩 (부품 이름, 장소 고르기 등). selected 면 흰 바탕. */
@Composable
fun PillChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    height: androidx.compose.ui.unit.Dp = 40.dp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) TextPrimary else SurfaceVariantDark)
            .let { m -> if (selected) m else m.border(1.dp, OutlineDark, RoundedCornerShape(999.dp)) }
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (selected) Color(0xFF0A0E14) else TextPrimary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            maxLines = 1
        )
    }
}

/** 화면 왼쪽 위 뒤로가기(44dp 원). */
@Composable
fun BackCircleButton(
    contentDescription: String,
    modifier: Modifier = Modifier,
    background: Color = Color.Transparent,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(background)
    ) {
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(26.dp)
        )
    }
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

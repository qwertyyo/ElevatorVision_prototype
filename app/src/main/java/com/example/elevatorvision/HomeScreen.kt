package com.example.elevatorvision

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elevatorvision.ui.theme.AccentCard
import com.example.elevatorvision.ui.theme.BrandBlue
import com.example.elevatorvision.ui.theme.BrandGreen
import com.example.elevatorvision.ui.theme.BrandOrange
import com.example.elevatorvision.ui.theme.OutlineDark
import com.example.elevatorvision.ui.theme.SurfaceDark
import com.example.elevatorvision.ui.theme.SurfaceVariantDark
import com.example.elevatorvision.ui.theme.TextMuted
import com.example.elevatorvision.ui.theme.TextPrimary
import com.example.elevatorvision.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 검사기준 오프라인 저장본 상태 (첫 화면 오른쪽 위 배지) */
enum class KijunStatus(val text: String, val dot: Color) {
    LATEST("검사기준 저장본 최신", BrandGreen),
    SAVED("검사기준 저장본 있음", BrandBlue),
    NONE("검사기준 저장본 없음", BrandOrange)
}

@Composable
fun HomeScreen(
    kijunStatus: KijunStatus,
    onStartLive: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenStorage: () -> Unit,
    onOpenSession: (String) -> Unit
) {
    val context = LocalContext.current
    val recent by produceState<List<SavedSession>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { SessionStore.loadAll(context).take(2) }
    }
    val fmt = remember { SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREA) }

    // 화면이 작으면 스크롤, 크면 아래 꼬리말이 화면 맨 아래에 붙는다
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .heightIn(min = maxHeight)
                .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                // 머리줄: 로고 + 이름 / 저장본 상태
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .border(1.dp, OutlineDark, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_icon),
                            contentDescription = "한국승강기안전공단 로고",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("ElevatorVision", color = TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                        Text("한국승강기안전공단", color = TextSecondary, fontSize = 12.sp)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(SurfaceDark)
                            .border(1.dp, OutlineDark, RoundedCornerShape(999.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(kijunStatus.dot)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(kijunStatus.text, color = TextMuted, fontSize = 12.sp)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("무엇을 하시겠어요?", color = TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    Text("인터넷이 없는 현장에서도 그대로 쓸 수 있습니다.", color = TextSecondary, fontSize = 14.sp)
                }

                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // AI 실시간 부품 인식 (큰 카드)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 196.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(AccentCard)
                            .clickable(onClick = onStartLive)
                            .padding(22.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CenterFocusStrong, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
                            }
                            Text(
                                "카메라",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(Color.Black.copy(alpha = 0.22f))
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Spacer(Modifier.height(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("AI 실시간 부품 인식", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text(
                                "카메라로 비추면 부품을 찾아 표시하고, 누르면 그 부품의 검사기준을 보여 줍니다.",
                                color = Color(0xFFEAF5FE),
                                fontSize = 14.sp,
                                lineHeight = 21.sp
                            )
                        }
                    }

                    // 검사기준 검색
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 104.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(SurfaceDark)
                            .border(1.dp, OutlineDark, RoundedCornerShape(22.dp))
                            .clickable(onClick = onOpenSearch)
                            .padding(horizontal = 22.dp, vertical = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceVariantDark),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = BrandOrange, modifier = Modifier.size(26.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("검사기준 검색", color = TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                            Text("검사기준 통합검색 사이트를 엽니다.", color = TextSecondary, fontSize = 13.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = TextSecondary)
                    }
                }

                // 최근 저장한 검사
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("최근 저장한 검사", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(
                            "저장소 전체 보기",
                            color = Color(0xFF7CC4FA),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onOpenStorage)
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                        )
                    }
                    if (recent.isEmpty()) {
                        Text(
                            "아직 저장한 검사가 없습니다. 부품 인식 화면에서 촬영한 뒤 저장하면 여기에 보입니다.",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceDark)
                                .padding(14.dp)
                        )
                    }
                    recent.forEach { s ->
                        val thumb by produceState<Bitmap?>(initialValue = null, key1 = s.imageFile.absolutePath) {
                            value = withContext(Dispatchers.IO) { SessionStore.decodeThumbnail(s.imageFile.absolutePath, 160) }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceDark)
                                .clickable { onOpenSession(s.id) }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceVariantDark)
                            ) {
                                thumb?.let {
                                    Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(partsSummary(s.detections), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                Text("${fmt.format(Date(s.timestamp))} · 부품 ${s.detections.size}건", color = TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp)
            ) {
                Text("KoELSA · Korea Elevator Safety Agency", color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("v${BuildConfig.VERSION_NAME}", color = TextSecondary, fontSize = 12.sp)
            }
        }
    }
}

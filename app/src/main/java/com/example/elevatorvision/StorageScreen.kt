package com.example.elevatorvision

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elevatorvision.ui.BackCircleButton
import com.example.elevatorvision.ui.theme.AccentCard
import com.example.elevatorvision.ui.theme.DangerRed
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

@Composable
fun StorageScreen(
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    val context = LocalContext.current
    var newestFirst by remember { mutableStateOf(true) }
    var sessions by remember { mutableStateOf<List<SavedSession>?>(null) }
    var selecting by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    LaunchedEffect(reloadKey) {
        sessions = withContext(Dispatchers.IO) { SessionStore.loadAll(context) }
    }

    val dayFmt = remember { SimpleDateFormat("yyyy.MM.dd (E)", Locale.KOREA) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.KOREA) }
    val groups = remember(sessions, newestFirst) {
        val list = sessions.orEmpty().let { if (newestFirst) it else it.reversed() }
        list.groupBy { dayFmt.format(Date(it.timestamp)) }.toList()
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BackCircleButton(contentDescription = "뒤로", onClick = onBack)
            Text("저장소", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            HeaderPill(if (newestFirst) "최신순" else "오래된순", onClick = { newestFirst = !newestFirst }) {
                Icon(Icons.Filled.SwapVert, contentDescription = null, tint = TextMuted, modifier = Modifier.size(15.dp))
            }
            HeaderPill(if (selecting) "취소" else "선택", onClick = {
                selecting = !selecting
                picked = emptySet()
            })
        }

        val list = sessions
        when {
            list == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            list.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("저장한 검사가 없습니다.", color = TextSecondary, fontSize = 15.sp)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groups.forEach { (day, items) ->
                    item(key = "h_$day", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "$day · ${items.size}장",
                            color = TextMuted,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    items(items, key = { it.id }) { s ->
                        SessionCard(
                            session = s,
                            time = timeFmt.format(Date(s.timestamp)),
                            selecting = selecting,
                            picked = s.id in picked,
                            onClick = {
                                if (selecting) {
                                    picked = if (s.id in picked) picked - s.id else picked + s.id
                                } else onOpenDetail(s.id)
                            }
                        )
                    }
                }
            }
        }

        if (selecting) {
            Button(
                onClick = { confirmDelete = true },
                enabled = picked.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DangerRed)
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (picked.isEmpty()) "지울 사진을 고르세요" else "${picked.size}장 삭제", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("삭제 확인") },
            text = { Text("고른 ${picked.size}장을 삭제할까요?\n삭제하면 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    val failed = picked.count { !SessionStore.delete(context, it) }
                    Toast.makeText(context, if (failed == 0) "삭제했습니다" else "${failed}장을 지우지 못했습니다", Toast.LENGTH_SHORT).show()
                    confirmDelete = false
                    selecting = false
                    picked = emptySet()
                    reloadKey++
                }) { Text("삭제", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun HeaderPill(text: String, onClick: () -> Unit, icon: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(SurfaceDark)
            .border(1.dp, OutlineDark, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            icon()
            Spacer(Modifier.width(4.dp))
        }
        Text(text, color = TextMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SessionCard(
    session: SavedSession,
    time: String,
    selecting: Boolean,
    picked: Boolean,
    onClick: () -> Unit
) {
    val thumb by produceState<Bitmap?>(initialValue = null, key1 = session.imageFile.absolutePath) {
        value = withContext(Dispatchers.IO) { SessionStore.decodeThumbnail(session.imageFile.absolutePath, 360) }
    }
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .let { m -> if (picked) m.border(2.dp, AccentCard, RoundedCornerShape(16.dp)) else m }
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(108.dp)
                .background(SurfaceVariantDark)
        ) {
            thumb?.let {
                Image(it.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (selecting) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(if (picked) Color.White else Color.Black.copy(alpha = 0.45f))
                        .border(1.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (picked) Icon(Icons.Filled.CheckCircle, contentDescription = "선택됨", tint = AccentCard, modifier = Modifier.size(24.dp))
                }
            }
        }
        Column(
            Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row {
                Text(time, color = TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text("부품 ${session.detections.size}건", color = TextSecondary, fontSize = 12.sp)
            }
            Text(partsSummary(session.detections), color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium, lineHeight = 19.sp, maxLines = 2)
        }
    }
}

/* ======================= Storage Detail ======================= */

@Composable
fun StorageDetailScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var detail by remember { mutableStateOf<SavedSession?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("M월 d일 (E) HH:mm", Locale.KOREA) }

    LaunchedEffect(sessionId) {
        loading = true
        detail = withContext(Dispatchers.IO) { SessionStore.load(context, sessionId) }
        bitmap = withContext(Dispatchers.IO) {
            detail?.let { BitmapFactory.decodeFile(it.imageFile.absolutePath) }
        }
        loading = false
    }

    val d = detail
    val bmp = bitmap
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    if (d == null || bmp == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("기록을 불러오지 못했습니다.", color = TextPrimary)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onBack) { Text("뒤로") }
        }
        return
    }

    CapturedView(
        bitmap = bmp,
        detections = d.detections,
        cropInfo = d.cropInfo,
        topBar = {
            Row(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .clickable(onClick = onBack)
                    .padding(start = 10.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(4.dp))
                Text("저장소", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(12.dp))
            Text(fmt.format(Date(d.timestamp)), color = TextMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { confirmDelete = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "이 기록 삭제", tint = DangerRed)
            }
        }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("삭제 확인") },
            text = { Text("이 기록을 삭제할까요?\n삭제하면 복구할 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    if (SessionStore.delete(context, sessionId)) {
                        Toast.makeText(context, "삭제했습니다", Toast.LENGTH_SHORT).show()
                        onBack()
                    } else {
                        Toast.makeText(context, "삭제하지 못했습니다", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("삭제", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            }
        )
    }
}

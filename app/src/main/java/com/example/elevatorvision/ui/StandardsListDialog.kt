package com.example.elevatorvision.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.elevatorvision.LawItem
import com.example.elevatorvision.StandardItem
import com.example.elevatorvision.ui.theme.BrandBlue
import com.example.elevatorvision.ui.theme.SurfaceVariantDark
import com.example.elevatorvision.ui.theme.TextSecondary

// 표준화/검사기준 공용 표시 단위: 목록에 보일 제목(header)과 펼치면 나오는 본문(body)
data class ListDialogEntry(
    val id: String,
    val header: String,
    val body: String
)

fun StandardItem.toListDialogEntry(): ListDialogEntry {
    val yearLabel = if (year != null) " ($year 년 ${round ?: ""}차)" else ""
    return ListDialogEntry(id = id, header = "$title$yearLabel", body = standardization)
}

fun LawItem.toListDialogEntry(): ListDialogEntry {
    val dateLabel = if (effectiveDate != null) "$effectiveDate 부터 적용" else "시행일 미상"
    val header = if (articleNo.isNotBlank()) {
        "[$articleNo] $articleTitle ($dateLabel)"
    } else {
        dateLabel
    }
    return ListDialogEntry(id = id, header = header, body = content)
}

@Composable
fun StandardsListDialog(
    partName: String,
    dialogTitle: String,
    entries: List<ListDialogEntry>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expandedIds by remember { mutableStateOf(setOf<String>()) }

    BottomSheetScaffold(
        visible = true,
        onDismiss = onDismiss,
        eyebrow = partName,
        title = "$dialogTitle (${entries.size}건)",
        modifier = modifier
    ) {
        if (entries.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("관련 자료가 없습니다.", color = TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(items = entries, key = { it.id }) { entry ->
                    val isExpanded = expandedIds.contains(entry.id)

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceVariantDark)
                    ) {
                        // 펼쳐졌을 때만 보이는 좌측 강조 바
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(if (isExpanded) BrandBlue else Color.Transparent)
                        )

                        Column(
                            Modifier
                                .weight(1f)
                                .clickable {
                                    expandedIds = if (isExpanded)
                                        expandedIds - entry.id
                                    else
                                        expandedIds + entry.id
                                }
                                .padding(14.dp)
                        ) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    entry.header,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null,
                                    tint = TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Text(
                                    entry.body,
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp,
                                    modifier = Modifier.padding(top = 10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

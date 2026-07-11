package com.example.elevatorvision.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.elevatorvision.StandardItem
import com.example.elevatorvision.LawItem

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
    onDismiss: () -> Unit
) {
    var expandedIds by remember { mutableStateOf(setOf<String>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            tonalElevation = 6.dp
        ) {
            Column(Modifier.fillMaxSize().padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$partName - $dialogTitle (${entries.size}건)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }

                Spacer(Modifier.height(8.dp))
                Divider()

                if (entries.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("관련 자료가 없습니다.")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items = entries, key = { it.id }) { entry ->
                            val isExpanded = expandedIds.contains(entry.id)

                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedIds = if (isExpanded)
                                            expandedIds - entry.id
                                        else
                                            expandedIds + entry.id
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        entry.header,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(if (isExpanded) "▲" else "▼")
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Text(
                                        entry.body,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                }
                            }
                            Divider()
                        }
                    }
                }
            }
        }
    }
}
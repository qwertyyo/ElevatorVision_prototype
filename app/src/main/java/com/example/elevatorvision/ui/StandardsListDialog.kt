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

@Composable
fun StandardsListDialog(
    partName: String,
    items: List<StandardItem>,
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
                        text = "$partName - 표준화 안내 (${items.size}건)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onDismiss) { Text("닫기") }
                }

                Spacer(Modifier.height(8.dp))
                Divider()

                if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("관련 표준화 자료가 없습니다.")
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize()) {
                        items(items = items, key = { it.id }) { item ->
                            val isExpanded = expandedIds.contains(item.id)

                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedIds = if (isExpanded)
                                            expandedIds - item.id
                                        else
                                            expandedIds + item.id
                                    }
                                    .padding(vertical = 10.dp)
                            ) {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(item.title, style = MaterialTheme.typography.bodyLarge)
                                        if (item.year != null) {
                                            Text(
                                                "${item.year}년 ${item.round ?: ""}차",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Text(if (isExpanded) "▲" else "▼")
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Text(
                                        item.standardization,
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
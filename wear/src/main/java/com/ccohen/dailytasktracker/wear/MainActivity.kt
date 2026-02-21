package com.ccohen.dailytasktracker.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

class MainActivity : ComponentActivity() {
    private val viewModel: WatchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WatchApp(viewModel)
            }
        }
    }
}

@Composable
private fun WatchApp(viewModel: WatchViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberScalingLazyListState()
    var selectedTaskId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedTask = uiState.tasks.firstOrNull { it.id == selectedTaskId }

    LaunchedEffect(uiState.tasks, selectedTaskId) {
        if (selectedTaskId != null && selectedTask == null) {
            selectedTaskId = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                top = 36.dp,
                bottom = 28.dp,
                start = 10.dp,
                end = 10.dp
            )
        ) {
            if (selectedTask == null) {
                item {
                    RefreshChip(uiState = uiState, onRefresh = viewModel::refreshSnapshot)
                }

                if (uiState.tasks.isEmpty()) {
                    item {
                        val emptyLabel = if (uiState.isLoading) "Loading..." else stringResource(id = R.string.no_tasks)
                        Chip(
                            onClick = viewModel::refreshSnapshot,
                            label = { Text(emptyLabel) },
                            secondaryLabel = { Text(uiState.status) },
                            colors = ChipDefaults.secondaryChipColors()
                        )
                    }
                } else {
                    items(uiState.tasks, key = { it.id }) { task ->
                        Chip(
                            onClick = { selectedTaskId = task.id },
                            label = {
                                Text(
                                    text = task.name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            secondaryLabel = { Text("Count ${task.count}") },
                            colors = ChipDefaults.primaryChipColors()
                        )
                    }
                }
            } else {
                item {
                    Chip(
                        onClick = { selectedTaskId = null },
                        label = { Text("Back to Tasks") },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }

                item {
                    Chip(
                        onClick = {},
                        label = {
                            Text(
                                text = selectedTask.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        secondaryLabel = { Text("Count ${selectedTask.count}") },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }

                item {
                    Chip(
                        onClick = { viewModel.onLogTask(selectedTask.id) },
                        label = { Text("Log Task") },
                        secondaryLabel = { Text("Add one now") },
                        colors = ChipDefaults.primaryChipColors()
                    )
                }

                val canUndo = viewModel.canUndoTask(selectedTask.id)
                item {
                    Chip(
                        onClick = { viewModel.onUndoWatchLog(selectedTask.id) },
                        enabled = canUndo,
                        label = { Text("Undo Last Watch Log") },
                        secondaryLabel = {
                            Text(if (canUndo) "One-step undo" else "No watch log to undo")
                        },
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }

                if (selectedTask.events.isEmpty()) {
                    item {
                        Chip(
                            onClick = {},
                            label = { Text("No timestamps yet") },
                            secondaryLabel = { Text(uiState.status) },
                            colors = ChipDefaults.secondaryChipColors()
                        )
                    }
                } else {
                    items(selectedTask.events.take(10)) { timestamp ->
                        Chip(
                            onClick = {},
                            label = { Text(viewModel.formatEventTime(timestamp)) },
                            secondaryLabel = { Text("Logged") },
                            colors = ChipDefaults.secondaryChipColors()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshChip(uiState: WatchUiState, onRefresh: () -> Unit) {
    Chip(
        onClick = onRefresh,
        label = { Text(text = stringResource(id = R.string.refresh_tasks)) },
        secondaryLabel = {
            val label = if (uiState.lastSyncLabel.isBlank()) {
                uiState.status
            } else {
                "Synced ${uiState.lastSyncLabel}"
            }
            Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        colors = ChipDefaults.secondaryChipColors()
    )
}

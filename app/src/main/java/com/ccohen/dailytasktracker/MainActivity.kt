package com.ccohen.dailytasktracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                DailyTaskTrackerScreen(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyTaskTrackerScreen(viewModel: MainViewModel) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<UiTask?>(null) }

    LaunchedEffect(Unit) {
        viewModel.snackEvents.collect { event ->
            val result = snackbarHostState.showSnackbar(
                message = event.message,
                actionLabel = if (event.canUndo) "Undo" else null,
                duration = if (event.canUndo) {
                    SnackbarDuration.Indefinite
                } else {
                    SnackbarDuration.Short
                }
            )
            if (result == SnackbarResult.ActionPerformed && event.canUndo) {
                viewModel.onUndoLastAction()
            }
        }
    }

    if (uiState.showResetConfirmation) {
        AlertDialog(
            onDismissRequest = viewModel::onResetCancelled,
            title = { Text("Reset today's logs?") },
            text = { Text("This will erase all logs since last reset.") },
            confirmButton = {
                TextButton(onClick = viewModel::onResetConfirmed) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onResetCancelled) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showAddDialog) {
        AddOrRenameTaskDialog(
            title = "Add Task",
            initialValue = "",
            confirmLabel = "Add",
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                showAddDialog = false
                viewModel.onAddTask(name)
            }
        )
    }

    renameTarget?.let { task ->
        AddOrRenameTaskDialog(
            title = "Rename Task",
            initialValue = task.name,
            confirmLabel = "Save",
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                viewModel.onRenameTask(task.id, name)
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (uiState.reorderMode) "Reorder Tasks" else "Daily Task Tracker")
                },
                actions = {
                    if (uiState.reorderMode) {
                        IconButton(onClick = viewModel::onSaveReorder) {
                            Icon(Icons.Default.Check, contentDescription = "Save order")
                        }
                    } else {
                        IconButton(onClick = viewModel::onStartReorder) {
                            Icon(Icons.Default.Reorder, contentDescription = "Reorder")
                        }
                    }
                    IconButton(onClick = viewModel::onResetClicked) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset")
                    }
                }
            )
        },
        floatingActionButton = {
            if (!uiState.reorderMode) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add task")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Tracking since ${formatResetTimestamp(uiState.lastResetAt)}",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.tasks.isEmpty()) {
                Text(
                    text = "No tasks yet. Tap + to add one.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items = uiState.tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            reorderMode = uiState.reorderMode,
                            canMoveUp = uiState.canMoveTaskUp(task.id),
                            canMoveDown = uiState.canMoveTaskDown(task.id),
                            onToggleExpand = { viewModel.onToggleTaskExpanded(task.id) },
                            onLog = { viewModel.onLogTask(task.id) },
                            onMoveUp = { viewModel.onMoveTaskUp(task.id) },
                            onMoveDown = { viewModel.onMoveTaskDown(task.id) },
                            onRename = { renameTarget = task },
                            onDelete = { viewModel.onDeleteTask(task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(
    task: UiTask,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleExpand: () -> Unit,
    onLog: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() }
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = task.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "${task.count}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (reorderMode) {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                    }
                } else {
                    IconButton(onClick = onLog, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Add, contentDescription = "Log task")
                    }
                    TaskMenu(onRename = onRename, onDelete = onDelete)
                }
            }

            if (task.expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                if (task.events.isEmpty()) {
                    Text(
                        text = "No entries yet",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        task.events.forEach { timestamp ->
                            Text(
                                text = formatEventTimestamp(timestamp),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    IconButton(onClick = { expanded = true }, modifier = Modifier.size(36.dp)) {
        Icon(Icons.Default.MoreVert, contentDescription = "Task options")
    }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
    ) {
        DropdownMenuItem(
            text = { Text("Rename") },
            onClick = {
                expanded = false
                onRename()
            }
        )
        DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
                expanded = false
                onDelete()
            }
        )
    }
}

@Composable
private fun AddOrRenameTaskDialog(
    title: String,
    initialValue: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                label = { Text("Task name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

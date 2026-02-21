package com.ccohen.dailytasktracker

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiTask(
    val id: String,
    val name: String,
    val count: Int,
    val events: List<Long>,
    val expanded: Boolean
)

data class MainUiState(
    val tasks: List<UiTask> = emptyList(),
    val orderedTaskIds: List<String> = emptyList(),
    val reorderMode: Boolean = false,
    val lastResetAt: Long = 0L,
    val showResetConfirmation: Boolean = false
) {
    fun canMoveTaskUp(taskId: String): Boolean {
        val index = orderedTaskIds.indexOf(taskId)
        return index > 0
    }

    fun canMoveTaskDown(taskId: String): Boolean {
        val index = orderedTaskIds.indexOf(taskId)
        return index >= 0 && index < orderedTaskIds.lastIndex
    }
}

data class SnackEvent(
    val message: String,
    val canUndo: Boolean
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaskRepository.create(application)

    private val expandedTaskIds = MutableStateFlow<Set<String>>(emptySet())
    private val reorderMode = MutableStateFlow(false)
    private val reorderDraftIds = MutableStateFlow<List<String>?>(null)
    private val showResetConfirmation = MutableStateFlow(false)

    private val _snackEvents = MutableSharedFlow<SnackEvent>()
    val snackEvents = _snackEvents.asSharedFlow()

    private var undoSnapshot: AppState? = null

    val uiState: StateFlow<MainUiState> = combine(
        repository.state,
        expandedTaskIds,
        reorderMode,
        reorderDraftIds,
        showResetConfirmation
    ) { state, expanded, isReorder, draftIds, shouldConfirmReset ->
        val orderedTasks = orderTasks(state.tasks, draftIds)
        MainUiState(
            tasks = orderedTasks.map { task ->
                UiTask(
                    id = task.id,
                    name = task.name,
                    count = task.events.size,
                    events = task.events.sortedDescending(),
                    expanded = expanded.contains(task.id)
                )
            },
            orderedTaskIds = orderedTasks.map { it.id },
            reorderMode = isReorder,
            lastResetAt = state.lastResetAt,
            showResetConfirmation = shouldConfirmReset
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MainUiState()
    )

    fun onToggleTaskExpanded(taskId: String) {
        expandedTaskIds.value = expandedTaskIds.value.toMutableSet().apply {
            if (contains(taskId)) remove(taskId) else add(taskId)
        }
    }

    fun onAddTask(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showMessage("Task name cannot be empty", canUndo = false)
            return
        }
        launchMutation("Task added") {
            repository.addTask(trimmed)
        }
    }

    fun onRenameTask(taskId: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            showMessage("Task name cannot be empty", canUndo = false)
            return
        }
        launchMutation("Task renamed") {
            repository.renameTask(taskId, trimmed)
        }
    }

    fun onDeleteTask(taskId: String) {
        launchMutation("Task deleted") {
            repository.deleteTask(taskId)
        }
        expandedTaskIds.value = expandedTaskIds.value - taskId
    }

    fun onLogTask(taskId: String) {
        launchMutation("Logged task") {
            repository.logTask(taskId, System.currentTimeMillis())
        }
    }

    fun onResetClicked() {
        viewModelScope.launch {
            val state = repository.state.value
            val now = System.currentTimeMillis()
            if (isSameLocalDay(state.lastResetAt, now)) {
                showResetConfirmation.value = true
            } else {
                performReset(now)
            }
        }
    }

    fun onResetConfirmed() {
        showResetConfirmation.value = false
        performReset(System.currentTimeMillis())
    }

    fun onResetCancelled() {
        showResetConfirmation.value = false
    }

    fun onStartReorder() {
        val currentIds = repository.state.value.tasks
            .sortedBy { it.sortOrder }
            .map { it.id }
        reorderDraftIds.value = currentIds
        reorderMode.value = true
    }

    fun onMoveTaskUp(taskId: String) {
        moveDraftTask(taskId, -1)
    }

    fun onMoveTaskDown(taskId: String) {
        moveDraftTask(taskId, 1)
    }

    fun onSaveReorder() {
        val draftIds = reorderDraftIds.value ?: return
        launchMutation("Task order saved") {
            repository.setTaskOrder(draftIds)
        }
        reorderMode.value = false
        reorderDraftIds.value = null
    }

    fun onUndoLastAction() {
        val snapshot = undoSnapshot ?: return
        viewModelScope.launch {
            val restored = repository.restore(snapshot)
            if (restored) {
                undoSnapshot = null
                showMessage("Last action undone", canUndo = false)
            }
        }
    }

    private fun performReset(now: Long) {
        launchMutation("Reset all counts") {
            repository.resetEvents(now)
        }
    }

    private fun moveDraftTask(taskId: String, delta: Int) {
        val draft = reorderDraftIds.value?.toMutableList() ?: return
        val fromIndex = draft.indexOf(taskId)
        if (fromIndex == -1) return

        val toIndex = fromIndex + delta
        if (toIndex !in draft.indices) return

        val moved = draft.removeAt(fromIndex)
        draft.add(toIndex, moved)
        reorderDraftIds.value = draft
    }

    private fun launchMutation(message: String, mutation: suspend () -> Boolean) {
        viewModelScope.launch {
            val snapshot = repository.snapshot()
            val changed = mutation()
            if (changed) {
                undoSnapshot = snapshot
                showMessage(message, canUndo = true)
            }
        }
    }

    private fun showMessage(message: String, canUndo: Boolean) {
        viewModelScope.launch {
            _snackEvents.emit(SnackEvent(message, canUndo))
        }
    }

    private fun orderTasks(tasks: List<TaskItem>, draftIds: List<String>?): List<TaskItem> {
        val defaultOrdered = tasks.sortedBy { it.sortOrder }
        if (draftIds.isNullOrEmpty()) {
            return defaultOrdered
        }

        val byId = defaultOrdered.associateBy { it.id }
        val ordered = draftIds.mapNotNull { byId[it] }
        val missing = defaultOrdered.filterNot { task -> draftIds.contains(task.id) }
        return ordered + missing
    }
}

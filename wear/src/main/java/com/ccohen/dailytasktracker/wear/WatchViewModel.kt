package com.ccohen.dailytasktracker.wear

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WatchTask(
    val id: String,
    val name: String,
    val count: Int,
    val events: List<Long>
)

data class WatchLogAction(
    val taskId: String,
    val timestamp: Long
)

data class WatchUiState(
    val tasks: List<WatchTask> = emptyList(),
    val lastSyncLabel: String = "",
    val status: String = "Waiting for phone...",
    val isLoading: Boolean = false,
    val lastWatchLogAction: WatchLogAction? = null
)

class WatchViewModel(application: Application) : AndroidViewModel(application),
    MessageClient.OnMessageReceivedListener {

    private val appContext = application.applicationContext
    private val messageClient = Wearable.getMessageClient(appContext)
    private val nodeClient = Wearable.getNodeClient(appContext)

    private val _uiState = MutableStateFlow(WatchUiState())
    val uiState: StateFlow<WatchUiState> = _uiState.asStateFlow()
    private var syncRequestId: Long = 0L

    private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    init {
        messageClient.addListener(this)
        refreshSnapshot()
    }

    fun refreshSnapshot() {
        viewModelScope.launch {
            val requestId = ++syncRequestId
            _uiState.update { it.copy(isLoading = true, status = "Syncing...") }
            val sent = sendMessageToPhone(REQUEST_SNAPSHOT_PATH, ByteArray(0))
            if (!sent) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        status = "Phone not connected"
                    )
                }
                return@launch
            }
            startSyncTimeout(requestId)
        }
    }

    fun onLogTask(taskId: String) {
        viewModelScope.launch {
            val requestId = ++syncRequestId
            val timestamp = System.currentTimeMillis()

            _uiState.update { it.copy(isLoading = true, status = "Logging...") }
            val payload = JSONObject().apply {
                put("taskId", taskId)
                put("timestamp", timestamp)
            }.toString().toByteArray(StandardCharsets.UTF_8)

            val sent = sendMessageToPhone(LOG_TASK_PATH, payload)
            if (!sent) {
                _uiState.update { it.copy(isLoading = false, status = "Could not send log") }
                return@launch
            }

            _uiState.update { it.copy(lastWatchLogAction = WatchLogAction(taskId, timestamp)) }
            startSyncTimeout(requestId)
        }
    }

    fun onUndoWatchLog(taskId: String) {
        val action = _uiState.value.lastWatchLogAction
        if (action == null || action.taskId != taskId) {
            _uiState.update { it.copy(status = "Nothing to undo") }
            return
        }

        viewModelScope.launch {
            val requestId = ++syncRequestId
            _uiState.update { it.copy(isLoading = true, status = "Undoing...") }

            val payload = JSONObject().apply {
                put("taskId", action.taskId)
                put("timestamp", action.timestamp)
            }.toString().toByteArray(StandardCharsets.UTF_8)

            val sent = sendMessageToPhone(UNDO_WATCH_LOG_PATH, payload)
            if (!sent) {
                _uiState.update { it.copy(isLoading = false, status = "Could not undo") }
                return@launch
            }

            startSyncTimeout(requestId)
        }
    }

    fun canUndoTask(taskId: String): Boolean {
        val action = _uiState.value.lastWatchLogAction
        return action != null && action.taskId == taskId
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != SNAPSHOT_PATH) {
            return
        }

        try {
            val payload = String(messageEvent.data, StandardCharsets.UTF_8)
            val root = JSONObject(payload)
            val tasks = mutableListOf<WatchTask>()

            val taskArray = root.optJSONArray("tasks")
            if (taskArray != null) {
                for (i in 0 until taskArray.length()) {
                    val taskObj = taskArray.optJSONObject(i) ?: continue
                    val eventArray = taskObj.optJSONArray("events")
                    val events = mutableListOf<Long>()
                    if (eventArray != null) {
                        for (j in 0 until eventArray.length()) {
                            events.add(eventArray.optLong(j))
                        }
                    }

                    tasks.add(
                        WatchTask(
                            id = taskObj.optString("id"),
                            name = taskObj.optString("name"),
                            count = taskObj.optInt("count", 0),
                            events = events.sortedDescending()
                        )
                    )
                }
            }

            val currentAction = _uiState.value.lastWatchLogAction
            val actionStillPresent = currentAction != null &&
                tasks.any { task ->
                    task.id == currentAction.taskId && task.events.contains(currentAction.timestamp)
                }

            val nowLabel = Instant.now()
                .atZone(ZoneId.systemDefault())
                .format(timeFormatter)

            _uiState.update {
                it.copy(
                    tasks = tasks,
                    lastSyncLabel = nowLabel,
                    status = "Synced",
                    isLoading = false,
                    lastWatchLogAction = if (actionStillPresent) currentAction else null
                )
            }
        } catch (_: Exception) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    status = "Sync failed"
                )
            }
        }
    }

    override fun onCleared() {
        messageClient.removeListener(this)
        super.onCleared()
    }

    fun formatEventTime(timestamp: Long): String {
        return Instant.ofEpochMilli(timestamp)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("h:mm a"))
    }

    private suspend fun sendMessageToPhone(path: String, payload: ByteArray): Boolean {
        return try {
            val connectedNodes = nodeClient.connectedNodes.await()
            if (connectedNodes.isEmpty()) {
                return false
            }

            connectedNodes.forEach { node ->
                messageClient.sendMessage(node.id, path, payload).await()
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun startSyncTimeout(requestId: Long) {
        viewModelScope.launch {
            delay(6000)
            if (requestId == syncRequestId && _uiState.value.isLoading) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        status = "No response from phone"
                    )
                }
            }
        }
    }
}

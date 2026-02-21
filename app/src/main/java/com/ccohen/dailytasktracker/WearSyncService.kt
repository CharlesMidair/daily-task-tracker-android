package com.ccohen.dailytasktracker

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class WearSyncService : WearableListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: TaskRepository by lazy { TaskRepository.create(applicationContext) }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            REQUEST_SNAPSHOT_PATH -> {
                serviceScope.launch {
                    sendSnapshotToNode(messageEvent.sourceNodeId)
                }
            }

            LOG_TASK_PATH -> {
                serviceScope.launch {
                    handleLogTask(messageEvent)
                }
            }

            UNDO_WATCH_LOG_PATH -> {
                serviceScope.launch {
                    handleUndoWatchLog(messageEvent)
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun handleLogTask(messageEvent: MessageEvent) {
        val payload = runCatching {
            String(messageEvent.data, StandardCharsets.UTF_8)
        }.getOrElse { "" }

        val parsed = runCatching { JSONObject(payload) }.getOrNull()
        val taskId = parsed?.optString("taskId").orEmpty()
        val timestamp = parsed?.optLong("timestamp", -1L) ?: -1L

        if (taskId.isNotBlank()) {
            val writeTimestamp = if (timestamp > 0) timestamp else System.currentTimeMillis()
            repository.logTask(taskId, writeTimestamp)
        }

        sendSnapshotToNode(messageEvent.sourceNodeId)
    }

    private suspend fun handleUndoWatchLog(messageEvent: MessageEvent) {
        val payload = runCatching {
            String(messageEvent.data, StandardCharsets.UTF_8)
        }.getOrElse { "" }

        val parsed = runCatching { JSONObject(payload) }.getOrNull()
        val taskId = parsed?.optString("taskId").orEmpty()
        val timestamp = parsed?.optLong("timestamp", -1L) ?: -1L

        if (taskId.isNotBlank() && timestamp > 0L) {
            repository.undoTaskLog(taskId, timestamp)
        }

        sendSnapshotToNode(messageEvent.sourceNodeId)
    }

    private suspend fun sendSnapshotToNode(nodeId: String) {
        val state = repository.snapshot()
        val payload = JSONObject().apply {
            put("lastResetAt", state.lastResetAt)
            put("tasks", JSONArray().apply {
                state.tasks
                    .sortedBy { it.sortOrder }
                    .forEach { task ->
                        put(JSONObject().apply {
                            put("id", task.id)
                            put("name", task.name)
                            put("count", task.events.size)
                            put("events", JSONArray().apply {
                                task.events.forEach { eventTs ->
                                    put(eventTs)
                                }
                            })
                        })
                    }
            })
        }.toString().toByteArray(StandardCharsets.UTF_8)

        runCatching {
            Wearable.getMessageClient(this)
                .sendMessage(nodeId, SNAPSHOT_PATH, payload)
                .await()
        }
    }
}

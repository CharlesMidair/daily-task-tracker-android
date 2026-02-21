package com.ccohen.dailytasktracker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class TaskItem(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val events: List<Long>
)

data class AppState(
    val lastResetAt: Long,
    val tasks: List<TaskItem>
)

fun AppState.deepCopy(): AppState {
    return copy(tasks = tasks.map { it.copy(events = it.events.toList()) })
}

class TaskRepository(
    private val prefs: SharedPreferences,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    companion object {
        private const val PREFS_NAME = "daily_task_tracker_prefs"
        private const val KEY_STATE = "app_state_json"
        @Volatile
        private var instance: TaskRepository? = null

        fun create(context: Context): TaskRepository {
            return instance ?: synchronized(this) {
                instance ?: TaskRepository(
                    context.applicationContext.getSharedPreferences(
                        PREFS_NAME,
                        Context.MODE_PRIVATE
                    )
                ).also { instance = it }
            }
        }
    }

    fun snapshot(): AppState = _state.value.deepCopy()

    suspend fun restore(snapshot: AppState): Boolean {
        return update { snapshot }
    }

    suspend fun addTask(name: String): Boolean {
        return update { current ->
            val maxOrder = current.tasks.maxOfOrNull { it.sortOrder } ?: -1
            val newTask = TaskItem(
                id = UUID.randomUUID().toString(),
                name = name,
                sortOrder = maxOrder + 1,
                events = emptyList()
            )
            current.copy(tasks = current.tasks + newTask)
        }
    }

    suspend fun renameTask(taskId: String, newName: String): Boolean {
        return update { current ->
            current.copy(
                tasks = current.tasks.map { task ->
                    if (task.id == taskId) task.copy(name = newName) else task
                }
            )
        }
    }

    suspend fun deleteTask(taskId: String): Boolean {
        return update { current ->
            current.copy(tasks = current.tasks.filterNot { it.id == taskId })
        }
    }

    suspend fun logTask(taskId: String, timestamp: Long): Boolean {
        return update { current ->
            current.copy(
                tasks = current.tasks.map { task ->
                    if (task.id == taskId) {
                        task.copy(events = task.events + timestamp)
                    } else {
                        task
                    }
                }
            )
        }
    }

    suspend fun undoTaskLog(taskId: String, timestamp: Long): Boolean {
        return update { current ->
            current.copy(
                tasks = current.tasks.map { task ->
                    if (task.id != taskId) {
                        task
                    } else {
                        val index = task.events.indexOfLast { it == timestamp }
                        if (index == -1) {
                            task
                        } else {
                            task.copy(events = task.events.toMutableList().apply { removeAt(index) })
                        }
                    }
                }
            )
        }
    }

    suspend fun resetEvents(now: Long): Boolean {
        return update { current ->
            current.copy(
                lastResetAt = now,
                tasks = current.tasks.map { it.copy(events = emptyList()) }
            )
        }
    }

    suspend fun setTaskOrder(taskIds: List<String>): Boolean {
        return update { current ->
            val byId = current.tasks.associateBy { it.id }
            val ordered = taskIds.mapNotNull { byId[it] }
            val remaining = current.tasks.filterNot { task -> taskIds.contains(task.id) }
            val all = ordered + remaining

            current.copy(
                tasks = all.mapIndexed { index, task ->
                    task.copy(sortOrder = index)
                }
            )
        }
    }

    private suspend fun update(transform: (AppState) -> AppState): Boolean {
        return withContext(dispatcher) {
            mutex.withLock {
                val current = _state.value
                val transformed = normalize(transform(current))
                if (transformed == current) {
                    return@withLock false
                }

                _state.value = transformed
                persist(transformed)
                true
            }
        }
    }

    private fun normalize(state: AppState): AppState {
        val ordered = state.tasks
            .sortedBy { it.sortOrder }
            .mapIndexed { index, task ->
                task.copy(sortOrder = index)
            }
        return state.copy(tasks = ordered)
    }

    private fun loadState(): AppState {
        val raw = prefs.getString(KEY_STATE, null)
        if (raw.isNullOrBlank()) {
            return AppState(
                lastResetAt = System.currentTimeMillis(),
                tasks = emptyList()
            )
        }

        return try {
            val root = JSONObject(raw)
            val lastResetAt = root.optLong("lastResetAt", System.currentTimeMillis())
            val taskArray = root.optJSONArray("tasks") ?: JSONArray()
            val tasks = mutableListOf<TaskItem>()

            for (i in 0 until taskArray.length()) {
                val taskObj = taskArray.optJSONObject(i) ?: continue
                val eventsArray = taskObj.optJSONArray("events") ?: JSONArray()
                val events = mutableListOf<Long>()
                for (j in 0 until eventsArray.length()) {
                    events.add(eventsArray.optLong(j))
                }

                tasks.add(
                    TaskItem(
                        id = taskObj.optString("id", UUID.randomUUID().toString()),
                        name = taskObj.optString("name", "Task"),
                        sortOrder = taskObj.optInt("sortOrder", i),
                        events = events
                    )
                )
            }

            normalize(AppState(lastResetAt = lastResetAt, tasks = tasks))
        } catch (_: Exception) {
            AppState(
                lastResetAt = System.currentTimeMillis(),
                tasks = emptyList()
            )
        }
    }

    private fun persist(state: AppState) {
        val root = JSONObject().apply {
            put("lastResetAt", state.lastResetAt)
            put("tasks", JSONArray().apply {
                state.tasks.sortedBy { it.sortOrder }.forEach { task ->
                    put(JSONObject().apply {
                        put("id", task.id)
                        put("name", task.name)
                        put("sortOrder", task.sortOrder)
                        put("events", JSONArray().apply {
                            task.events.forEach { eventTs ->
                                put(eventTs)
                            }
                        })
                    })
                }
            })
        }

        prefs.edit().putString(KEY_STATE, root.toString()).apply()
    }
}

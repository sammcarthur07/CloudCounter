// HistoryViewModel.kt
package com.vibecode.cloudcounter

import android.app.Application
import androidx.lifecycle.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

sealed class HistoryItem {
    abstract val timestamp: Long

    data class ActivityItem(val log: ActivityLog) : HistoryItem() {
        override val timestamp: Long = log.timestamp
    }

    data class SummaryItem(val summary: SessionSummary) : HistoryItem() {
        override val timestamp: Long = summary.timestamp
    }
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        var onReverseGoal: (suspend (ActivityLog, Smoker, String?) -> Unit)? = null
        var onRestoreStash: (suspend (ActivityLog, Smoker) -> Unit)? = null
    }

    private val repository = (application as CloudCounterApplication).repository

    private val logsSource: LiveData<List<ActivityLog>> =
        repository.allLogs

    private val summariesSource: LiveData<List<SessionSummary>> =
        repository.allSummaries

    private val _allItems = MediatorLiveData<List<HistoryItem>>()
    val allItems: LiveData<List<HistoryItem>> = _allItems

    init {
        _allItems.addSource(logsSource) { combineItems() }
        _allItems.addSource(summariesSource) { combineItems() }
    }

    private fun combineItems() {
        val logs = logsSource.value.orEmpty().map { HistoryItem.ActivityItem(it) }
        val summaries = summariesSource.value.orEmpty().map { HistoryItem.SummaryItem(it) }
        
        // DEBUG: Log session details
        summaries.forEach { item ->
            if (item is HistoryItem.SummaryItem) {
                val s = item.summary
                val effectiveTimestamp = if (s.isActive) s.timestamp else (s.timestamp + s.sessionLength)
                android.util.Log.d("HistoryViewModel", "📋 Session ${s.id}: timestamp=${s.timestamp}, effective=${effectiveTimestamp}, isActive=${s.isActive}, length=${s.sessionLength}ms")
            }
        }
        
        // Separate active and inactive sessions
        val activeSessions = summaries.filter { it is HistoryItem.SummaryItem && it.summary.isActive }
        val inactiveSessions = summaries.filter { it is HistoryItem.SummaryItem && !it.summary.isActive }
        
        android.util.Log.d("HistoryViewModel", "📋 Active sessions: ${activeSessions.size}, Inactive: ${inactiveSessions.size}")
        
        // Sort active sessions by their start timestamp (newest first)
        val sortedActive = activeSessions.sortedByDescending { it.timestamp }
        
        // Combine inactive sessions and logs, sorting with custom timestamp logic
        val sortedRest = (inactiveSessions + logs).sortedByDescending { item ->
            when (item) {
                is HistoryItem.SummaryItem -> {
                    // For inactive sessions, use end time (start + length) for sorting
                    item.summary.timestamp + item.summary.sessionLength
                }
                is HistoryItem.ActivityItem -> {
                    // For activities, use their actual timestamp
                    item.timestamp
                }
            }
        }
        
        android.util.Log.d("HistoryViewModel", "📋 Sorted items - Active: ${sortedActive.size}, Rest: ${sortedRest.size}")
        
        // Combine with active sessions always at the top
        _allItems.value = sortedActive + sortedRest
    }

    fun deleteLog(log: ActivityLog) {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("HistoryViewModel", "🗑️ Deleting activity with callbacks: ${log.type}")
            
            // Get session share code if available
            val prefs = getApplication<CloudCounterApplication>().getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
            val sessionShareCode = if (log.sessionId != null && log.sessionId != 0L) {
                prefs.getString("currentShareCode", null)
            } else null
            
            // Use deleteWithCallbacks to handle goal and stash reversal
            repository.deleteWithCallbacks(
                log = log,
                onReverseGoal = if (onReverseGoal != null) { activityLog, smoker ->
                    android.util.Log.d("HistoryViewModel", "🎯 Reversing goal progress for ${activityLog.type} by ${smoker.name}")
                    onReverseGoal?.invoke(activityLog, smoker, sessionShareCode)
                } else null,
                onRestoreStash = if (onRestoreStash != null) { activityLog, smoker ->
                    android.util.Log.d("HistoryViewModel", "📦 Restoring stash for ${activityLog.type} by ${smoker.name}")
                    onRestoreStash?.invoke(activityLog, smoker)
                } else null
            )
        }
    }
    
    /** 
     * Comprehensive single activity deletion with cloud sync
     * This method ensures the activity is fully deleted from both local and cloud storage
     */
    fun deleteActivityWithCloudSync(log: ActivityLog) {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("HistoryViewModel", "🗑️ ========== SINGLE ACTIVITY DELETION START ==========")
            android.util.Log.d("HistoryViewModel", "🗑️ Deleting activity ${log.id}: ${log.type} at ${log.timestamp}")
            android.util.Log.d("HistoryViewModel", "🗑️   - Type: ${log.type}")
            android.util.Log.d("HistoryViewModel", "🗑️   - Smoker ID: ${log.smokerId}")
            android.util.Log.d("HistoryViewModel", "🗑️   - Session ID: ${log.sessionId ?: "null"}")
            android.util.Log.d("HistoryViewModel", "🗑️   - Timestamp: ${log.timestamp}")
            
            // Get session share code if available
            val prefs = getApplication<CloudCounterApplication>().getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
            val sessionShareCode = if (log.sessionId != null && log.sessionId != 0L) {
                prefs.getString("currentShareCode", null)
            } else null
            
            // Delete with callbacks to handle goal and stash reversal
            android.util.Log.d("HistoryViewModel", "🗑️ Deleting from local database with callbacks...")
            repository.deleteWithCallbacks(
                log = log,
                onReverseGoal = if (onReverseGoal != null) { activityLog, smoker ->
                    android.util.Log.d("HistoryViewModel", "🎯 Reversing goal progress for ${activityLog.type} by ${smoker.name}")
                    onReverseGoal?.invoke(activityLog, smoker, sessionShareCode)
                } else null,
                onRestoreStash = if (onRestoreStash != null) { activityLog, smoker ->
                    android.util.Log.d("HistoryViewModel", "📦 Restoring stash for ${activityLog.type} by ${smoker.name}")
                    onRestoreStash?.invoke(activityLog, smoker)
                } else null
            )
            android.util.Log.d("HistoryViewModel", "🗑️   ✅ Deleted from local database with callbacks")
            
            if (!sessionShareCode.isNullOrEmpty()) {
                android.util.Log.d("HistoryViewModel", "🗑️ Activity was part of cloud room: $sessionShareCode")
            }
            
            android.util.Log.d("HistoryViewModel", "🗑️ ========== SINGLE ACTIVITY DELETION COMPLETE ==========")
            
            // The LiveData/Flow will automatically notify observers of the change
        }
    }

    fun insertLog(log: ActivityLog) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insert(log)
        }
    }

    /** NEW: delete a session summary */
    fun deleteSummary(summary: SessionSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteSummary(summary)
        }
    }

    /** Delete a session summary along with all its activities */
    fun deleteSessionWithActivities(summary: SessionSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            android.util.Log.d("HistoryViewModel", "🗑️ ========== DELETION START ==========")
            android.util.Log.d("HistoryViewModel", "🗑️ Deleting session ${summary.id}")
            android.util.Log.d("HistoryViewModel", "🗑️ Session timestamp (sessionId): ${summary.timestamp}")
            android.util.Log.d("HistoryViewModel", "🗑️ Session length: ${summary.sessionLength}ms (${summary.sessionLength/1000/60} minutes)")
            
            // Calculate the session time range
            val sessionStart = summary.timestamp
            val sessionEnd = summary.timestamp + summary.sessionLength
            
            android.util.Log.d("HistoryViewModel", "🗑️ Time range: $sessionStart to $sessionEnd")
            
            // Get activities by BOTH time range AND sessionId for comprehensive deletion
            android.util.Log.d("HistoryViewModel", "🗑️ Searching for activities by time range ($sessionStart, $sessionEnd)...")
            val activitiesByTime = repository.getLogsBetweenTimestamps(sessionStart, sessionEnd)
            android.util.Log.d("HistoryViewModel", "🗑️ Found ${activitiesByTime.size} activities by time range")
            activitiesByTime.forEach { activity ->
                android.util.Log.d("HistoryViewModel", "🗑️   - Time: ${activity.type} at ${activity.timestamp}, sessionId=${activity.sessionId}, id=${activity.id}")
            }
            
            android.util.Log.d("HistoryViewModel", "🗑️ Searching for activities by sessionId (${summary.timestamp})...")
            val activitiesBySessionId = repository.getActivitiesBySessionId(summary.timestamp)
            android.util.Log.d("HistoryViewModel", "🗑️ Found ${activitiesBySessionId.size} activities by sessionId")
            activitiesBySessionId.forEach { activity ->
                android.util.Log.d("HistoryViewModel", "🗑️   - SessionId: ${activity.type} at ${activity.timestamp}, sessionId=${activity.sessionId}, id=${activity.id}")
            }
            
            // Combine both lists and remove duplicates by activity ID
            val sessionActivities = (activitiesByTime + activitiesBySessionId).distinctBy { it.id }
            
            android.util.Log.d("HistoryViewModel", "🗑️ Total unique activities to delete: ${sessionActivities.size}")
            
            // Get session share code if available
            val prefs = getApplication<CloudCounterApplication>().getSharedPreferences("sesh", android.content.Context.MODE_PRIVATE)
            val sessionShareCode = if (summary.timestamp != 0L) {
                prefs.getString("currentShareCode", null)
            } else null
            
            // Delete each activity with callbacks for goal and stash reversal
            sessionActivities.forEach { activity ->
                android.util.Log.d("HistoryViewModel", "🗑️ Deleting activity ${activity.id}: ${activity.type} at ${activity.timestamp}")
                
                // Use deleteWithCallbacks to handle goal and stash reversal
                repository.deleteWithCallbacks(
                    log = activity,
                    onReverseGoal = if (onReverseGoal != null) { activityLog, smoker ->
                        android.util.Log.d("HistoryViewModel", "🎯 Reversing goal progress for ${activityLog.type} by ${smoker.name}")
                        onReverseGoal?.invoke(activityLog, smoker, sessionShareCode)
                    } else null,
                    onRestoreStash = if (onRestoreStash != null) { activityLog, smoker ->
                        android.util.Log.d("HistoryViewModel", "📦 Restoring stash for ${activityLog.type} by ${smoker.name}")
                        onRestoreStash?.invoke(activityLog, smoker)
                    } else null
                )
                android.util.Log.d("HistoryViewModel", "🗑️   ✅ Deleted activity ${activity.id} with callbacks")
            }
            
            // Delete the summary itself (repository handles both local and cloud deletion)
            android.util.Log.d("HistoryViewModel", "🗑️ Deleting session summary ${summary.id}")
            repository.deleteSummary(summary)
            android.util.Log.d("HistoryViewModel", "🗑️   ✅ Deleted session ${summary.id}")
            
            android.util.Log.d("HistoryViewModel", "🗑️ ========== DELETION COMPLETE ==========")
            
            // The LiveData/Flow will automatically notify observers of the change
        }
    }
}

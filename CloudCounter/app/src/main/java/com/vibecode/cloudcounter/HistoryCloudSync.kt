package com.vibecode.cloudcounter

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Handles cloud synchronization of activity history and session summaries
 * with pagination support and local caching
 */
class HistoryCloudSync {
    private val firestore = FirebaseFirestore.getInstance()
    
    companion object {
        private const val TAG = "HistoryCloudSync"
        private const val PAGE_SIZE = 100
        
        // Collection names
        private const val USERS_COLLECTION = "users"
        private const val ACTIVITY_HISTORY = "activity_history"
        private const val SESSION_SUMMARIES = "session_summaries"
        private const val SMOKERS = "smokers"
        
        // Activity fields
        private const val FIELD_ID = "id"
        private const val FIELD_TIMESTAMP = "timestamp"
        private const val FIELD_TYPE = "type"
        private const val FIELD_SESSION_ID = "sessionId"
        private const val FIELD_SMOKER_ID = "smokerId"
        // smokerName removed - not part of ActivityLog
        private const val FIELD_CONSUMER_ID = "consumerId"
        private const val FIELD_PAYER_STASH_OWNER_ID = "payerStashOwnerId"
        private const val FIELD_GRAMS_AT_LOG = "gramsAtLog"
        private const val FIELD_PRICE_PER_GRAM_AT_LOG = "pricePerGramAtLog"
        private const val FIELD_CUSTOM_ACTIVITY_ID = "customActivityId"
        private const val FIELD_CUSTOM_ACTIVITY_NAME = "customActivityName"
        private const val FIELD_SESSION_START_TIME = "sessionStartTime"
        private const val FIELD_BOWL_QUANTITY = "bowlQuantity"
        
        // Session fields
        private const val FIELD_SESSION_ID_VALUE = "id"
        private const val FIELD_SMOKER_NAMES = "smokerNames"
        private const val FIELD_CONES_PER_SMOKER = "conesPerSmoker"
        private const val FIELD_TOTAL_CONES = "totalCones"
        private const val FIELD_ROUNDS = "rounds"
        private const val FIELD_SESSION_LENGTH = "sessionLength"
        private const val FIELD_LONGEST_INTERVAL = "longestInterval"
        private const val FIELD_SHORTEST_INTERVAL = "shortestInterval"
        private const val FIELD_SESSION_TIMESTAMP = "timestamp"
        private const val FIELD_LIVE_SYNC_ENABLED = "liveSyncEnabled"
        private const val FIELD_SHARE_CODE = "shareCode"
        private const val FIELD_ROOM_NAME = "roomName"
        private const val FIELD_ACTIVITY_BREAKDOWN = "activityBreakdown"
        private const val FIELD_IS_ACTIVE = "isActive"
        private const val FIELD_LAST_SYNC = "lastSync"
    }
    
    private var lastActivityDocument: DocumentSnapshot? = null
    private var lastSessionDocument: DocumentSnapshot? = null
    
    /**
     * Upload a single activity to Firestore
     */
    suspend fun uploadActivity(userId: String, activity: ActivityLog): Result<Unit> {
        return try {
            Log.d(TAG, "📤 Uploading activity: ${activity.type} at ${activity.timestamp}")
            
            val activityData = hashMapOf(
                FIELD_ID to activity.id,  // FIX: Store the activity ID for deletion
                FIELD_TIMESTAMP to activity.timestamp,
                FIELD_TYPE to activity.type.name,
                FIELD_SESSION_ID to activity.sessionId,
                FIELD_SMOKER_ID to activity.smokerId,
                // smokerName not stored - will be resolved from smokerId
                FIELD_CONSUMER_ID to activity.consumerId,
                FIELD_PAYER_STASH_OWNER_ID to activity.payerStashOwnerId,
                FIELD_GRAMS_AT_LOG to activity.gramsAtLog,
                FIELD_PRICE_PER_GRAM_AT_LOG to activity.pricePerGramAtLog,
                FIELD_CUSTOM_ACTIVITY_ID to activity.customActivityId,
                FIELD_CUSTOM_ACTIVITY_NAME to activity.customActivityName,
                FIELD_SESSION_START_TIME to activity.sessionStartTime,
                FIELD_BOWL_QUANTITY to activity.bowlQuantity,
                FIELD_LAST_SYNC to Date()
            )
            
            // Use timestamp as document ID for uniqueness
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(ACTIVITY_HISTORY)
                .document(activity.timestamp.toString())
                .set(activityData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "✅ Activity uploaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload activity", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download activities with pagination
     * Returns pair of (activities, hasMore)
     */
    suspend fun downloadActivities(
        userId: String,
        startAfter: DocumentSnapshot? = null
    ): Result<Pair<List<ActivityLog>, Boolean>> {
        return try {
            Log.d(TAG, "📥 Downloading activities page for user: $userId")
            Log.d(TAG, "📥 Starting after: ${startAfter?.id}")
            
            var query = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(ACTIVITY_HISTORY)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())
            
            if (startAfter != null) {
                query = query.startAfter(startAfter)
            }
            
            val snapshot = query.get().await()
            
            val activities = snapshot.documents.mapNotNull { doc ->
                try {
                    ActivityLog(
                        id = doc.getLong(FIELD_ID) ?: 0,  // FIX: Read ID from field, not document ID
                        timestamp = doc.getLong(FIELD_TIMESTAMP) ?: 0,
                        type = ActivityType.valueOf(doc.getString(FIELD_TYPE) ?: "CONE"),
                        sessionId = doc.getLong(FIELD_SESSION_ID),
                        smokerId = doc.getLong(FIELD_SMOKER_ID) ?: 0,
                        // smokerName not in ActivityLog
                        consumerId = doc.getLong(FIELD_CONSUMER_ID) ?: 0,
                        payerStashOwnerId = doc.getString(FIELD_PAYER_STASH_OWNER_ID),
                        gramsAtLog = doc.getDouble(FIELD_GRAMS_AT_LOG) ?: 0.0,
                        pricePerGramAtLog = doc.getDouble(FIELD_PRICE_PER_GRAM_AT_LOG) ?: 0.0,
                        customActivityId = doc.getString(FIELD_CUSTOM_ACTIVITY_ID),
                        customActivityName = doc.getString(FIELD_CUSTOM_ACTIVITY_NAME),
                        sessionStartTime = doc.getLong(FIELD_SESSION_START_TIME),
                        bowlQuantity = doc.getLong(FIELD_BOWL_QUANTITY)?.toInt() ?: 1
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing activity document: ${doc.id}", e)
                    null
                }
            }
            
            lastActivityDocument = if (snapshot.documents.isNotEmpty()) {
                snapshot.documents.last()
            } else {
                null
            }
            
            val hasMore = snapshot.documents.size >= PAGE_SIZE
            
            Log.d(TAG, "✅ Downloaded ${activities.size} activities, hasMore: $hasMore")
            Result.success(Pair(activities, hasMore))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to download activities", e)
            Result.failure(e)
        }
    }
    
    /**
     * Upload a session summary to Firestore
     */
    suspend fun uploadSessionSummary(userId: String, session: SessionSummary): Result<Unit> {
        return try {
            Log.d(TAG, "📤 Uploading session: ${session.id}")
            
            val sessionData = hashMapOf(
                FIELD_SESSION_ID_VALUE to session.id,
                FIELD_SMOKER_NAMES to session.smokerNames,
                FIELD_CONES_PER_SMOKER to session.conesPerSmoker,
                FIELD_TOTAL_CONES to session.totalCones,
                FIELD_ROUNDS to session.rounds,
                FIELD_SESSION_LENGTH to session.sessionLength,
                FIELD_LONGEST_INTERVAL to session.longestInterval,
                FIELD_SHORTEST_INTERVAL to session.shortestInterval,
                FIELD_SESSION_TIMESTAMP to session.timestamp,
                FIELD_LIVE_SYNC_ENABLED to session.liveSyncEnabled,
                FIELD_SHARE_CODE to session.shareCode,
                FIELD_ROOM_NAME to session.roomName,
                FIELD_ACTIVITY_BREAKDOWN to session.activityBreakdown,
                FIELD_IS_ACTIVE to session.isActive,
                FIELD_LAST_SYNC to Date()
            )
            
            firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SESSION_SUMMARIES)
                .document(session.id.toString())
                .set(sessionData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "✅ Session uploaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload session", e)
            Result.failure(e)
        }
    }
    
    /**
     * Download session summaries with pagination
     */
    suspend fun downloadSessionSummaries(
        userId: String,
        startAfter: DocumentSnapshot? = null
    ): Result<Pair<List<SessionSummary>, Boolean>> {
        return try {
            Log.d(TAG, "📥 Downloading sessions page for user: $userId")
            
            var query = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SESSION_SUMMARIES)
                .orderBy(FIELD_SESSION_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(PAGE_SIZE.toLong())
            
            if (startAfter != null) {
                query = query.startAfter(startAfter)
            }
            
            val snapshot = query.get().await()
            
            val sessions = snapshot.documents.mapNotNull { doc ->
                try {
                    SessionSummary(
                        id = doc.getLong(FIELD_SESSION_ID_VALUE) ?: 0,
                        smokerNames = (doc.get(FIELD_SMOKER_NAMES) as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
                        conesPerSmoker = (doc.get(FIELD_CONES_PER_SMOKER) as? List<*>)?.mapNotNull { 
                            when(it) {
                                is Long -> it.toInt()
                                is Double -> it.toInt()
                                else -> null
                            }
                        } ?: emptyList(),
                        totalCones = doc.getLong(FIELD_TOTAL_CONES)?.toInt() ?: 0,
                        rounds = doc.getLong(FIELD_ROUNDS)?.toInt() ?: 0,
                        sessionLength = doc.getLong(FIELD_SESSION_LENGTH) ?: 0,
                        longestInterval = doc.getLong(FIELD_LONGEST_INTERVAL) ?: 0,
                        shortestInterval = doc.getLong(FIELD_SHORTEST_INTERVAL) ?: 0,
                        timestamp = doc.getLong(FIELD_SESSION_TIMESTAMP) ?: 0,
                        liveSyncEnabled = doc.getBoolean(FIELD_LIVE_SYNC_ENABLED) ?: false,
                        shareCode = doc.getString(FIELD_SHARE_CODE),
                        roomName = doc.getString(FIELD_ROOM_NAME),
                        activityBreakdown = doc.getString(FIELD_ACTIVITY_BREAKDOWN),
                        // Always set isActive to false when downloading from cloud
                        // Sessions can't be active after app reinstall
                        isActive = false
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing session document: ${doc.id}", e)
                    null
                }
            }
            
            lastSessionDocument = if (snapshot.documents.isNotEmpty()) {
                snapshot.documents.last()
            } else {
                null
            }
            
            val hasMore = snapshot.documents.size >= PAGE_SIZE
            
            Log.d(TAG, "✅ Downloaded ${sessions.size} sessions, hasMore: $hasMore")
            Result.success(Pair(sessions, hasMore))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to download sessions", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get next page of activities
     */
    suspend fun getNextActivityPage(userId: String): Result<Pair<List<ActivityLog>, Boolean>> {
        return downloadActivities(userId, lastActivityDocument)
    }
    
    /**
     * Get next page of sessions
     */
    suspend fun getNextSessionPage(userId: String): Result<Pair<List<SessionSummary>, Boolean>> {
        return downloadSessionSummaries(userId, lastSessionDocument)
    }
    
    /**
     * Batch upload activities (for initial sync or background sync)
     */
    suspend fun batchUploadActivities(userId: String, activities: List<ActivityLog>): Result<Unit> {
        return try {
            Log.d(TAG, "📤 Batch uploading ${activities.size} activities")
            
            val batch = firestore.batch()
            
            activities.forEach { activity ->
                val ref = firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(ACTIVITY_HISTORY)
                    .document(activity.timestamp.toString())
                
                val data = hashMapOf(
                    FIELD_ID to activity.id,  // FIX: Store the activity ID for deletion
                    FIELD_TIMESTAMP to activity.timestamp,
                    FIELD_TYPE to activity.type.name,
                    FIELD_SESSION_ID to activity.sessionId,
                    FIELD_SMOKER_ID to activity.smokerId,
                    // smokerName not stored - will be resolved from smokerId
                    FIELD_CONSUMER_ID to activity.consumerId,
                    FIELD_PAYER_STASH_OWNER_ID to activity.payerStashOwnerId,
                    FIELD_GRAMS_AT_LOG to activity.gramsAtLog,
                    FIELD_PRICE_PER_GRAM_AT_LOG to activity.pricePerGramAtLog,
                    FIELD_CUSTOM_ACTIVITY_ID to activity.customActivityId,
                    FIELD_CUSTOM_ACTIVITY_NAME to activity.customActivityName,
                    FIELD_SESSION_START_TIME to activity.sessionStartTime,
                    FIELD_BOWL_QUANTITY to activity.bowlQuantity,
                    FIELD_LAST_SYNC to Date()
                )
                
                batch.set(ref, data, SetOptions.merge())
            }
            
            batch.commit().await()
            
            Log.d(TAG, "✅ Batch upload completed")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Batch upload failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Check if cloud sync is needed based on last sync time
     */
    suspend fun needsSync(userId: String, lastLocalActivityTime: Long): Boolean {
        return try {
            val lastCloudActivity = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(ACTIVITY_HISTORY)
                .orderBy(FIELD_TIMESTAMP, Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()
            
            if (lastCloudActivity.documents.isEmpty()) {
                // No cloud data, needs sync if we have local data
                return lastLocalActivityTime > 0
            }
            
            val cloudTimestamp = lastCloudActivity.documents.first().getLong(FIELD_TIMESTAMP) ?: 0
            
            // Needs sync if local is newer than cloud
            lastLocalActivityTime > cloudTimestamp
        } catch (e: Exception) {
            Log.e(TAG, "Error checking sync status", e)
            true // Assume sync needed on error
        }
    }
    
    /**
     * Delete an activity from cloud
     */
    suspend fun deleteActivity(userId: String, activityId: Long): Boolean {
        return try {
            Log.d(TAG, "🗑️ Deleting activity from cloud: $activityId")
            
            // Find and delete the document with matching ID
            val querySnapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(ACTIVITY_HISTORY)
                .whereEqualTo(FIELD_ID, activityId)
                .limit(1)
                .get()
                .await()
                
            if (querySnapshot.documents.isNotEmpty()) {
                val docId = querySnapshot.documents.first().id
                firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(ACTIVITY_HISTORY)
                    .document(docId)
                    .delete()
                    .await()
                    
                Log.d(TAG, "✅ Activity deleted from cloud: $activityId")
                true
            } else {
                Log.d(TAG, "⚠️ Activity not found in cloud: $activityId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete activity from cloud", e)
            false
        }
    }
    
    /**
     * Delete a session from cloud
     */
    suspend fun deleteSessionSummary(userId: String, sessionId: Long): Boolean {
        return try {
            Log.d(TAG, "🗑️ Deleting session from cloud: $sessionId")
            
            // Find and delete the document with matching ID
            val querySnapshot = firestore.collection(USERS_COLLECTION)
                .document(userId)
                .collection(SESSION_SUMMARIES)
                .whereEqualTo(FIELD_SESSION_ID_VALUE, sessionId)
                .limit(1)
                .get()
                .await()
                
            if (querySnapshot.documents.isNotEmpty()) {
                val docId = querySnapshot.documents.first().id
                firestore.collection(USERS_COLLECTION)
                    .document(userId)
                    .collection(SESSION_SUMMARIES)
                    .document(docId)
                    .delete()
                    .await()
                    
                Log.d(TAG, "✅ Session deleted from cloud: $sessionId")
                true
            } else {
                Log.d(TAG, "⚠️ Session not found in cloud: $sessionId")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to delete session from cloud", e)
            false
        }
    }
}
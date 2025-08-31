package com.sam.cloudcounter

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class StatsManager(
    private val context: Context,
    private val repository: ActivityRepository,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "StatsManager"
        private const val PREFS_NAME = "sam_stats_cache"
        private const val CACHE_KEY = "stats_cache"
        private const val SAM_UID = "diY4ATkGQYhYndv2lQY4rZAUKGl2"
        private const val STATS_ADJUSTMENTS_PATH = "stats_adjustments/sam_stats"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private var adjustmentsListener: ListenerRegistration? = null
    private var currentAdjustments = StatsAdjustments()

    /**
     * Get today's date string in device's local timezone
     */
    private fun getTodayDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date())
    }

    /**
     * Check if a timestamp is from today (device's local timezone)
     */
    private fun isToday(timestamp: Long): Boolean {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sdf.timeZone = TimeZone.getDefault()
        return sdf.format(Date(timestamp)) == getTodayDate()
    }

    /**
     * Get the start of today in milliseconds (device's local timezone)
     */
    private fun getTodayStartMillis(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * Load cached stats from SharedPreferences
     */
    private fun loadCache(): StatsCache? {
        val json = prefs.getString(CACHE_KEY, null) ?: return null
        return try {
            val parts = json.split("|")
            if (parts.size != 7) return null

            StatsCache(
                todayCones = parts[0].toInt(),
                todayJoints = parts[1].toInt(),
                allTimeCones = parts[2].toInt(),
                allTimeJoints = parts[3].toInt(),
                lastSyncTimestamp = parts[4].toLong(),
                todayDate = parts[5],
                cacheVersion = parts[6].toInt()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cache", e)
            null
        }
    }

    /**
     * Save stats to cache
     */
    private fun saveCache(cache: StatsCache) {
        val json = "${cache.todayCones}|${cache.todayJoints}|${cache.allTimeCones}|" +
                "${cache.allTimeJoints}|${cache.lastSyncTimestamp}|${cache.todayDate}|${cache.cacheVersion}"
        prefs.edit().putString(CACHE_KEY, json).apply()
    }

    /**
     * Clear cache (useful when date changes)
     */
    private fun clearTodayCache(): StatsCache {
        val cache = loadCache() ?: StatsCache()
        return cache.copy(
            todayCones = 0,
            todayJoints = 0,
            todayDate = getTodayDate()
        )
    }

    /**
     * Fetch activities from both local and Firebase
     */
    private suspend fun fetchActivities(sinceTimestamp: Long = 0): Pair<List<SessionActivity>, List<ActivityLog>> {
        return withContext(Dispatchers.IO) {
            val cloudActivities = mutableListOf<SessionActivity>()
            val localActivities = mutableListOf<ActivityLog>()

            // 1. Fetch from local database
            try {
                // Get Sam's local smoker IDs
                val samSmokers = repository.getAllSmokersList().filter { smoker ->
                    smoker.cloudUserId == SAM_UID ||
                            (smoker.isOwner && smoker.name.contains("Sam", ignoreCase = true))
                }

                if (samSmokers.isNotEmpty()) {
                    val smokerIds = samSmokers.map { it.smokerId }
                    val logs = repository.getLogsForSmokersInTimeRange(
                        smokerIds,
                        sinceTimestamp,
                        System.currentTimeMillis()
                    )
                    localActivities.addAll(logs)
                    Log.d(TAG, "Found ${logs.size} local activities since $sinceTimestamp")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching local activities", e)
            }

            // 2. Fetch from Firebase rooms where Sam participated
            try {
                val roomsSnapshot = firestore.collection("rooms")
                    .whereArrayContains("participants", SAM_UID)
                    .get()
                    .await()

                for (doc in roomsSnapshot.documents) {
                    val room = doc.toObject(RoomData::class.java) ?: continue
                    val activities = room.safeActivities().filter { activity ->
                        activity.timestamp > sinceTimestamp &&
                                (activity.smokerId == SAM_UID || activity.smokerId.startsWith("local_"))
                    }
                    cloudActivities.addAll(activities)
                }
                Log.d(TAG, "Found ${cloudActivities.size} cloud activities since $sinceTimestamp")
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching cloud activities", e)
            }

            Pair(cloudActivities, localActivities)
        }
    }

    /**
     * Deduplicate activities using composite key
     */
    private fun deduplicateActivities(
        cloudActivities: List<SessionActivity>,
        localActivities: List<ActivityLog>
    ): List<ActivityLog> {
        val seen = mutableSetOf<String>()
        val result = mutableListOf<ActivityLog>()

        // Add local activities first
        localActivities.forEach { activity ->
            val key = "${activity.timestamp}-${activity.type}-${activity.smokerId}"
            if (seen.add(key)) {
                result.add(activity)
            }
        }

        // Add cloud activities that aren't duplicates
        cloudActivities.forEach { activity ->
            val type = try {
                ActivityType.valueOf(activity.type.uppercase())
            } catch (e: Exception) {
                null
            } ?: return@forEach

            val key = "${activity.timestamp}-${type}-${activity.smokerId}"
            if (seen.add(key)) {
                // Create ActivityLog from SessionActivity
                result.add(
                    ActivityLog(
                        id = 0,
                        smokerId = 0, // Will be resolved later if needed
                        type = type,
                        timestamp = activity.timestamp
                    )
                )
            }
        }

        return result.sortedBy { it.timestamp }
    }

    /**
     * Calculate stats from activities
     */
    private fun calculateStats(activities: List<ActivityLog>): SamStats {
        val todayStart = getTodayStartMillis()

        val todayActivities = activities.filter { it.timestamp >= todayStart }
        val todayCones = todayActivities.count { it.type == ActivityType.CONE }
        val todayJoints = todayActivities.count { it.type == ActivityType.JOINT }

        val allTimeCones = activities.count { it.type == ActivityType.CONE }
        val allTimeJoints = activities.count { it.type == ActivityType.JOINT }

        return SamStats(
            todayCones = todayCones,
            todayJoints = todayJoints,
            allTimeCones = allTimeCones,
            allTimeJoints = allTimeJoints
        )
    }

    /**
     * Get stats with caching and real-time updates
     */
    suspend fun getStats(): SamStats {
        return withContext(Dispatchers.IO) {
            // Load cache
            var cache = loadCache()

            // Check if cache date changed (new day)
            if (cache != null && cache.todayDate != getTodayDate()) {
                cache = clearTodayCache()
                saveCache(cache)
            }

            // If no cache or stale, fetch everything
            if (cache == null) {
                val (cloudActivities, localActivities) = fetchActivities(0)
                val allActivities = deduplicateActivities(cloudActivities, localActivities)
                val stats = calculateStats(allActivities)

                // Save to cache
                val newCache = StatsCache(
                    todayCones = stats.todayCones,
                    todayJoints = stats.todayJoints,
                    allTimeCones = stats.allTimeCones,
                    allTimeJoints = stats.allTimeJoints,
                    lastSyncTimestamp = System.currentTimeMillis(),
                    todayDate = getTodayDate()
                )
                saveCache(newCache)

                return@withContext applyAdjustments(stats)
            }

            // Fetch only new activities since last sync
            val (cloudActivities, localActivities) = fetchActivities(cache.lastSyncTimestamp)

            if (cloudActivities.isNotEmpty() || localActivities.isNotEmpty()) {
                val newActivities = deduplicateActivities(cloudActivities, localActivities)

                // Update cache with new activities
                val todayStart = getTodayStartMillis()
                val newTodayActivities = newActivities.filter { it.timestamp >= todayStart }
                val newTodayCones = newTodayActivities.count { it.type == ActivityType.CONE }
                val newTodayJoints = newTodayActivities.count { it.type == ActivityType.JOINT }
                val newAllTimeCones = newActivities.count { it.type == ActivityType.CONE }
                val newAllTimeJoints = newActivities.count { it.type == ActivityType.JOINT }

                val updatedCache = cache.copy(
                    todayCones = cache.todayCones + newTodayCones,
                    todayJoints = cache.todayJoints + newTodayJoints,
                    allTimeCones = cache.allTimeCones + newAllTimeCones,
                    allTimeJoints = cache.allTimeJoints + newAllTimeJoints,
                    lastSyncTimestamp = System.currentTimeMillis()
                )
                saveCache(updatedCache)
                cache = updatedCache
            }

            // Return cached stats with adjustments
            val stats = SamStats(
                todayCones = cache.todayCones,
                todayJoints = cache.todayJoints,
                allTimeCones = cache.allTimeCones,
                allTimeJoints = cache.allTimeJoints
            )

            applyAdjustments(stats)
        }
    }

    /**
     * Apply Firebase adjustments to stats
     */
    private fun applyAdjustments(stats: SamStats): SamStats {
        return stats.copy(
            todayCones = stats.todayCones + currentAdjustments.todayConesAdjustment,
            todayJoints = stats.todayJoints + currentAdjustments.todayJointsAdjustment,
            allTimeCones = stats.allTimeCones + currentAdjustments.allTimeConesAdjustment,
            allTimeJoints = stats.allTimeJoints + currentAdjustments.allTimeJointsAdjustment
        )
    }

    /**
     * Start listening to Firebase adjustments
     */
    fun startAdjustmentsListener(onUpdate: (StatsAdjustments) -> Unit) {
        adjustmentsListener?.remove()

        adjustmentsListener = firestore.document(STATS_ADJUSTMENTS_PATH)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to adjustments", error)
                    return@addSnapshotListener
                }

                currentAdjustments = snapshot?.toObject(StatsAdjustments::class.java)
                    ?: StatsAdjustments()
                onUpdate(currentAdjustments)
            }
    }

    /**
     * Save adjustments to Firebase (admin only)
     */
    suspend fun saveAdjustments(adjustments: StatsAdjustments): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                val currentUser = FirebaseAuth.getInstance().currentUser
                if (currentUser?.uid != SAM_UID) {
                    return@withContext Result.failure(Exception("Unauthorized"))
                }

                firestore.document(STATS_ADJUSTMENTS_PATH)
                    .set(adjustments)
                    .await()

                currentAdjustments = adjustments
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save adjustments", e)
                Result.failure(e)
            }
        }
    }

    /**
     * Clear all stats cache
     */
    fun clearCache() {
        prefs.edit().clear().apply()
    }

    /**
     * Stop listeners
     */
    fun cleanup() {
        adjustmentsListener?.remove()
    }
}
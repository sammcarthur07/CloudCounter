package com.sam.cloudcounter

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID // FIX: Added missing import for UUID

/**
 * Room-centric session management - all session data stored in room document
 * Now includes auto-add state management and enhanced return from away logic
 */
class SessionSyncService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val repository: ActivityRepository? = null
) {
    companion object {
        private const val TAG = "SessionSyncService"
        private const val COLLECTION_ROOMS = "rooms"
    }

    private val roomsCollection = firestore.collection(COLLECTION_ROOMS)
    private var roomListener: ListenerRegistration? = null

    private val roomListeners = mutableMapOf<String, RoomListener>()

    // NEW & REFACTORED: This is the primary function to safely add a local smoker to a room.
    // It uses a transaction to prevent race conditions between different devices.
    suspend fun findOrCreateSharedSmoker(
        shareCode: String,
        addedByUserId: String,
        smokerName: String
    ): Result<Smoker> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 Finding or creating shared smoker '$smokerName' in room '$shareCode'")
        try {
            val roomRef = roomsCollection.document(shareCode)

            val smokerToReturn = firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found during transaction")

                val currentSharedSmokers = room.safeSharedSmokers()

                // Find an existing local user by name
                val existingEntry = currentSharedSmokers.entries.find { (_, data) ->
                    val isLocal = data["isLocal"] as? Boolean ?: false
                    val name = data["name"] as? String
                    isLocal && name == smokerName
                }

                if (existingEntry != null) {
                    Log.d(TAG, "🔄 Smoker '$smokerName' already exists in room. Using existing.")

                    // SIMPLIFIED: The key of the entry is the smokerId (e.g., "local_UUID")
                    val existingUid = existingEntry.key.removePrefix("local_")

                    Smoker(uid = existingUid, name = smokerName, isCloudSmoker = false)
                } else {
                    Log.d(TAG, "🔄 Smoker '$smokerName' not found. Creating new entry in room.")
                    val newSmoker = Smoker(name = smokerName) // Generates a new UID
                    val smokerRoomId = "local_${newSmoker.uid}"
                    val now = System.currentTimeMillis()

                    val newSmokerData = mapOf(
                        "smokerId" to smokerRoomId,
                        "name" to newSmoker.name,
                        "isLocal" to true,
                        "addedBy" to addedByUserId,
                        "addedAt" to now,
                        "order" to now
                    )

                    // Use FieldValue to merge the new smoker into the map atomically
                    transaction.update(roomRef, "sharedSmokers.$smokerRoomId", newSmokerData)
                    transaction.update(roomRef, "updatedAt", now)

                    newSmoker
                }
            }.await()

            Result.success(smokerToReturn)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to find or create shared smoker: ${e.message}", e)
            Result.failure(e)
        }
    }
    
    /**
     * Remove a specific activity from a room (for undo functionality)
     */
    suspend fun removeActivityFromRoom(
        shareCode: String,
        smokerUid: String,
        activityType: ActivityType,
        timestamp: Long
    ): Result<Unit> {
        return try {
            val roomRef = firestore.collection("rooms").document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                if (!roomSnapshot.exists()) {
                    throw Exception("Room not found")
                }

                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Invalid room data")

                // Find and remove the specific activity
                val activities = room.safeActivities().toMutableList()
                val activityToRemove = activities.find { activity ->
                    activity.smokerId == smokerUid &&
                            activity.type.equals(activityType.name, ignoreCase = true) &&
                            activity.timestamp == timestamp
                }

                if (activityToRemove != null) {
                    activities.remove(activityToRemove)

                    // IMPORTANT: Recalculate stats after removing the activity
                    val updatedStats = calculateSessionStats(activities, room.startTime)

                    // Update room with remaining activities AND updated stats
                    val updatedRoom = room.copy(
                        activities = activities,
                        currentStats = updatedStats,  // This was missing!
                        updatedAt = System.currentTimeMillis()  // Changed from lastUpdated to updatedAt
                    )

                    transaction.set(roomRef, updatedRoom)
                    Log.d(TAG, "Removed activity: $activityType for $smokerUid at $timestamp")
                } else {
                    Log.w(TAG, "Activity not found for removal: $activityType for $smokerUid at $timestamp")
                }
            }.await()

            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove activity from room", e)
            Result.failure(e)
        }
    }

    /**
     * Generates a unique 6-character shareCode by ensuring no document exists with that ID.
     */
    private suspend fun generateShareCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var code: String
        do {
            code = (1..6).map { chars.random() }.joinToString("")
        } while (roomsCollection.document(code).get().await().exists())
        return code
    }

    private data class RoomListener(
        val listener: ListenerRegistration,
        val onChange: (RoomData) -> Unit
    )

    /**
     * Add user to the joinedUsers list after successful password verification
     */
    suspend fun addUserToJoinedList(shareCode: String, userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔐 addUserToJoinedList() - shareCode=$shareCode, userId=$userId")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            roomRef.update(
                "joinedUsers", FieldValue.arrayUnion(userId),
                "updatedAt", System.currentTimeMillis()
            ).await()
            Log.d(TAG, "🔐 User added to joined list successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🔐 Failed to add user to joined list", e)
            Result.failure(e)
        }
    }

    /**
     * Creates a new room document (ID = shareCode) and returns the full RoomData.
     */
    suspend fun createRoom(creatorId: String, roomName: String): Result<RoomData> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🏠 createRoom() START - creatorId='$creatorId', roomName='$roomName'")
        return@withContext try {
            val shareCode = generateShareCode()
            val now = System.currentTimeMillis()
            val room = RoomData(
                owner = creatorId,
                name = roomName,
                shareCode = shareCode,
                participants = listOf(creatorId),
                activeParticipants = listOf(creatorId),
                active = true,
                createdAt = now,
                updatedAt = now,
                startTime = now,
                lastActivityTime = now,
                activities = emptyList(),
                currentStats = SessionStats(),
                roundsCounter = 0,
                autoAddState = AutoAddState()
            )
            Log.d(TAG, "🏠 createRoom() Writing room: $room")
            roomsCollection.document(shareCode).set(room).await()
            Log.d(TAG, "🏠 createRoom() SUCCESS")
            Result.success(room)
        } catch (e: Exception) {
            Log.e(TAG, "🏠 createRoom() FAILED", e)
            Result.failure(e)
        }
    }

    suspend fun getRoomData(shareCode: String): RoomData? = withContext(Dispatchers.IO) {
        try {
            val doc = firestore.collection("rooms")
                .document(shareCode)
                .get()
                .await()
            doc.toObject(RoomData::class.java)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting room data: ${e.message}")
            null
        }
    }

    suspend fun forceRefreshRoom(shareCode: String): Result<Unit> {
        return try {
            // Trigger a manual refresh of the room data
            val snapshot = firestore.collection("rooms")
                .document(shareCode)
                .get()
                .await()

            val roomData = snapshot.toObject(RoomData::class.java)
            roomData?.let {
                // Trigger the room listener update
                roomListeners[shareCode]?.onChange?.invoke(it)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }


    /**
     * Pause a specific smoker (by smoker ID, not user ID)
     */
    suspend fun pauseSmoker(shareCode: String, smokerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "⏸️ pauseSmoker() - smokerId=$smokerId, shareCode=$shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            if (!roomRef.get().await().exists()) {
                Log.e(TAG, "⏸️ pauseSmoker() failed: Room not found")
                return@withContext Result.failure(Exception("Room not found"))
            }
            roomRef.update(
                "pausedSmokers", FieldValue.arrayUnion(smokerId),
                "updatedAt", System.currentTimeMillis()
            ).await()
            Log.d(TAG, "⏸️ pauseSmoker() SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "⏸️ pauseSmoker() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Resume a specific smoker (by smoker ID)
     */
    suspend fun resumeSmoker(shareCode: String, smokerId: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "▶️ resumeSmoker() - smokerId=$smokerId, shareCode=$shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            if (!roomRef.get().await().exists()) {
                Log.e(TAG, "▶️ resumeSmoker() failed: Room not found")
                return@withContext Result.failure(Exception("Room not found"))
            }
            roomRef.update(
                "pausedSmokers", FieldValue.arrayRemove(smokerId),
                "updatedAt", System.currentTimeMillis()
            ).await()
            Log.d(TAG, "▶️ resumeSmoker() SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "▶️ resumeSmoker() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Mark user as away (ended session but room still exists)
     */
    suspend fun markUserAway(userId: String, shareCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🏃 markUserAway() - userId=$userId, shareCode=$shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            if (!roomRef.get().await().exists()) {
                Log.e(TAG, "🏃 markUserAway() failed: Room not found")
                return@withContext Result.failure(Exception("Room not found"))
            }
            roomRef.update(
                "activeParticipants", FieldValue.arrayRemove(userId),
                "awayParticipants", FieldValue.arrayUnion(userId),
                "updatedAt", System.currentTimeMillis()
            ).await()
            Log.d(TAG, "🏃 markUserAway() SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🏃 markUserAway() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Enhanced return user from away status (when they rejoin session)
     */
    suspend fun returnFromAway(userId: String, shareCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 returnFromAway() - userId=$userId, shareCode=$shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            val roomSnapshot = roomRef.get().await()

            if (!roomSnapshot.exists()) {
                Log.e(TAG, "🔄 returnFromAway() failed: Room not found")
                return@withContext Result.failure(Exception("Room not found"))
            }

            val room = roomSnapshot.toObject(RoomData::class.java)
            if (room == null) {
                Log.e(TAG, "🔄 returnFromAway() failed: Invalid room data")
                return@withContext Result.failure(Exception("Invalid room data"))
            }

            val updatedAwayParticipants: List<String> = room.safeAwayParticipants().filterNot { it == userId }
            val updatedActiveParticipants: List<String> = room.activeParticipants.toMutableList().apply {
                if (!contains(userId)) add(userId)
            }

            roomRef.update(
                "awayParticipants", updatedAwayParticipants,
                "activeParticipants", updatedActiveParticipants,
                "updatedAt", System.currentTimeMillis()
            ).await()

            Log.d(TAG, "🔄 returnFromAway() SUCCESS - User $userId returned from away status")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🔄 returnFromAway() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Returns only rooms that currently have at least one active participant.
     * Sorted by creation time (most recent first)
     */
    suspend fun getActiveRooms(): Result<List<RoomData>> = withContext(Dispatchers.IO) {
        return@withContext try {
            val currentUserId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            val snapshot = roomsCollection.get().await()
            val rooms = mutableListOf<RoomData>()

            snapshot.documents.forEach { doc ->
                try {
                    val room = doc.toObject(RoomData::class.java)
                    if (room != null) {
                        val hasActiveParticipants = room.activeParticipants.isNotEmpty()
                        val userIsParticipant = currentUserId != null &&
                                (room.participants.contains(currentUserId) ||
                                        room.activeParticipants.contains(currentUserId) ||
                                        room.safePausedSmokers().contains(currentUserId) ||
                                        room.safeAwayParticipants().contains(currentUserId))

                        if (hasActiveParticipants || userIsParticipant) {
                            rooms.add(room)
                            Log.d(TAG, "✅ Compatible room: ${room.name} (${room.shareCode})")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Skipping incompatible room ${doc.id}: ${e.message}")
                }
            }

            // Sort rooms by createdAt timestamp, most recent first
            val sortedRooms = rooms.sortedByDescending { it.createdAt }

            Log.d(TAG, "Found ${sortedRooms.size} compatible rooms (sorted by creation time)")
            Result.success(sortedRooms)
        } catch (e: Exception) {
            Log.e(TAG, "getActiveRooms failed", e)
            Result.failure(e)
        }
    }

    /**
     * Joins an existing room by shareCode (adds to participants & activeParticipants).
     */
    suspend fun joinRoom(userId: String, shareCode: String): Result<RoomData> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🏠 joinRoom() - userId=$userId, shareCode=$shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            val snap = roomRef.get().await()
            if (!snap.exists()) {
                Log.e(TAG, "🏠 joinRoom() failed: Room not found")
                return@withContext Result.failure(Exception("Room not found"))
            }

            val room = snap.toObject(RoomData::class.java)
            if (room == null) {
                Log.e(TAG, "🏠 joinRoom() failed: Invalid room data")
                return@withContext Result.failure(Exception("Invalid room data"))
            }

            val wasAway = room.safeAwayParticipants().contains(userId)

            if (wasAway) {
                Log.d(TAG, "🏠 joinRoom() - User was away, handling return from away status")
                roomRef.update(
                    "participants", FieldValue.arrayUnion(userId),
                    "activeParticipants", FieldValue.arrayUnion(userId),
                    "awayParticipants", FieldValue.arrayRemove(userId),
                    "updatedAt", System.currentTimeMillis()
                ).await()
            } else {
                roomRef.update(
                    "participants", FieldValue.arrayUnion(userId),
                    "activeParticipants", FieldValue.arrayUnion(userId),
                    "updatedAt", System.currentTimeMillis()
                ).await()
            }

            val updated = roomRef.get().await().toObject(RoomData::class.java)
            if (updated != null) {
                Log.d(TAG, "🏠 joinRoom() SUCCESS${if (wasAway) " (returned from away)" else ""}")
                Result.success(updated)
            } else {
                Result.failure(Exception("Failed to read room data after joining"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "🏠 joinRoom() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Add activity directly to room document and update auto-add timers
     */
    suspend fun addActivityToRoom(
        shareCode: String,
        smokerUid: String,
        smokerName: String,
        activityType: ActivityType,
        timestamp: Long, // <<< ADD THIS PARAMETER
        deviceId: String = ""
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🎯 addActivityToRoom() - $activityType for $smokerName (UID: $smokerUid) in room $shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            // val now = System.currentTimeMillis() // <<< REMOVE THIS LINE

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                val newActivity = SessionActivity(
                    smokerId = smokerUid,
                    smokerName = smokerName,
                    type = activityType.name,
                    timestamp = timestamp, // <<< USE THE PARAMETER HERE
                    deviceId = deviceId
                )

                val currentActivities = room.safeActivities()
                val updatedActivities = currentActivities + newActivity

                // ... (rest of the function is the same)
                val activeParticipantCount = updatedActivities
                    .map { it.smokerId }
                    .distinct()
                    .count()

                val updatedStats = calculateSessionStatsWithActiveCount(
                    updatedActivities,
                    room.startTime,
                    activeParticipantCount
                )

                val updatedAutoAddState = updateAutoAddTimers(
                    room.safeAutoAddState(),
                    updatedActivities,
                    activityType,
                    timestamp // <<< USE THE PARAMETER HERE
                )

                val updatedRoom = room.copy(
                    activities = updatedActivities,
                    currentStats = updatedStats,
                    lastActivityTime = timestamp, // <<< USE THE PARAMETER HERE
                    updatedAt = System.currentTimeMillis(), // Keep this as real time for sync purposes
                    autoAddState = updatedAutoAddState
                )

                transaction.set(roomRef, updatedRoom)

                Log.d(TAG, "🎯 Added activity: $activityType for $smokerName. Total activities: ${updatedActivities.size}")
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🎯 addActivityToRoom() FAILED", e)
            Result.failure(e)
        }
    }

    private fun calculateSessionStatsWithActiveCount(
        activities: List<SessionActivity>,
        sessionStartTime: Long,
        activeParticipantCount: Int
    ): SessionStats {
        val now = System.currentTimeMillis()

        val coneActivities = activities.filter { it.type == "CONE" }.sortedBy { it.timestamp }
        val jointActivities = activities.filter { it.type == "JOINT" }.sortedBy { it.timestamp }
        val bowlActivities = activities.filter { it.type == "BOWL" }.sortedBy { it.timestamp }

        val gaps = coneActivities.zipWithNext { a, b -> b.timestamp - a.timestamp }

        val sinceLastConeMs = if (coneActivities.isEmpty()) {
            now - sessionStartTime
        } else {
            now - coneActivities.last().timestamp
        }

        val sinceLastJointMs = if (jointActivities.isEmpty()) {
            now - sessionStartTime
        } else {
            now - jointActivities.last().timestamp
        }

        val sinceLastBowlMs = if (bowlActivities.isEmpty()) {
            now - sessionStartTime
        } else {
            now - bowlActivities.last().timestamp
        }

        // Calculate cones since last bowl
        val lastBowl = bowlActivities.lastOrNull()
        val conesSinceLastBowl = if (lastBowl != null) {
            coneActivities.count { it.timestamp > lastBowl.timestamp }
        } else {
            coneActivities.size
        }

        // Get last cone smoker name
        val lastConeSmokerName = coneActivities.lastOrNull()?.smokerName

        val totalHits = activities.size

        val totalRounds = if (activeParticipantCount > 0 && totalHits > activeParticipantCount) {
            (totalHits - 1) / activeParticipantCount
        } else {
            0
        }

        val hitsInCurrentRound = if (activeParticipantCount > 0) {
            totalHits % activeParticipantCount
        } else {
            0
        }

        val perSmokerStats = activities.groupBy { it.smokerId }.mapValues { (smokerId, userActivities) ->
            val userCones = userActivities.filter { it.type == "CONE" }.sortedBy { it.timestamp }
            val userJoints = userActivities.filter { it.type == "JOINT" }.sortedBy { it.timestamp }
            val userBowls = userActivities.filter { it.type == "BOWL" }.sortedBy { it.timestamp }

            val userConeGaps = userCones.zipWithNext { a, b -> b.timestamp - a.timestamp }
            val userJointGaps = userJoints.zipWithNext { a, b -> b.timestamp - a.timestamp }
            val userBowlGaps = userBowls.zipWithNext { a, b -> b.timestamp - a.timestamp }

            PerSmokerData(
                smokerName = userActivities.first().smokerName,
                totalCones = userCones.size,
                totalJoints = userJoints.size,
                totalBowls = userBowls.size,
                avgGapMs = if (userConeGaps.isEmpty()) 0L else userConeGaps.average().toLong(),
                longestGapMs = userConeGaps.maxOrNull() ?: 0L,
                shortestGapMs = userConeGaps.minOrNull() ?: 0L,
                avgJointGapMs = if (userJointGaps.isEmpty()) 0L else userJointGaps.average().toLong(),
                longestJointGapMs = userJointGaps.maxOrNull() ?: 0L,
                shortestJointGapMs = userJointGaps.minOrNull() ?: 0L,
                avgBowlGapMs = if (userBowlGaps.isEmpty()) 0L else userBowlGaps.average().toLong(),
                longestBowlGapMs = userBowlGaps.maxOrNull() ?: 0L,
                shortestBowlGapMs = userBowlGaps.minOrNull() ?: 0L,
                lastActivityTime = userActivities.maxOfOrNull { it.timestamp } ?: 0L
            )
        }

        return SessionStats(
            totalCones = coneActivities.size,
            totalJoints = jointActivities.size,
            totalBowls = bowlActivities.size,
            longestGapMs = gaps.maxOrNull() ?: 0L,
            shortestGapMs = gaps.minOrNull() ?: 0L,
            sinceLastConeMs = sinceLastConeMs,
            sinceLastJointMs = sinceLastJointMs,
            sinceLastBowlMs = sinceLastBowlMs,
            perSmokerStats = perSmokerStats,
            lastCalculated = now,
            totalRounds = totalRounds,
            hitsInCurrentRound = hitsInCurrentRound,
            participantCount = activeParticipantCount,
            lastConeSmokerName = lastConeSmokerName,
            conesSinceLastBowl = conesSinceLastBowl
        )
    }

    /**
     * Update auto-add state for a specific activity type
     */
    suspend fun updateAutoAddState(
        shareCode: String,
        activityType: ActivityType,
        enabled: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🤖 updateAutoAddState() - $activityType enabled=$enabled in room $shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                val currentAutoState = room.safeAutoAddState()
                val now = System.currentTimeMillis()

                val updatedAutoState = when (activityType) {
                    ActivityType.CONE -> {
                        if (enabled) {
                            val activities = room.safeActivities()
                            val coneActivities = activities.filter { it.type == ActivityType.CONE.name }.sortedBy { it.timestamp }

                            if (coneActivities.size >= 2) {
                                val lastActivity = coneActivities.last()
                                val secondLastActivity = coneActivities[coneActivities.size - 2]
                                val originalGap = lastActivity.timestamp - secondLastActivity.timestamp
                                val nextTime = now + originalGap

                                Log.d(TAG, "🤖 Cone auto enabled - storing original gap: ${originalGap}ms")

                                currentAutoState.copy(
                                    coneAutoEnabled = true,
                                    coneNextAutoTime = nextTime,
                                    coneOriginalGap = originalGap,
                                    lastUpdated = now
                                )
                            } else {
                                Log.d(TAG, "🤖 Not enough cone activities for auto-add")
                                currentAutoState.copy(
                                    coneAutoEnabled = false,
                                    coneNextAutoTime = 0L,
                                    coneOriginalGap = 0L,
                                    lastUpdated = now
                                )
                            }
                        } else {
                            currentAutoState.copy(
                                coneAutoEnabled = false,
                                coneNextAutoTime = 0L,
                                coneOriginalGap = 0L,
                                lastUpdated = now
                            )
                        }
                    }
                    ActivityType.JOINT -> {
                        if (enabled) {
                            val activities = room.safeActivities()
                            val jointActivities = activities.filter { it.type == ActivityType.JOINT.name }.sortedBy { it.timestamp }

                            if (jointActivities.size >= 2) {
                                val lastActivity = jointActivities.last()
                                val secondLastActivity = jointActivities[jointActivities.size - 2]
                                val originalGap = lastActivity.timestamp - secondLastActivity.timestamp
                                val nextTime = now + originalGap

                                Log.d(TAG, "🤖 Joint auto enabled - storing original gap: ${originalGap}ms")

                                currentAutoState.copy(
                                    jointAutoEnabled = true,
                                    jointNextAutoTime = nextTime,
                                    jointOriginalGap = originalGap,
                                    lastUpdated = now
                                )
                            } else {
                                currentAutoState.copy(
                                    jointAutoEnabled = false,
                                    jointNextAutoTime = 0L,
                                    jointOriginalGap = 0L,
                                    lastUpdated = now
                                )
                            }
                        } else {
                            currentAutoState.copy(
                                jointAutoEnabled = false,
                                jointNextAutoTime = 0L,
                                jointOriginalGap = 0L,
                                lastUpdated = now
                            )
                        }
                    }
                    ActivityType.BOWL -> {
                        if (enabled) {
                            val activities = room.safeActivities()
                            val bowlActivities = activities.filter { it.type == ActivityType.BOWL.name }.sortedBy { it.timestamp }

                            if (bowlActivities.size >= 2) {
                                val lastActivity = bowlActivities.last()
                                val secondLastActivity = bowlActivities[bowlActivities.size - 2]
                                val originalGap = lastActivity.timestamp - secondLastActivity.timestamp
                                val nextTime = now + originalGap

                                Log.d(TAG, "🤖 Bowl auto enabled - storing original gap: ${originalGap}ms")

                                currentAutoState.copy(
                                    bowlAutoEnabled = true,
                                    bowlNextAutoTime = nextTime,
                                    bowlOriginalGap = originalGap,
                                    lastUpdated = now
                                )
                            } else {
                                currentAutoState.copy(
                                    bowlAutoEnabled = false,
                                    bowlNextAutoTime = 0L,
                                    bowlOriginalGap = 0L,
                                    lastUpdated = now
                                )
                            }
                        } else {
                            currentAutoState.copy(
                                bowlAutoEnabled = false,
                                bowlNextAutoTime = 0L,
                                bowlOriginalGap = 0L,
                                lastUpdated = now
                            )
                        }
                    }
                    else -> currentAutoState
                }

                val updatedRoom = room.copy(
                    autoAddState = updatedAutoState,
                    updatedAt = now
                )

                transaction.set(roomRef, updatedRoom)
            }.await()

            Log.d(TAG, "🤖 Updated auto-add state for $activityType")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🤖 updateAutoAddState() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Update auto-add timers after a new activity is added
     */
    private fun updateAutoAddTimers(
        currentAutoState: AutoAddState,
        updatedActivities: List<SessionActivity>,
        addedActivityType: ActivityType,
        now: Long
    ): AutoAddState {
        Log.d(TAG, "🤖 Updating auto-add timers after $addedActivityType was added")

        val newConeTime = if (currentAutoState.coneAutoEnabled && currentAutoState.coneOriginalGap > 0) {
            val nextTime = now + currentAutoState.coneOriginalGap
            Log.d(TAG, "🤖 Cone - Using stored original gap: ${currentAutoState.coneOriginalGap}ms")
            nextTime
        } else {
            currentAutoState.coneNextAutoTime
        }

        val newJointTime = if (currentAutoState.jointAutoEnabled && currentAutoState.jointOriginalGap > 0) {
            val nextTime = now + currentAutoState.jointOriginalGap
            Log.d(TAG, "🤖 Joint - Using stored original gap: ${currentAutoState.jointOriginalGap}ms")
            nextTime
        } else {
            currentAutoState.jointNextAutoTime
        }

        val newBowlTime = if (currentAutoState.bowlAutoEnabled && currentAutoState.bowlOriginalGap > 0) {
            val nextTime = now + currentAutoState.bowlOriginalGap
            Log.d(TAG, "🤖 Bowl - Using stored original gap: ${currentAutoState.bowlOriginalGap}ms")
            nextTime
        } else {
            currentAutoState.bowlNextAutoTime
        }

        return currentAutoState.copy(
            coneNextAutoTime = newConeTime,
            jointNextAutoTime = newJointTime,
            bowlNextAutoTime = newBowlTime,
            lastUpdated = now
        )
    }

    /**
     * Calculate session stats from activities list
     */
    private fun calculateSessionStats(activities: List<SessionActivity>, sessionStartTime: Long): SessionStats {
        val now = System.currentTimeMillis()

        val coneActivities = activities.filter { it.type == "CONE" }.sortedBy { it.timestamp }
        val jointActivities = activities.filter { it.type == "JOINT" }.sortedBy { it.timestamp }
        val bowlActivities = activities.filter { it.type == "BOWL" }.sortedBy { it.timestamp }

        val gaps = coneActivities.zipWithNext { a, b -> b.timestamp - a.timestamp }

        val sinceLastConeMs = if (coneActivities.isEmpty()) {
            now - sessionStartTime
        } else {
            now - coneActivities.last().timestamp
        }

        val sinceLastJointMs = if (jointActivities.isEmpty()) {
            now - sessionStartTime
        } else {
            now - jointActivities.last().timestamp
        }

        val sinceLastBowlMs = if (bowlActivities.isEmpty()) {
            now - sessionStartTime
        } else {
            now - bowlActivities.last().timestamp
        }

        // Calculate cones since last bowl
        val lastBowl = bowlActivities.lastOrNull()
        val conesSinceLastBowl = if (lastBowl != null) {
            coneActivities.count { it.timestamp > lastBowl.timestamp }
        } else {
            coneActivities.size
        }

        // Get last cone smoker name
        val lastConeSmokerName = coneActivities.lastOrNull()?.smokerName

        val uniqueParticipants = activities.map { it.smokerId }.distinct()
        val participantCount = uniqueParticipants.size
        val totalHits = activities.size

        val totalRounds = if (participantCount > 0 && totalHits > participantCount) {
            (totalHits - 1) / participantCount
        } else {
            0
        }

        val hitsInCurrentRound = if (participantCount > 0 && totalHits > 0) {
            if (totalRounds == 0) {
                totalHits
            } else {
                ((totalHits - 1) % participantCount) + 1
            }
        } else {
            0
        }

        val perSmokerStats = activities.groupBy { it.smokerId }.mapValues { (smokerId, userActivities) ->
            val userCones = userActivities.filter { it.type == "CONE" }.sortedBy { it.timestamp }
            val userJoints = userActivities.filter { it.type == "JOINT" }.sortedBy { it.timestamp }
            val userBowls = userActivities.filter { it.type == "BOWL" }.sortedBy { it.timestamp }

            val userConeGaps = userCones.zipWithNext { a, b -> b.timestamp - a.timestamp }
            val userJointGaps = userJoints.zipWithNext { a, b -> b.timestamp - a.timestamp }
            val userBowlGaps = userBowls.zipWithNext { a, b -> b.timestamp - a.timestamp }

            PerSmokerData(
                smokerName = userActivities.first().smokerName,
                totalCones = userCones.size,
                totalJoints = userJoints.size,
                totalBowls = userBowls.size,
                avgGapMs = if (userConeGaps.isEmpty()) 0L else userConeGaps.average().toLong(),
                longestGapMs = userConeGaps.maxOrNull() ?: 0L,
                shortestGapMs = userConeGaps.minOrNull() ?: 0L,
                avgJointGapMs = if (userJointGaps.isEmpty()) 0L else userJointGaps.average().toLong(),
                longestJointGapMs = userJointGaps.maxOrNull() ?: 0L,
                shortestJointGapMs = userJointGaps.minOrNull() ?: 0L,
                avgBowlGapMs = if (userBowlGaps.isEmpty()) 0L else userBowlGaps.average().toLong(),
                longestBowlGapMs = userBowlGaps.maxOrNull() ?: 0L,
                shortestBowlGapMs = userBowlGaps.minOrNull() ?: 0L,
                lastActivityTime = userActivities.maxOfOrNull { it.timestamp } ?: 0L
            )
        }

        Log.d(TAG, "📊 Stats calculation: activities=${activities.size}, participants=$participantCount, totalHits=$totalHits, totalRounds=$totalRounds, hitsInCurrentRound=$hitsInCurrentRound, conesSinceLastBowl=$conesSinceLastBowl")

        return SessionStats(
            totalCones = coneActivities.size,
            totalJoints = jointActivities.size,
            totalBowls = bowlActivities.size,
            longestGapMs = gaps.maxOrNull() ?: 0L,
            shortestGapMs = gaps.minOrNull() ?: 0L,
            sinceLastConeMs = sinceLastConeMs,
            sinceLastJointMs = sinceLastJointMs,
            sinceLastBowlMs = sinceLastBowlMs,
            perSmokerStats = perSmokerStats,
            lastCalculated = now,
            totalRounds = totalRounds,
            hitsInCurrentRound = hitsInCurrentRound,
            participantCount = participantCount,
            lastConeSmokerName = lastConeSmokerName,
            conesSinceLastBowl = conesSinceLastBowl
        )
    }
    /**
     * Update the rounds counter setting in the room
     */
    suspend fun updateRoundsCounterInRoom(
        shareCode: String,
        roundsCounter: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 updateRoundsCounterInRoom() - room=$shareCode, roundsCounter=$roundsCounter")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                val updatedRoom = room.copy(
                    roundsCounter = roundsCounter,
                    updatedAt = System.currentTimeMillis()
                )

                transaction.set(roomRef, updatedRoom)
            }.await()

            Log.d(TAG, "🔄 Updated rounds counter to $roundsCounter in room $shareCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🔄 updateRoundsCounterInRoom() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Update the rounds counter in the room
     */
    suspend fun updateRoundsInRoom(
        shareCode: String,
        totalRounds: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 updateRoundsInRoom() - shareCode=$shareCode, totalRounds=$totalRounds")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                val updatedStats = room.safeCurrentStats().copy(
                    totalRounds = totalRounds
                )

                val updatedRoom = room.copy(
                    currentStats = updatedStats,
                    updatedAt = System.currentTimeMillis()
                )

                transaction.set(roomRef, updatedRoom)
            }.await()

            Log.d(TAG, "🔄 Updated rounds to $totalRounds in room $shareCode")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🔄 updateRoundsInRoom() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Leaves a room completely
     */
    suspend fun leaveRoom(userId: String, shareCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🏠 leaveRoom() - userId=$userId, shareCode=$shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            val snap = roomRef.get().await()
            if (!snap.exists()) {
                Log.e(TAG, "🏠 leaveRoom() failed: Room not found")
                return@withContext Result.failure(Exception("Room not found"))
            }
            val room = snap.toObject(RoomData::class.java)
                ?: return@withContext Result.failure(Exception("Malformed room data"))
            val remaining = room.participants.filterNot { it == userId }
            val remainingActive = room.activeParticipants.filterNot { it == userId }

            if (remaining.isEmpty()) {
                Log.d(TAG, "🏠 leaveRoom() no participants left, deleting room")
                roomRef.delete().await()
            } else {
                roomRef.update(
                    "participants", remaining,
                    "activeParticipants", remainingActive,
                    "updatedAt", System.currentTimeMillis()
                ).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🏠 leaveRoom() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Marks you "in" the session
     */
    suspend fun markActive(userId: String, shareCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🏠 markActive() - userId=$userId, shareCode=$shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            if (!roomRef.get().await().exists()) {
                Log.e(TAG, "🏠 markActive() failed: Room not found")
                return@withContext Result.failure(Exception("Room not found"))
            }
            roomRef.update(
                "activeParticipants", FieldValue.arrayUnion(userId),
                "updatedAt", System.currentTimeMillis()
            ).await()
            Log.d(TAG, "🏠 markActive() SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🏠 markActive() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Marks you "away"
     */
    suspend fun markAway(userId: String, shareCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🏠 markAway() - userId=$userId, shareCode=$shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)
            if (!roomRef.get().await().exists()) {
                Log.e(TAG, "🏠 markAway() failed: Room not found")
                return@withContext Result.failure(Exception("Room not found"))
            }
            roomRef.update(
                "activeParticipants", FieldValue.arrayRemove(userId),
                "updatedAt", System.currentTimeMillis()
            ).await()
            Log.d(TAG, "🏠 markAway() SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🏠 markAway() FAILED", e)
            Result.failure(e)
        }
    }

    suspend fun updateSharedSmokerInRoom(
        shareCode: String,
        smokerUid: String,
        updatedName: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "📝 updateSharedSmokerInRoom() - Updating smoker $smokerUid to '$updatedName' in room $shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                // Update the smoker name in sharedSmokers
                val updatedSharedSmokers = room.safeSharedSmokers().toMutableMap()
                val smokerData = updatedSharedSmokers[smokerUid]?.toMutableMap()
                if (smokerData != null) {
                    smokerData["name"] = updatedName
                    updatedSharedSmokers[smokerUid] = smokerData
                    Log.d(TAG, "📝 Updated smoker in sharedSmokers map: $smokerUid")
                } else {
                    Log.w(TAG, "📝 Smoker not found in sharedSmokers: $smokerUid")
                }

                // Update the smoker name in all activities for this smoker
                val updatedActivities = room.safeActivities().map { activity ->
                    if (activity.smokerId == smokerUid) {
                        Log.d(TAG, "📝 Updating activity for smoker: ${activity.type} at ${activity.timestamp}")
                        activity.copy(smokerName = updatedName)
                    } else {
                        activity
                    }
                }

                val activitiesUpdatedCount = updatedActivities.count { it.smokerId == smokerUid }
                Log.d(TAG, "📝 Updated $activitiesUpdatedCount activities for smoker $smokerUid")

                // Recalculate stats with updated activities
                val activeParticipantCount = updatedActivities
                    .map { it.smokerId }
                    .distinct()
                    .count()

                val updatedStats = calculateSessionStatsWithActiveCount(
                    updatedActivities,
                    room.startTime,
                    activeParticipantCount
                )

                // Update per-smoker stats to reflect the new name
                val updatedPerSmokerStats = updatedStats.perSmokerStats.mapValues { (key, value) ->
                    if (key == smokerUid) {
                        value.copy(smokerName = updatedName)
                    } else {
                        value
                    }
                }

                val finalStats = updatedStats.copy(
                    perSmokerStats = updatedPerSmokerStats,
                    lastConeSmokerName = if (updatedStats.lastConeSmokerName == room.safeSharedSmokers()[smokerUid]?.get("name")) {
                        updatedName
                    } else {
                        updatedStats.lastConeSmokerName
                    }
                )

                // Update the room with all changes
                val updatedRoom = room.copy(
                    sharedSmokers = updatedSharedSmokers,
                    activities = updatedActivities,
                    currentStats = finalStats,
                    updatedAt = System.currentTimeMillis()
                )

                transaction.set(roomRef, updatedRoom)

                Log.d(TAG, "📝 Successfully updated smoker name in room (${activitiesUpdatedCount} activities updated)")
            }.await()

            Log.d(TAG, "📝 updateSharedSmokerInRoom() SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "📝 updateSharedSmokerInRoom() FAILED", e)
            Result.failure(e)
        }
    }


    suspend fun joinRoomWithSmokerSync(
        userId: String,
        shareCode: String,
        localSmokers: List<Smoker>
    ): Result<RoomData> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🏠 joinRoomWithSmokerSync() - userId=$userId, shareCode=$shareCode, ${localSmokers.size} local smokers")
        return@withContext try {
            // First join the room
            val joinResult = joinRoom(userId, shareCode)

            joinResult.fold(
                onSuccess = { roomData ->
                    // After successfully joining, sync local smokers to the room
                    if (localSmokers.isNotEmpty()) {
                        Log.d(TAG, "🏠 Syncing ${localSmokers.size} local smokers to room after join")

                        syncLocalSmokersToRoom(userId, shareCode, localSmokers).fold(
                            onSuccess = {
                                Log.d(TAG, "🏠 joinRoomWithSmokerSync() SUCCESS - joined room and synced ${localSmokers.size} smokers")

                                // Fetch the updated room data after syncing smokers
                                val roomRef = roomsCollection.document(shareCode)
                                val updatedRoom = roomRef.get().await().toObject(RoomData::class.java)
                                Result.success(updatedRoom ?: roomData)
                            },
                            onFailure = { syncError ->
                                Log.w(TAG, "🏠 Joined room but failed to sync smokers: ${syncError.message}")
                                // Still return success for joining, even if smoker sync failed
                                Result.success(roomData)
                            }
                        )
                    } else {
                        Log.d(TAG, "🏠 No local smokers to sync")
                        Result.success(roomData)
                    }
                },
                onFailure = { joinError ->
                    Log.e(TAG, "🏠 joinRoomWithSmokerSync() failed to join room", joinError)
                    Result.failure(joinError)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "🏠 joinRoomWithSmokerSync() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * Syncs room's shared smokers to local database, maintaining order
     */
    suspend fun syncRoomSmokersToLocal(
        currentUserId: String,
        sharedSmokers: Map<String, Map<String, Any>>
    ): List<Smoker> = withContext(Dispatchers.IO) {
        val newSmokers = mutableListOf<Smoker>()

        if (repository == null) {
            Log.w(TAG, "Repository not available for syncing smokers")
            return@withContext newSmokers
        }

        // Sort shared smokers by their 'order' field to maintain consistent ordering
        val sortedSharedSmokers = sharedSmokers.entries.sortedBy {
            (it.value["order"] as? Long) ?: 0L
        }

        Log.d(TAG, "🔄 Processing ${sortedSharedSmokers.size} shared smokers from room")

        for ((smokerRoomId, smokerData) in sortedSharedSmokers) { // The key is the ID in the room map
            try {
                val name = (smokerData["name"] as? String)?.trim() ?: continue
                val isLocal = smokerData["isLocal"] as? Boolean ?: false

                Log.d(TAG, "🔄 Processing shared smoker: '$name' (Room ID: $smokerRoomId, isLocal: $isLocal)")

                if (isLocal && smokerRoomId.startsWith("local_")) {
                    val uid = smokerRoomId.removePrefix("local_")

                    // CRITICAL: Check for existing smoker by UID, not by name.
                    val existingSmokerByUid = repository.getSmokerByUid(uid)

                    if (existingSmokerByUid == null) {
                        // Only create if we truly don't have this smoker
                        val newSmoker = Smoker(
                            uid = uid, // Use the UID from the cloud
                            name = name,
                            isCloudSmoker = false,
                            cloudUserId = null,
                            shareCode = null,
                            lastSyncTime = System.currentTimeMillis()
                        )

                        val insertedId = repository.insertSmoker(newSmoker)
                        val createdSmoker = newSmoker.copy(smokerId = insertedId)
                        newSmokers.add(createdSmoker)

                        Log.d(TAG, "🔄 ✅ Created new local smoker from room: '$name' (UID: $uid, new local ID: $insertedId)")
                    } else {
                        Log.d(TAG, "🔄 ⚠️ Smoker with UID '$uid' already exists locally (Name: ${existingSmokerByUid.name}, ID: ${existingSmokerByUid.smokerId}) - skipping creation")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "🔄 Error syncing shared smoker: ${e.message}", e)
            }
        }

        Log.d(TAG, "🔄 Synced ${newSmokers.size} new smokers from room")
        newSmokers
    }

    /**
     * Deletes all smokers from the room so all participants see the deletion
     */
    suspend fun deleteAllSmokersFromRoom(
        shareCode: String,
        deletedByUserId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Removing all smokers from room $shareCode")

            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                // Clear all shared smokers
                val emptySharedSmokers = emptyMap<String, Map<String, Any>>()

                // Remove all participants except the one doing the deletion
                val updatedParticipants = listOf(deletedByUserId)
                val updatedActiveParticipants = listOf(deletedByUserId)
                val emptyAwayParticipants = emptyList<String>()
                val emptyPausedSmokers = emptyList<String>()

                // DON'T clear activities - keep them for stats history
                val updatedRoom = room.copy(
                    sharedSmokers = emptySharedSmokers,
                    participants = updatedParticipants,
                    activeParticipants = updatedActiveParticipants,
                    awayParticipants = emptyAwayParticipants,
                    pausedSmokers = emptyPausedSmokers,
                    updatedAt = System.currentTimeMillis()
                )

                transaction.set(roomRef, updatedRoom)
            }.await()

            Log.d(TAG, "✅ Successfully removed all smokers from room (keeping activity history)")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove all smokers from room: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun startRoomListener(
        shareCode: String,
        onChange: (RoomData) -> Unit,
        onSmokerDeleted: ((String) -> Unit)? = null,
        onAllSmokersDeleted: (() -> Unit)? = null,
        onError: (Exception) -> Unit = { Log.e(TAG, "Listener error", it) }
    ) {
        Log.d(TAG, "🎧 Starting enhanced room listener for $shareCode")

        // Remove existing listener for this room if any
        roomListeners[shareCode]?.listener?.remove()

        var lastSharedSmokers = mapOf<String, Map<String, Any>>()

        val listener = roomsCollection.document(shareCode)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "🎧 Room listener error", err)
                    onError(err)
                    return@addSnapshotListener
                }

                snap?.toObject(RoomData::class.java)?.let { room ->
                    val currentSmokers = room.safeSharedSmokers()
                    val currentSmokerCount = currentSmokers.size

                    Log.d(TAG, "🎧 Room updated: ${room.safeActivities().size} activities, ${room.activeParticipants.size} active, $currentSmokerCount smokers")

                    // Check for mass deletion (all smokers deleted)
                    if (lastSharedSmokers.isNotEmpty() && currentSmokers.isEmpty()) {
                        Log.d(TAG, "🎧 All smokers deleted from room")
                        onAllSmokersDeleted?.invoke()
                    } else if (lastSharedSmokers.isNotEmpty()) {
                        // Check for individual deletions
                        val deletedSmokers = lastSharedSmokers.keys - currentSmokers.keys
                        deletedSmokers.forEach { deletedSmokerId ->
                            Log.d(TAG, "🎧 Smoker deleted from room: $deletedSmokerId")
                            onSmokerDeleted?.invoke(deletedSmokerId)
                        }
                    }

                    lastSharedSmokers = currentSmokers
                    onChange(room)
                }
            }

        // Store the listener
        roomListeners[shareCode] = RoomListener(listener, onChange)
        roomListener = listener // Keep for backward compatibility
    }

    fun calculateSessionStatsWithGaps(activities: List<SessionActivity>): SessionStats {
        // Sort activities by timestamp
        val sortedActivities = activities.sortedBy { it.timestamp }

        // Calculate cone gaps
        val coneActivities = sortedActivities.filter { it.type.equals("CONE", ignoreCase = true) }
        var longestGapMs = 0L
        var shortestGapMs = Long.MAX_VALUE
        var lastGapMs: Long? = null
        var previousGapMs: Long? = null
        val gaps = mutableListOf<Long>()

        if (coneActivities.size >= 2) {
            for (i in 1 until coneActivities.size) {
                val gap = coneActivities[i].timestamp - coneActivities[i - 1].timestamp
                gaps.add(gap)

                if (gap > longestGapMs) longestGapMs = gap
                if (gap < shortestGapMs) shortestGapMs = gap
            }

            if (gaps.isNotEmpty()) {
                lastGapMs = gaps.last()
                if (gaps.size >= 2) {
                    previousGapMs = gaps[gaps.size - 2]
                }
            }
        }

        if (shortestGapMs == Long.MAX_VALUE) {
            shortestGapMs = 0L
        }

        // Get last cone info
        val lastCone = coneActivities.lastOrNull()
        val lastConeSmokerName = lastCone?.smokerName
        val sinceLastConeMs = if (lastCone != null) {
            System.currentTimeMillis() - lastCone.timestamp
        } else {
            0L
        }

        // Calculate cones since last bowl
        val lastBowl = sortedActivities
            .filter { it.type.equals("BOWL", ignoreCase = true) }
            .lastOrNull()

        val conesSinceLastBowl = if (lastBowl != null) {
            coneActivities.count { it.timestamp > lastBowl.timestamp }
        } else {
            coneActivities.size
        }

        // Count totals
        val totalCones = coneActivities.size
        val totalJoints = sortedActivities.count { it.type.equals("JOINT", ignoreCase = true) }
        val totalBowls = sortedActivities.count { it.type.equals("BOWL", ignoreCase = true) }

        // ... calculate other stats like rounds, per-smoker stats, etc ...

        return SessionStats(
            totalCones = totalCones,
            totalJoints = totalJoints,
            totalBowls = totalBowls,
            longestGapMs = longestGapMs,
            shortestGapMs = shortestGapMs,
            sinceLastConeMs = sinceLastConeMs,
            sinceLastJointMs = 0L, // Calculate if needed
            sinceLastBowlMs = 0L, // Calculate if needed
            totalRounds = 0, // Calculate based on your logic
            hitsInCurrentRound = 0, // Calculate based on your logic
            participantCount = 0, // Calculate based on participants
            perSmokerStats = emptyMap(), // Calculate per-smoker stats
            lastConeSmokerName = lastConeSmokerName,
            conesSinceLastBowl = conesSinceLastBowl,
            lastGapMs = lastGapMs,
            previousGapMs = previousGapMs
        )
    }

    /**
     * Enhanced: Syncs all local smokers from a user to the room with ordering
     */
    suspend fun syncLocalSmokersToRoom(
        userId: String,
        shareCode: String,
        localSmokers: List<Smoker>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🔄 syncLocalSmokersToRoom() - userId=$userId, shareCode=$shareCode, ${localSmokers.size} smokers")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                val currentSharedSmokers = room.safeSharedSmokers().toMutableMap()
                val now = System.currentTimeMillis()
                var smokersAdded = 0

                localSmokers.forEach { localSmoker ->
                    // The ID in the room is based on the UID
                    val roomSmokerId = "local_${localSmoker.uid}"

                    // Check only by ID (UID) because name can be changed.
                    if (!currentSharedSmokers.containsKey(roomSmokerId)) {
                        val smokerData = mapOf(
                            "smokerId" to roomSmokerId,
                            "name" to localSmoker.name,
                            "isLocal" to true,
                            "addedBy" to userId,
                            "addedAt" to now,
                            "order" to now,
                            "isCloudSmoker" to localSmoker.isCloudSmoker,
                            "cloudUserId" to (localSmoker.cloudUserId ?: ""),
                            "shareCode" to (localSmoker.shareCode ?: ""),
                            "passwordHash" to (localSmoker.passwordHash ?: "")
                        )
                        currentSharedSmokers[roomSmokerId] = smokerData
                        smokersAdded++
                        Log.d(TAG, "🔄 Adding smoker to room: ${localSmoker.name} (UID: $roomSmokerId)")
                    } else {
                        Log.d(TAG, "🔄 Skipping duplicate smoker (already in room by UID): ${localSmoker.name}")
                    }
                }

                if (smokersAdded > 0) {
                    val updatedRoom = room.copy(
                        sharedSmokers = currentSharedSmokers,
                        updatedAt = now
                    )
                    transaction.set(roomRef, updatedRoom)
                    Log.d(TAG, "🔄 Added $smokersAdded new smokers to room (total: ${currentSharedSmokers.size})")
                } else {
                    Log.d(TAG, "🔄 No new smokers to add - all already exist in room")
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🔄 syncLocalSmokersToRoom() FAILED", e)
            Result.failure(e)
        }
    }

    /**
     * DEPRECATED: This function has a race condition. Use findOrCreateSharedSmoker instead.
     */
    suspend fun addSharedSmokerToRoom(
        shareCode: String,
        addedByUserId: String,
        smoker: Smoker
    ): Result<Unit> {
        Log.e(TAG, "FATAL: Deprecated function addSharedSmokerToRoom was called for smoker '${smoker.name}'. Use findOrCreateSharedSmoker instead to prevent race conditions.")
        return Result.failure(Exception("Do not use addSharedSmokerToRoom. Use findOrCreateSharedSmoker."))
    }


    /**
     * Removes a smoker from the room so all participants see the deletion
     */
    suspend fun removeSmokerFromRoom(
        shareCode: String,
        smokerUid: String, // This is now the UID of the smoker
        removedByUserId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🗑️ Removing smoker '$smokerUid' from room $shareCode")

            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                // Remove from shared smokers
                val currentSharedSmokers = room.safeSharedSmokers().toMutableMap()
                currentSharedSmokers.remove(smokerUid)

                // Remove from participants and active participants (for cloud users)
                val updatedParticipants = room.participants.filter { it != smokerUid }
                val updatedActiveParticipants = room.activeParticipants.filter { it != smokerUid }
                val updatedAwayParticipants = room.safeAwayParticipants().filter { it != smokerUid }
                val updatedPausedSmokers = room.safePausedSmokers().filter { it != smokerUid }

                // DON'T remove activities - keep them for stats history
                // Just update the room structure
                val updatedRoom = room.copy(
                    sharedSmokers = currentSharedSmokers,
                    participants = updatedParticipants,
                    activeParticipants = updatedActiveParticipants,
                    awayParticipants = updatedAwayParticipants,
                    pausedSmokers = updatedPausedSmokers,
                    updatedAt = System.currentTimeMillis()
                )

                transaction.set(roomRef, updatedRoom)
            }.await()

            Log.d(TAG, "✅ Successfully removed smoker '$smokerUid' from room (keeping activity history)")
            Result.success(Unit)

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to remove smoker from room: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun clearSessionActivitiesForSmoker(
        shareCode: String,
        smokerUid: String,
        sessionStart: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🧹 Clearing session activities for smoker $smokerUid in room $shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                // Filter out activities for this smoker in the current session
                val filteredActivities = room.safeActivities().filter { activity ->
                    !(activity.smokerId == smokerUid && activity.timestamp >= sessionStart)
                }

                val removedCount = room.safeActivities().size - filteredActivities.size
                Log.d(TAG, "🧹 Removing $removedCount activities for smoker $smokerUid")

                // Recalculate stats
                val updatedStats = calculateSessionStats(filteredActivities, room.startTime)

                // Update room
                val updatedRoom = room.copy(
                    activities = filteredActivities,
                    currentStats = updatedStats,
                    updatedAt = System.currentTimeMillis()
                )

                transaction.set(roomRef, updatedRoom)
            }.await()

            Log.d(TAG, "🧹 Successfully cleared session activities for smoker")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🧹 Failed to clear session activities for smoker", e)
            Result.failure(e)
        }
    }

    suspend fun clearAllSessionActivities(
        shareCode: String,
        sessionStart: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        Log.d(TAG, "🧹 Clearing all session activities in room $shareCode")
        return@withContext try {
            val roomRef = roomsCollection.document(shareCode)

            firestore.runTransaction { transaction ->
                val roomSnapshot = transaction.get(roomRef)
                val room = roomSnapshot.toObject(RoomData::class.java)
                    ?: throw Exception("Room not found")

                // Filter out all activities in the current session
                val filteredActivities = room.safeActivities().filter { activity ->
                    activity.timestamp < sessionStart
                }

                val removedCount = room.safeActivities().size - filteredActivities.size
                Log.d(TAG, "🧹 Removing $removedCount activities from session")

                // Recalculate stats
                val updatedStats = calculateSessionStats(filteredActivities, room.startTime)

                // Update room
                val updatedRoom = room.copy(
                    activities = filteredActivities,
                    currentStats = updatedStats,
                    updatedAt = System.currentTimeMillis()
                )

                transaction.set(roomRef, updatedRoom)
            }.await()

            Log.d(TAG, "🧹 Successfully cleared all session activities")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🧹 Failed to clear all session activities", e)
            Result.failure(e)
        }
    }

    /**
     * Stops and clears the active listener.
     */
    fun stopAllListeners() {
        Log.d(TAG, "🎧 Stopping room listeners")
        roomListener?.remove()
        roomListener = null

        // Remove all room listeners
        roomListeners.values.forEach { it.listener.remove() }
        roomListeners.clear()
    }
}
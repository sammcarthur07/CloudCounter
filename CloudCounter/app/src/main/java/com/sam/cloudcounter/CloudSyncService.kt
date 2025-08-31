package com.sam.cloudcounter

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Simplified CloudSyncService - handles only smoker profiles now
 * Activities are handled by SessionSyncService via room documents
 */
class CloudSyncService(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val repository: ActivityRepository
) {
    companion object {
        private const val COLLECTION_CLOUD_SMOKERS = "cloudSmokers"
        private const val TAG = "CloudSyncService"
    }

    /**
     * Creates a cloud smoker profile for a Google-authenticated user, with optional password hash.
     */
    suspend fun createCloudSmoker(
        userId: String,
        name: String,
        passwordHash: String? = null
    ): Result<CloudSmokerData> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Creating cloud smoker for userId: $userId, name: $name, hasPassword: ${passwordHash != null}")

            val shareCode = generateShareCode()
            val cloudData = CloudSmokerData(
                userId = userId,
                name = name,
                shareCode = shareCode,
                totalCones = 0,
                totalJoints = 0,
                totalBowls = 0,
                lastActivity = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                passwordHash = passwordHash
            )

            firestore.collection(COLLECTION_CLOUD_SMOKERS)
                .document(userId)
                .set(cloudData)
                .await()

            Log.d(TAG, "Successfully created cloud smoker with shareCode: $shareCode")
            Result.success(cloudData)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create cloud smoker profile for $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Gets a smoker by name from the local repository
     */
    suspend fun getSmokerByName(name: String): Smoker? = withContext(Dispatchers.IO) {
        try {
            repository.getSmokerByName(name)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting smoker by name: $name", e)
            null
        }
    }




    /**
     * Send a session invitation push notification to a cloud smoker
     */
    suspend fun sendSessionInvitation(
        toUserId: String,
        fromUserName: String,
        sessionShareCode: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Create an invitation document in Firestore that will trigger a Cloud Function
            val invitation = hashMapOf(
                "toUserId" to toUserId,
                "fromUserName" to fromUserName,
                "sessionShareCode" to sessionShareCode,
                "timestamp" to System.currentTimeMillis(),
                "type" to "session_invitation"
            )

            firestore.collection("notifications")
                .add(invitation)
                .await()

            Log.d(TAG, "Session invitation created for user $toUserId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send session invitation", e)
            Result.failure(e)
        }
    }

    /**
     * Convert CloudSmokerData to CloudSmokerSearchResult
     */
    fun CloudSmokerData.toSearchResult(isOnline: Boolean = false): CloudSmokerSearchResult {
        return CloudSmokerSearchResult(
            userId = this.userId,
            name = this.name,
            shareCode = this.shareCode,
            totalActivities = this.totalCones + this.totalJoints + this.totalBowls,
            lastActivity = this.lastActivity,
            hasPassword = !this.passwordHash.isNullOrEmpty(),
            totalCones = this.totalCones,
            totalJoints = this.totalJoints,
            totalBowls = this.totalBowls,
            isOnline = isOnline
        )
    }

    /**
     * Updates the password hash for an existing cloud smoker.
     */
    suspend fun updateCloudSmokerPassword(
        userId: String,
        passwordHash: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val updateData = mapOf(
                "passwordHash" to passwordHash,
                "updatedAt" to System.currentTimeMillis()
            )
            firestore.collection(COLLECTION_CLOUD_SMOKERS)
                .document(userId)
                .update(updateData)
                .await()

            Log.d(TAG, "Successfully updated password for user: $userId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update password for user: $userId", e)
            Result.failure(e)
        }
    }

    /**
     * Verifies the provided plain-text password against the stored hash.
     */
    suspend fun verifyCloudSmokerPassword(
        userId: String,
        password: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Verifying password for user: $userId")

            val snapshot = firestore.collection(COLLECTION_CLOUD_SMOKERS)
                .document(userId)
                .get()
                .await()

            val cloudData = snapshot.toObject(CloudSmokerData::class.java)
            val storedHash = cloudData?.passwordHash

            Log.d(TAG, "Retrieved cloud data for $userId, hasPasswordHash: ${!storedHash.isNullOrBlank()}")

            val isValid = if (storedHash.isNullOrBlank()) {
                true
            } else {
                PasswordUtils.verifyPassword(password, storedHash)
            }

            Log.d(TAG, "Password verification result for $userId: $isValid")
            Result.success(isValid)
        } catch (e: Exception) {
            Log.e(TAG, "Error verifying password for user: $userId", e)
            Result.failure(e)
        }
    }

    suspend fun updateCloudSmokerName(userId: String, newName: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "name" to newName,
                "updatedAt" to System.currentTimeMillis()  // Changed from "lastUpdated"
            )

            firestore.collection(COLLECTION_CLOUD_SMOKERS)  // Changed from "smokers" to use the constant
                .document(userId)
                .update(updates)
                .await()

            Log.d(TAG, "Updated cloud smoker name for $userId to $newName")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update cloud smoker name", e)
            Result.failure(e)
        }
    }

    /**
     * Searches for cloud smokers by name, indicating if each has a password.
     */
    suspend fun searchSmokersByName(name: String): Result<List<CloudSmokerSearchResult>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching for smokers by name: $name")

                val query = firestore.collection(COLLECTION_CLOUD_SMOKERS)
                    .whereGreaterThanOrEqualTo("name", name)
                    .whereLessThanOrEqualTo("name", name + "\uf8ff")
                    .limit(20)
                    .get()
                    .await()

                val results = query.documents.mapNotNull { doc ->
                    doc.toObject(CloudSmokerData::class.java)?.let { cloudData ->
                        CloudSmokerSearchResult(
                            userId = cloudData.userId,
                            name = cloudData.name,
                            shareCode = cloudData.shareCode,
                            totalActivities = cloudData.totalCones + cloudData.totalJoints + cloudData.totalBowls,
                            lastActivity = cloudData.lastActivity,
                            hasPassword = !cloudData.passwordHash.isNullOrBlank()
                        )
                    }
                }

                Log.d(TAG, "Found ${results.size} smokers matching name: $name")
                Result.success(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching smokers by name: $name", e)
                Result.failure(e)
            }
        }

    /**
     * Searches for a cloud smoker by share code.
     */
    suspend fun searchSmokerByCode(shareCode: String): Result<CloudSmokerSearchResult?> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Searching for smoker by code: $shareCode")

                val snapshot = firestore.collection(COLLECTION_CLOUD_SMOKERS)
                    .whereEqualTo("shareCode", shareCode)
                    .limit(1)
                    .get()
                    .await()

                val data = snapshot.documents.firstOrNull()
                    ?.toObject(CloudSmokerData::class.java)

                val result = data?.let { cloudData ->
                    CloudSmokerSearchResult(
                        userId = cloudData.userId,
                        name = cloudData.name,
                        shareCode = cloudData.shareCode,
                        totalActivities = cloudData.totalCones + cloudData.totalJoints + cloudData.totalBowls,
                        lastActivity = cloudData.lastActivity,
                        hasPassword = !cloudData.passwordHash.isNullOrBlank()
                    )
                }

                Log.d(TAG, "Search by code result: ${if (result != null) "found ${result.name}" else "not found"}")
                Result.success(result)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching smoker by code: $shareCode", e)
                Result.failure(e)
            }
        }

    /**
     * Retrieves all cloud smokers for browsing.
     */
    suspend fun getAllCloudSmokers(): Result<List<CloudSmokerSearchResult>> =
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Fetching all cloud smokers")

                val snapshot = firestore.collection(COLLECTION_CLOUD_SMOKERS)
                    .orderBy("updatedAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                    .limit(50)
                    .get()
                    .await()

                val results = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(CloudSmokerData::class.java)?.let { cloudData ->
                        CloudSmokerSearchResult(
                            userId = cloudData.userId,
                            name = cloudData.name,
                            shareCode = cloudData.shareCode,
                            totalActivities = cloudData.totalCones + cloudData.totalJoints + cloudData.totalBowls,
                            lastActivity = cloudData.lastActivity,
                            hasPassword = !cloudData.passwordHash.isNullOrBlank()
                        )
                    }
                }

                Log.d(TAG, "Retrieved ${results.size} cloud smokers")
                results.forEach { smoker ->
                    Log.d(TAG, "Smoker: ${smoker.name}, hasPassword: ${smoker.hasPassword}")
                }

                Result.success(results)
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching all cloud smokers", e)
                Result.failure(e)
            }
        }

    /**
     * Syncs local smoker profile data to cloud (profile only, not activities).
     */
    suspend fun syncSmokerToCloud(smoker: Smoker): Result<Unit> =
        withContext(Dispatchers.IO) {
            if (!smoker.isCloudSmoker || smoker.cloudUserId == null) {
                return@withContext Result.failure(Exception("Smoker is not a cloud smoker"))
            }
            try {
                val totalCones = repository.countConesForSmoker(smoker.smokerId)
                val totalJoints = repository.countJointsForSmoker(smoker.smokerId)
                val totalBowls = repository.countBowlsForSmoker(smoker.smokerId)
                val lastActivity = repository.getLastActivityForSmoker(smoker.smokerId)?.timestamp ?: 0L

                val updateMap = mutableMapOf<String, Any>(
                    "name" to smoker.name,
                    "totalCones" to totalCones,
                    "totalJoints" to totalJoints,
                    "totalBowls" to totalBowls,
                    "lastActivity" to lastActivity,
                    "updatedAt" to System.currentTimeMillis()
                )

                if (!smoker.passwordHash.isNullOrBlank()) {
                    updateMap["passwordHash"] = smoker.passwordHash!!
                }

                firestore.collection(COLLECTION_CLOUD_SMOKERS)
                    .document(smoker.cloudUserId)
                    .set(updateMap, SetOptions.merge())
                    .await()

                repository.markSmokerSynced(smoker.smokerId)
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync smoker: ${smoker.name}", e)
                Result.failure(e)
            }
        }

    /**
     * Retrieves a single cloud smoker's data.
     */
    suspend fun getCloudSmokerData(userId: String): Result<CloudSmokerData?> =
        withContext(Dispatchers.IO) {
            try {
                val doc = firestore.collection(COLLECTION_CLOUD_SMOKERS)
                    .document(userId)
                    .get()
                    .await()
                Result.success(doc.toObject(CloudSmokerData::class.java))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Generates a unique 6-character share code.
     */
    private suspend fun generateShareCode(): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var code: String
        do {
            code = (1..6).map { chars.random() }.joinToString("")
        } while (
            !firestore.collection(COLLECTION_CLOUD_SMOKERS)
                .whereEqualTo("shareCode", code)
                .limit(1)
                .get()
                .await()
                .isEmpty
        )
        return code
    }

    /**
     * Ensures the current user's smoker profile is synced to cloud before joining a room
     */
    suspend fun ensureCurrentSmokerIsSynced(currentUserId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Ensuring current smoker is synced for user: $currentUserId")

            val currentSmoker = repository.getSmokerByCloudUserId(currentUserId)

            if (currentSmoker == null) {
                Log.e(TAG, "🔄 No local smoker found for current user: $currentUserId")
                return@withContext Result.failure(Exception("No smoker profile found for current user"))
            }

            Log.d(TAG, "🔄 Found local smoker: ${currentSmoker.name}")

            syncSmokerToCloud(currentSmoker).fold(
                onSuccess = {
                    Log.d(TAG, "🔄 Successfully synced current smoker: ${currentSmoker.name}")
                },
                onFailure = { error ->
                    Log.e(TAG, "🔄 Failed to sync current smoker: ${error.message}")
                    return@withContext Result.failure(error)
                }
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "🔄 Error ensuring current smoker is synced", e)
            Result.failure(e)
        }
    }

    /**
     * Gets a smoker's profile data from the cloud
     */
    suspend fun getCloudSmokerProfile(userId: String): Result<CloudSmokerData?> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "🔄 Getting cloud smoker profile for: $userId")

            val doc = firestore.collection(COLLECTION_CLOUD_SMOKERS)
                .document(userId)
                .get()
                .await()

            val cloudData = doc.toObject(CloudSmokerData::class.java)
            Log.d(TAG, "🔄 Retrieved cloud smoker: ${cloudData?.name ?: "NOT FOUND"}")

            Result.success(cloudData)
        } catch (e: Exception) {
            Log.e(TAG, "🔄 Failed to get cloud smoker profile for $userId", e)
            Result.failure(e)
        }
    }
}

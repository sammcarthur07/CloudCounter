package com.sam.cloudcounter

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Handles cloud synchronization of stash data using Firebase Firestore
 */
class StashCloudSync {
    private val firestore = FirebaseFirestore.getInstance()
    private val stashCollection = firestore.collection("user_stash")
    
    companion object {
        private const val TAG = "StashCloudSync"
        
        // Firestore field names
        private const val FIELD_CURRENT_GRAMS = "currentGrams"
        private const val FIELD_TOTAL_GRAMS = "totalGrams"
        private const val FIELD_PRICE_PER_GRAM = "pricePerGram"
        private const val FIELD_LAST_UPDATED = "lastUpdated"
        private const val FIELD_LAST_SYNC = "lastSync"
        
        // Ratio fields
        private const val FIELD_CONE_GRAMS = "coneGrams"
        private const val FIELD_JOINT_GRAMS = "jointGrams"
        private const val FIELD_BOWL_GRAMS = "bowlGrams"
        private const val FIELD_USER_DEFINED_CONE_GRAMS = "userDefinedConeGrams"
        private const val FIELD_DEDUCT_CONES = "deductCones"
        private const val FIELD_DEDUCT_JOINTS = "deductJoints"
        private const val FIELD_DEDUCT_BOWLS = "deductBowls"
    }
    
    /**
     * Uploads current stash data to Firestore
     */
    suspend fun uploadStash(userId: String, stash: Stash): Result<Unit> {
        return try {
            Log.d(TAG, "📤 Uploading stash for user: $userId")
            Log.d(TAG, "📤 Stash data: ${stash.currentGrams}g @ $${stash.pricePerGram}/g")
            
            val stashData = hashMapOf(
                FIELD_CURRENT_GRAMS to stash.currentGrams,
                FIELD_TOTAL_GRAMS to stash.totalGrams,
                FIELD_PRICE_PER_GRAM to stash.pricePerGram,
                FIELD_LAST_UPDATED to stash.lastUpdated,
                FIELD_LAST_SYNC to Date()
            )
            
            stashCollection.document(userId)
                .set(stashData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "✅ Stash uploaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload stash", e)
            Result.failure(e)
        }
    }
    
    /**
     * Downloads stash data from Firestore
     */
    suspend fun downloadStash(userId: String): Result<Stash?> {
        return try {
            Log.d(TAG, "📥 Downloading stash for user: $userId")
            
            val document = stashCollection.document(userId).get().await()
            
            if (!document.exists()) {
                Log.d(TAG, "📥 No cloud stash found for user")
                return Result.success(null)
            }
            
            val stash = Stash(
                currentGrams = document.getDouble(FIELD_CURRENT_GRAMS) ?: 0.0,
                totalGrams = document.getDouble(FIELD_TOTAL_GRAMS) ?: 0.0,
                pricePerGram = document.getDouble(FIELD_PRICE_PER_GRAM) ?: 15.0,
                lastUpdated = document.getDate(FIELD_LAST_UPDATED) ?: Date()
            )
            
            Log.d(TAG, "✅ Stash downloaded: ${stash.currentGrams}g @ $${stash.pricePerGram}/g")
            Result.success(stash)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to download stash", e)
            Result.failure(e)
        }
    }
    
    /**
     * Uploads consumption ratios to Firestore
     */
    suspend fun uploadRatios(userId: String, ratios: ConsumptionRatio): Result<Unit> {
        return try {
            Log.d(TAG, "📤 Uploading ratios for user: $userId")
            
            val ratioData = hashMapOf(
                FIELD_CONE_GRAMS to ratios.coneGrams,
                FIELD_JOINT_GRAMS to ratios.jointGrams,
                FIELD_BOWL_GRAMS to ratios.bowlGrams,
                FIELD_USER_DEFINED_CONE_GRAMS to ratios.userDefinedConeGrams,
                FIELD_DEDUCT_CONES to ratios.deductConesFromStash,
                FIELD_DEDUCT_JOINTS to ratios.deductJointsFromStash,
                FIELD_DEDUCT_BOWLS to ratios.deductBowlsFromStash,
                FIELD_LAST_UPDATED to ratios.lastUpdated,
                FIELD_LAST_SYNC to Date()
            )
            
            stashCollection.document(userId)
                .collection("settings")
                .document("ratios")
                .set(ratioData, SetOptions.merge())
                .await()
            
            Log.d(TAG, "✅ Ratios uploaded successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to upload ratios", e)
            Result.failure(e)
        }
    }
    
    /**
     * Downloads consumption ratios from Firestore
     */
    suspend fun downloadRatios(userId: String): Result<ConsumptionRatio?> {
        return try {
            Log.d(TAG, "📥 Downloading ratios for user: $userId")
            
            val document = stashCollection.document(userId)
                .collection("settings")
                .document("ratios")
                .get()
                .await()
            
            if (!document.exists()) {
                Log.d(TAG, "📥 No cloud ratios found for user")
                return Result.success(null)
            }
            
            val ratios = ConsumptionRatio(
                coneGrams = document.getDouble(FIELD_CONE_GRAMS) ?: 0.3,
                jointGrams = document.getDouble(FIELD_JOINT_GRAMS) ?: 0.7,
                bowlGrams = document.getDouble(FIELD_BOWL_GRAMS) ?: 0.2,
                userDefinedConeGrams = document.getDouble(FIELD_USER_DEFINED_CONE_GRAMS),
                deductConesFromStash = document.getBoolean(FIELD_DEDUCT_CONES) ?: true,
                deductJointsFromStash = document.getBoolean(FIELD_DEDUCT_JOINTS) ?: true,
                deductBowlsFromStash = document.getBoolean(FIELD_DEDUCT_BOWLS) ?: false,
                lastUpdated = document.getDate(FIELD_LAST_UPDATED) ?: Date()
            )
            
            Log.d(TAG, "✅ Ratios downloaded: C=${ratios.coneGrams}g, J=${ratios.jointGrams}g, B=${ratios.bowlGrams}g")
            Result.success(ratios)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to download ratios", e)
            Result.failure(e)
        }
    }
    
    /**
     * Performs a full sync - downloads from cloud and merges with local data
     * Returns the merged stash data that should be saved locally
     */
    suspend fun syncStash(userId: String, localStash: Stash?): Result<Stash> {
        return try {
            Log.d(TAG, "🔄 Starting stash sync for user: $userId")
            Log.d(TAG, "🔄 Local stash: ${localStash?.currentGrams}g @ $${localStash?.pricePerGram}/g")
            
            // Download cloud stash
            val cloudResult = downloadStash(userId)
            if (cloudResult.isFailure) {
                Log.e(TAG, "🔄 Failed to download cloud stash, using local")
                return Result.success(localStash ?: Stash())
            }
            
            val cloudStash = cloudResult.getOrNull()
            Log.d(TAG, "🔄 Cloud stash: ${cloudStash?.currentGrams}g @ $${cloudStash?.pricePerGram}/g")
            
            // Merge strategy: Use the most recently updated stash
            val mergedStash = when {
                cloudStash == null && localStash == null -> {
                    Log.d(TAG, "🔄 No stash found, creating new")
                    Stash()
                }
                cloudStash == null -> {
                    Log.d(TAG, "🔄 No cloud stash, using local and uploading")
                    // Upload local stash to cloud
                    uploadStash(userId, localStash!!)
                    localStash
                }
                localStash == null -> {
                    Log.d(TAG, "🔄 No local stash, using cloud")
                    cloudStash
                }
                else -> {
                    // Both exist - merge intelligently
                    // If local stash is empty (0.0g), always prefer cloud stash
                    if (localStash.currentGrams == 0.0 && localStash.totalGrams == 0.0 && cloudStash.currentGrams > 0) {
                        Log.d(TAG, "🔄 Local stash is empty, using cloud stash with ${cloudStash.currentGrams}g")
                        cloudStash
                    }
                    // If cloud stash is empty and local has data, use local
                    else if (cloudStash.currentGrams == 0.0 && cloudStash.totalGrams == 0.0 && localStash.currentGrams > 0) {
                        Log.d(TAG, "🔄 Cloud stash is empty, uploading local with ${localStash.currentGrams}g")
                        uploadStash(userId, localStash)
                        localStash
                    }
                    // Both have data - use the most recent
                    else if (localStash.lastUpdated.after(cloudStash.lastUpdated)) {
                        Log.d(TAG, "🔄 Both have data, local is newer (${localStash.currentGrams}g), uploading to cloud")
                        uploadStash(userId, localStash)
                        localStash
                    } else {
                        Log.d(TAG, "🔄 Both have data, cloud is newer (${cloudStash.currentGrams}g), using cloud")
                        cloudStash
                    }
                }
            }
            
            Log.d(TAG, "✅ Sync complete: ${mergedStash.currentGrams}g @ $${mergedStash.pricePerGram}/g")
            Result.success(mergedStash)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Sync failed", e)
            Result.failure(e)
        }
    }
    
    /**
     * Performs a full sync of consumption ratios
     */
    suspend fun syncRatios(userId: String, localRatios: ConsumptionRatio?): Result<ConsumptionRatio> {
        return try {
            Log.d(TAG, "🔄 Starting ratios sync for user: $userId")
            
            // Download cloud ratios
            val cloudResult = downloadRatios(userId)
            if (cloudResult.isFailure) {
                Log.e(TAG, "🔄 Failed to download cloud ratios, using local")
                return Result.success(localRatios ?: ConsumptionRatio())
            }
            
            val cloudRatios = cloudResult.getOrNull()
            
            // Merge strategy: Use the most recently updated ratios
            val mergedRatios = when {
                cloudRatios == null && localRatios == null -> {
                    Log.d(TAG, "🔄 No ratios found, creating defaults")
                    ConsumptionRatio()
                }
                cloudRatios == null -> {
                    Log.d(TAG, "🔄 No cloud ratios, uploading local")
                    uploadRatios(userId, localRatios!!)
                    localRatios
                }
                localRatios == null -> {
                    Log.d(TAG, "🔄 No local ratios, using cloud")
                    cloudRatios
                }
                else -> {
                    // Both exist - use the most recent
                    if (localRatios.lastUpdated.after(cloudRatios.lastUpdated)) {
                        Log.d(TAG, "🔄 Local ratios are newer, uploading to cloud")
                        uploadRatios(userId, localRatios)
                        localRatios
                    } else {
                        Log.d(TAG, "🔄 Cloud ratios are newer, using cloud")
                        cloudRatios
                    }
                }
            }
            
            Log.d(TAG, "✅ Ratios sync complete")
            Result.success(mergedRatios)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Ratios sync failed", e)
            Result.failure(e)
        }
    }
}
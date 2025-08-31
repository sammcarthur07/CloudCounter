package com.sam.cloudcounter

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class VideoSignalingService {
    private val firestore = FirebaseFirestore.getInstance()

    companion object {
        private const val TAG = "VideoSignaling"
        private const val COLLECTION_VIDEO_ROOMS = "video_rooms"
    }

    // FIX: Add all fields that Firebase uses
    data class ParticipantData(
        var userId: String = "",      // Changed from val to var
        var userName: String = "",    // Changed from val to var
        var joinedAt: Long = 0,       // Changed from val to var
        var isActive: Boolean = true, // Changed from val to var
        var leftAt: Long? = null,     // Changed from val to var
        var returnedAt: Long? = null, // Changed from val to var
        var lastHeartbeat: Long? = null // Changed from val to var
    )

    data class SignalData(
        val type: String = "",
        val senderId: String = "",
        val receiverId: String = "",
        val sdp: String? = null,
        val candidate: String? = null,
        val sdpMid: String? = null,
        val sdpMLineIndex: Int? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    suspend fun joinVideoRoom(roomId: String, userId: String, userName: String): Result<Unit> {
        return try {
            Log.d(TAG, "Joining video room - roomId: '$roomId', userId: '$userId', userName: '$userName'")

            val participantData = mapOf(
                "userId" to userId,
                "userName" to userName,
                "joinedAt" to System.currentTimeMillis(),
                "isActive" to true,
                "lastHeartbeat" to System.currentTimeMillis()
            )

            // Use set instead of add to ensure the document exists
            firestore.collection(COLLECTION_VIDEO_ROOMS)
                .document(roomId)
                .collection("participants")
                .document(userId)
                .set(participantData)
                .await()

            // Also check if other participants exist
            val existingParticipants = firestore.collection(COLLECTION_VIDEO_ROOMS)
                .document(roomId)
                .collection("participants")
                .whereEqualTo("isActive", true)
                .get()
                .await()

            Log.d(TAG, "Joined room successfully. Active participants: ${existingParticipants.size()}")
            existingParticipants.documents.forEach { doc ->
                val data = doc.data
                Log.d(TAG, "  Participant: ${doc.id} - ${data?.get("userName")} (active: ${data?.get("isActive")})")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to join video room", e)
            Result.failure(e)
        }
    }

    suspend fun leaveVideoRoom(roomId: String, userId: String): Result<Unit> {
        return try {
            // Don't delete the participant, just mark as inactive
            firestore.collection(COLLECTION_VIDEO_ROOMS)
                .document(roomId)
                .collection("participants")
                .document(userId)
                .update(
                    "isActive", false,
                    "leftAt", System.currentTimeMillis()
                )
                .await()

            Log.d(TAG, "Marked as inactive in video room: $roomId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to leave video room", e)
            Result.failure(e)
        }
    }

    suspend fun returnToVideoRoom(roomId: String, userId: String, userName: String): Result<Unit> {
        return try {
            val updates = mapOf(
                "userId" to userId,
                "userName" to userName,
                "isActive" to true,
                "returnedAt" to System.currentTimeMillis(),
                "lastHeartbeat" to System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_VIDEO_ROOMS)
                .document(roomId)
                .collection("participants")
                .document(userId)
                .update(updates)
                .await()

            Log.d(TAG, "Returned to video room: $roomId")
            Result.success(Unit)
        } catch (e: Exception) {
            // If update fails, try to rejoin
            Log.e(TAG, "Failed to update, attempting rejoin", e)
            joinVideoRoom(roomId, userId, userName)
        }
    }

    fun observeParticipants(roomId: String): Flow<List<ParticipantData>> = callbackFlow {
        Log.d(TAG, "Starting to observe participants in room: $roomId")

        val listener = firestore.collection(COLLECTION_VIDEO_ROOMS)
            .document(roomId)
            .collection("participants")
            .whereEqualTo("isActive", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing participants", error)
                    return@addSnapshotListener
                }

                val participants = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        doc.toObject(ParticipantData::class.java)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing participant: ${e.message}")
                        null
                    }
                } ?: emptyList()

                Log.d(TAG, "Participants updated: ${participants.size}")
                participants.forEach { p ->
                    Log.d(TAG, "  - ${p.userId}: ${p.userName} (active: ${p.isActive})")
                }

                trySend(participants)
            }

        awaitClose { listener.remove() }
    }

    fun observeSignals(roomId: String, userId: String): Flow<SignalData> = callbackFlow {
        val listener = firestore.collection(COLLECTION_VIDEO_ROOMS)
            .document(roomId)
            .collection("signals")
            .whereEqualTo("receiverId", userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing signals", error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val signal = change.document.toObject(SignalData::class.java)
                        trySend(signal)

                        // Delete the signal after processing
                        change.document.reference.delete()
                    }
                }
            }

        awaitClose { listener.remove() }
    }

    suspend fun sendOffer(roomId: String, senderId: String, senderName: String, receiverId: String, sdp: String): Result<Unit> {
        return sendSignal(roomId, "offer", senderId, receiverId, sdp, null)
    }

    suspend fun sendAnswer(roomId: String, senderId: String, senderName: String, receiverId: String, sdp: String): Result<Unit> {
        return sendSignal(roomId, "answer", senderId, receiverId, sdp, null)
    }

    suspend fun sendIceCandidate(roomId: String, senderId: String, receiverId: String, candidate: String): Result<Unit> {
        return sendSignal(roomId, "ice_candidate", senderId, receiverId, null, candidate)
    }

    private suspend fun sendSignal(
        roomId: String,
        type: String,
        senderId: String,
        receiverId: String,
        sdp: String?,
        candidate: String?
    ): Result<Unit> {
        return try {
            val signalData = SignalData(
                type = type,
                senderId = senderId,
                receiverId = receiverId,
                sdp = sdp,
                candidate = candidate,
                timestamp = System.currentTimeMillis()
            )

            firestore.collection(COLLECTION_VIDEO_ROOMS)
                .document(roomId)
                .collection("signals")
                .add(signalData)
                .await()

            Log.d(TAG, "Signal sent: $type from $senderId to $receiverId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send signal", e)
            Result.failure(e)
        }
    }
}
package com.sam.cloudcounter

import android.util.Log
import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

class ChatSyncService {
    companion object {
        private const val TAG = "ChatSyncService"
        private const val COLLECTION_MESSAGES = "chat_messages"
        private const val COLLECTION_PRESENCE = "chat_presence"
        private const val MESSAGE_RETENTION_DAYS = 30L
    }

    private val firestore = FirebaseFirestore.getInstance()
    private val messagesCollection = firestore.collection(COLLECTION_MESSAGES)
    private val presenceCollection = firestore.collection(COLLECTION_PRESENCE)

    /**
     * Send a chat message to a room
     */
    suspend fun sendMessage(
        roomId: String,
        senderId: String,
        senderName: String,
        message: String
    ): Result<String> {
        return try {
            val messageDoc = messagesCollection.document()
            val messageData = hashMapOf(
                "roomId" to roomId,
                "senderId" to senderId,
                "senderName" to senderName,
                "message" to message,
                "timestamp" to FieldValue.serverTimestamp(),
                "isDeleted" to false,
                "editedAt" to null,
                "reactions" to emptyMap<String, String>()
            )

            messageDoc.set(messageData).await()
            Log.d(TAG, "Message sent successfully: ${messageDoc.id}")
            Result.success(messageDoc.id)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message", e)
            Result.failure(e)
        }
    }

    /**
     * Edit a message
     */
    suspend fun editMessage(
        messageId: String,
        newContent: String
    ): Result<Unit> {
        return try {
            messagesCollection.document(messageId)
                .update(
                    "message", newContent,
                    "editedAt", FieldValue.serverTimestamp()
                )
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to edit message", e)
            Result.failure(e)
        }
    }

    /**
     * Add reaction to a message
     */
    suspend fun addReaction(
        messageId: String,
        userId: String,
        reaction: String
    ): Result<Unit> {
        return try {
            messagesCollection.document(messageId)
                .update("reactions.$userId", reaction)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add reaction", e)
            Result.failure(e)
        }
    }

    /**
     * Stream messages for a room with real-time updates
     */
    fun streamRoomMessages(
        roomId: String,
        limit: Int = 50
    ): Flow<List<ChatMessageData>> = callbackFlow {
        val listener = messagesCollection
            .whereEqualTo("roomId", roomId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit.toLong())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error streaming messages", error)
                    return@addSnapshotListener
                }

                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ChatMessageData(
                            id = doc.id,
                            roomId = doc.getString("roomId") ?: "",
                            senderId = doc.getString("senderId") ?: "",
                            senderName = doc.getString("senderName") ?: "",
                            message = doc.getString("message") ?: "",
                            timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                            isDeleted = doc.getBoolean("isDeleted") ?: false,
                            editedAt = doc.getTimestamp("editedAt")?.toDate()?.time,
                            reactions = (doc.get("reactions") as? Map<String, String>) ?: emptyMap()
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing message", e)
                        null
                    }
                } ?: emptyList()

                trySend(messages)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Update user presence in a room
     */
    suspend fun updatePresence(
        roomId: String,
        userId: String,
        userName: String,
        isOnline: Boolean,
        isTyping: Boolean = false
    ): Result<Unit> {
        return try {
            val presenceData = hashMapOf(
                "roomId" to roomId,
                "userId" to userId,
                "userName" to userName,
                "isOnline" to isOnline,
                "isTyping" to isTyping,
                "lastSeen" to FieldValue.serverTimestamp()
            )

            presenceCollection
                .document("${roomId}_${userId}")
                .set(presenceData)
                .await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update presence", e)
            Result.failure(e)
        }
    }

    /**
     * Stream online users in a room
     */
    fun streamOnlineUsers(roomId: String): Flow<List<ChatUserPresence>> = callbackFlow {
        val listener = presenceCollection
            .whereEqualTo("roomId", roomId)
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error streaming online users", error)
                    return@addSnapshotListener
                }

                val users = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ChatUserPresence(
                            userId = doc.getString("userId") ?: "",
                            userName = doc.getString("userName") ?: "",
                            roomId = roomId,
                            isOnline = true,
                            isTyping = doc.getBoolean("isTyping") ?: false,
                            lastSeen = doc.getTimestamp("lastSeen")?.toDate()?.time ?: 0L
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error parsing user presence", e)
                        null
                    }
                } ?: emptyList()

                trySend(users)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Clean up old messages (for maintenance)
     */
    suspend fun cleanupOldMessages(): Result<Int> {
        return try {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(MESSAGE_RETENTION_DAYS)
            val oldMessages = messagesCollection
                .whereLessThan("timestamp", cutoffTime)
                .get()
                .await()

            var deletedCount = 0
            val batch = firestore.batch()

            oldMessages.documents.forEach { doc ->
                batch.delete(doc.reference)
                deletedCount++

                // Firestore batch limit is 500
                if (deletedCount % 500 == 0) {
                    batch.commit().await()
                }
            }

            if (deletedCount % 500 != 0) {
                batch.commit().await()
            }

            Log.d(TAG, "Cleaned up $deletedCount old messages")
            Result.success(deletedCount)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to cleanup old messages", e)
            Result.failure(e)
        }
    }

    /**
     * Get message history with pagination
     */
    suspend fun getMessageHistory(
        roomId: String,
        startAfter: DocumentSnapshot? = null,
        limit: Int = 50
    ): Result<MessageHistoryPage> {
        return try {
            var query = messagesCollection
                .whereEqualTo("roomId", roomId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())

            startAfter?.let {
                query = query.startAfter(it)
            }

            val snapshot = query.get().await()

            val messages = snapshot.documents.mapNotNull { doc ->
                try {
                    ChatMessageData(
                        id = doc.id,
                        roomId = doc.getString("roomId") ?: "",
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "",
                        message = doc.getString("message") ?: "",
                        timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L,
                        isDeleted = doc.getBoolean("isDeleted") ?: false,
                        editedAt = doc.getTimestamp("editedAt")?.toDate()?.time,
                        reactions = (doc.get("reactions") as? Map<String, String>) ?: emptyMap()
                    )
                } catch (e: Exception) {
                    null
                }
            }

            val lastDocument = if (snapshot.documents.isNotEmpty()) {
                snapshot.documents.last()
            } else null

            Result.success(MessageHistoryPage(messages, lastDocument))

        } catch (e: Exception) {
            Log.e(TAG, "Failed to get message history", e)
            Result.failure(e)
        }
    }
}

/**
 * Data class for chat messages from Firebase
 */
data class ChatMessageData(
    val id: String,
    val roomId: String,
    val senderId: String,
    val senderName: String,
    val message: String,
    val timestamp: Long,
    val isDeleted: Boolean,
    val editedAt: Long? = null,
    val reactions: Map<String, String> = emptyMap()
)

/**
 * Data class for user presence
 */
data class ChatUserPresence(
    val userId: String,
    val userName: String,
    val roomId: String,
    val isOnline: Boolean,
    val isTyping: Boolean,
    val lastSeen: Long
)

/**
 * Data class for paginated message history
 */
data class MessageHistoryPage(
    val messages: List<ChatMessageData>,
    val lastDocument: DocumentSnapshot?
)
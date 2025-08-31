package com.sam.cloudcounter

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val chatDao: ChatDao = AppDatabase.getDatabase(application).chatDao()
    private val firestore = FirebaseFirestore.getInstance()

    private var currentUserId: String? = null
    private var currentUserName: String? = null
    private var currentRoomId: String? = null

    private val _currentRoomMessages = MutableLiveData<List<ChatMessage>>()
    val currentRoomMessages: LiveData<List<ChatMessage>> = _currentRoomMessages

    private val _onlineUsers = MutableLiveData<List<ChatUser>>()
    val onlineUsers: LiveData<List<ChatUser>> = _onlineUsers

    private val _typingUsers = MutableLiveData<List<String>>()
    val typingUsers: LiveData<List<String>> = _typingUsers

    var onNewMessageReceived: ((ChatMessage) -> Unit)? = null

    private var messageListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null

    // Track processed message IDs to prevent duplicates and track startup
    private val processedMessageIds = mutableSetOf<String>()
    private var isInitialLoad = true
    private var startupTimestamp = 0L

    // Presence management
    private var cleanupJob: Job? = null
    private val STALE_THRESHOLD = 5 * 60 * 1000L // 5 minutes, same as video chat

    companion object {
        private const val TAG = "ChatViewModel"
        private const val COLLECTION_CHAT_USERS = "chat_users"
    }

    fun initializeChat(userId: String, userName: String) {
        Log.d(TAG, "🔍 DEBUG initializeChat(): userId='$userId', userName='$userName'")
        currentUserId = userId
        currentUserName = userName
        startupTimestamp = System.currentTimeMillis()
        Log.d(TAG, "🔍 DEBUG initializeChat(): currentUserId set to '$currentUserId'")
        Log.d(TAG, "Chat initialized for user: $userName ($userId) at $startupTimestamp")
    }

    fun joinRoom(roomId: String, isSeshChat: Boolean) {

        Log.d(TAG, "🔍 DEBUG joinRoom(): roomId='$roomId'")
        Log.d(TAG, "🔍 DEBUG joinRoom(): currentUserId='$currentUserId'")
        Log.d(TAG, "🔍 DEBUG joinRoom(): currentUserName='$currentUserName'")

        if (currentUserId == null) {
            Log.e(TAG, "Cannot join room - user not initialized")
            return
        }

        // Clear processed messages when joining a new room
        processedMessageIds.clear()
        isInitialLoad = true
        currentRoomId = roomId

        viewModelScope.launch {
            try {
                // Get the smoker name from database
                val smokerDao = AppDatabase.getDatabase(getApplication()).smokerDao()
                val smoker = smokerDao.getSmokerByCloudUserId(currentUserId!!)

                Log.d(TAG, "🔍 DEBUG joinRoom(): smoker from DB = ${smoker?.name}")
                Log.d(TAG, "🔍 DEBUG joinRoom(): smoker cloudUserId = ${smoker?.cloudUserId}")


                val userName = smoker?.name ?: currentUserName ?: "Unknown"

                // Update the current user name to smoker name
                currentUserName = userName

                // Create or update room in local database
                val roomName = if (isSeshChat) "Session Chat" else "Public Chat"
                val roomType = if (isSeshChat) "sesh" else "public"

                val chatRoom = ChatRoom(
                    roomId = roomId,
                    roomName = roomName,
                    roomType = roomType,
                    shareCode = if (isSeshChat) roomId.removePrefix("sesh_") else null,
                    lastMessageTime = System.currentTimeMillis(),
                    unreadCount = 0,
                    isActive = true
                )

                chatDao.insertOrUpdateRoom(chatRoom)

                // IMPORTANT: Clean up stale users BEFORE joining (like video chat does)
                cleanupStaleUsers()

                // Mark user as online in this room
                updateUserPresence(true)

                // Start periodic cleanup (less aggressive than heartbeat)
                startPeriodicCleanup()

                // Load recent messages from local database first
                loadLocalMessages()

                // Start listening to messages AFTER loading local messages
                startMessageListener()

                // Start listening to online users
                startUsersListener()

                Log.d(TAG, "Joined room: $roomId with smoker name: $userName")

            } catch (e: Exception) {
                Log.e(TAG, "Error joining room", e)
            }
        }
    }

    fun leaveRoom(roomId: String) {
        if (currentUserId == null) return

        viewModelScope.launch {
            try {
                // Stop cleanup job
                cleanupJob?.cancel()
                cleanupJob = null

                // Mark user as offline immediately
                updateUserPresence(false)

                // Stop listeners
                messageListener?.remove()
                usersListener?.remove()

                currentRoomId = null
                processedMessageIds.clear()
                isInitialLoad = true
                _currentRoomMessages.value = emptyList()
                _onlineUsers.value = emptyList()

                Log.d(TAG, "Left room: $roomId")

            } catch (e: Exception) {
                Log.e(TAG, "Error leaving room", e)
            }
        }
    }

    fun editMessage(messageId: String, newMessageText: String) {
        Log.d(TAG, "====== EDIT MESSAGE START ======")
        Log.d(TAG, "Editing message: $messageId with new text: $newMessageText")

        if (newMessageText.isBlank()) {
            Log.d(TAG, "New message text is blank, returning")
            return
        }

        viewModelScope.launch {
            try {
                val editTime = System.currentTimeMillis()

                // Update in local database
                withContext(Dispatchers.IO) {
                    chatDao.updateMessageContent(messageId, newMessageText, editTime)
                    Log.d(TAG, "Local DB update complete")
                }

                // Update in Firebase
                val firestore = FirebaseFirestore.getInstance()
                val updateData = hashMapOf(
                    "message" to newMessageText,
                    "isEdited" to true,
                    "lastEditTime" to editTime
                )

                firestore.collection("chat_messages")
                    .document(messageId)
                    .update(updateData as Map<String, Any>)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Message edited in Firebase successfully!")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to edit in Firebase: ${e.message}", e)
                    }

                // Reload messages to show the update
                loadLocalMessages()

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error editing message", e)
            }
        }
        Log.d(TAG, "====== EDIT MESSAGE END ======")
    }

    private fun startPeriodicCleanup() {
        cleanupJob?.cancel()
        cleanupJob = viewModelScope.launch {
            while (isActive) {
                delay(60_000L) // Check every minute
                cleanupStaleUsers()
            }
        }
    }

    private suspend fun cleanupStaleUsers() {
        val roomId = currentRoomId ?: return

        try {
            val currentTime = System.currentTimeMillis()
            val staleThreshold = currentTime - STALE_THRESHOLD

            Log.d(TAG, "Cleaning up stale users older than ${STALE_THRESHOLD / 1000} seconds")

            // Query all users in the room
            val allUsers = firestore.collection(COLLECTION_CHAT_USERS)
                .whereEqualTo("roomId", roomId)
                .get()
                .await()

            val batch = firestore.batch()
            var staleCount = 0

            allUsers.documents.forEach { doc ->
                val lastSeen = doc.getLong("lastSeen") ?: 0L
                val isOnline = doc.getBoolean("isOnline") ?: false
                val userId = doc.getString("userId") ?: ""

                // If user is marked online but hasn't updated recently, mark them offline
                if (isOnline && lastSeen < staleThreshold) {
                    Log.d(TAG, "Marking stale user as offline: $userId (last seen: ${(currentTime - lastSeen) / 1000}s ago)")
                    batch.update(doc.reference, mapOf(
                        "isOnline" to false,
                        "isTyping" to false
                    ))
                    staleCount++
                }
            }

            if (staleCount > 0) {
                batch.commit().await()
                Log.d(TAG, "Cleaned up $staleCount stale users")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up stale users", e)
        }
    }

    fun sendMessage(roomId: String, messageText: String) {
        Log.d(TAG, "🔍 DEBUG sendMessage(): START")
        Log.d(TAG, "🔍 DEBUG sendMessage(): roomId='$roomId', message='$messageText'")
        Log.d(TAG, "🔍 DEBUG sendMessage(): currentUserId='$currentUserId'")
        Log.d(TAG, "🔍 DEBUG sendMessage(): currentUserName='$currentUserName'")

        if (messageText.isBlank()) {
            Log.d(TAG, "Message is blank, returning")
            return
        }

        if (currentUserId == null) {
            Log.e(TAG, "Cannot send message - user not initialized")
            return
        }

        viewModelScope.launch {
            try {
                // Get the smoker name from database
                val smokerDao = AppDatabase.getDatabase(getApplication()).smokerDao()
                val smoker = smokerDao.getSmokerByCloudUserId(currentUserId!!)

                Log.d(TAG, "🔍 DEBUG sendMessage(): smoker from DB = ${smoker?.name}")
                Log.d(TAG, "🔍 DEBUG sendMessage(): smoker cloudUserId = ${smoker?.cloudUserId}")

                val senderName = smoker?.name ?: currentUserName ?: "Unknown"

                val messageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()

                Log.d(TAG, "Creating message with ID: $messageId at $timestamp")
                Log.d(TAG, "Using smoker name: $senderName")

                val message = ChatMessage(
                    messageId = messageId,
                    roomId = roomId,
                    senderId = currentUserId!!,
                    senderName = senderName,
                    message = messageText,
                    timestamp = timestamp,
                    isSynced = false,
                    isDeleted = false,
                    likeCount = 0,
                    reportCount = 0
                )

                Log.d(TAG, "🔍 DEBUG sendMessage(): Created ChatMessage object:")
                Log.d(TAG, "🔍 DEBUG   - messageId: ${message.messageId}")
                Log.d(TAG, "🔍 DEBUG   - senderId: ${message.senderId}")
                Log.d(TAG, "🔍 DEBUG   - senderName: ${message.senderName}")

                // Add to processed IDs immediately to prevent duplicate processing
                processedMessageIds.add(messageId)

                // Save to local database first
                Log.d(TAG, "Inserting to local DB...")
                val localId = chatDao.insertMessage(message)
                Log.d(TAG, "Local DB insert complete, ID: $localId")

                // Update UI immediately with local message
                loadLocalMessages()

                // Send to Firebase
                Log.d(TAG, "Sending to Firebase...")
                val firebaseMessage = hashMapOf(
                    "messageId" to messageId,
                    "roomId" to roomId,
                    "senderId" to currentUserId!!,
                    "senderName" to senderName,
                    "message" to messageText,
                    "timestamp" to timestamp,
                    "isDeleted" to false,
                    "likeCount" to 0
                )

                // Use flat structure for messages (not nested under rooms)
                firestore.collection("chat_messages")
                    .document(messageId)
                    .set(firebaseMessage)
                    .addOnSuccessListener {
                        Log.d(TAG, "✅ Message sent to Firebase successfully!")
                        viewModelScope.launch {
                            // Mark as synced
                            chatDao.updateMessage(message.copy(id = localId, isSynced = true))
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "❌ Failed to send to Firebase: ${e.message}", e)
                    }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error sending message", e)
            }
        }
        Log.d(TAG, "====== SEND MESSAGE END ======")
    }

    fun deleteMessage(message: ChatMessage) {
        viewModelScope.launch {
            try {
                // Mark as deleted in local database
                chatDao.markMessageDeleted(message.id)

                // Mark as deleted in Firebase
                firestore.collection("chat_messages")
                    .document(message.messageId)
                    .update("isDeleted", true)
                    .await()

                Log.d(TAG, "Message deleted")

                // Reload messages
                loadLocalMessages()

            } catch (e: Exception) {
                Log.e(TAG, "Error deleting message", e)
            }
        }
    }

    // FIXED: Prevent deleted messages from reappearing
    private fun startMessageListener() {
        val roomId = currentRoomId ?: return

        Log.d(TAG, "====== START MESSAGE LISTENER ======")
        Log.d(TAG, "Starting message listener for room: $roomId")

        messageListener?.remove()

        messageListener = firestore.collection("chat_messages")
            .whereEqualTo("roomId", roomId)
            .orderBy("timestamp")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "❌ Message listener error", error)
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    viewModelScope.launch {
                        // Get locally deleted messages for this user
                        val locallyDeletedIds = withContext(Dispatchers.IO) {
                            chatDao.getUserDeletedMessageIds(currentUserId!!)
                        }.toSet()

                        // Get existing message IDs from local database
                        val existingLocalMessages = withContext(Dispatchers.IO) {
                            chatDao.getMessagesForRoomSync(roomId).map { it.messageId }.toSet()
                        }

                        // Process all documents from Firebase
                        val firebaseMessageIds = mutableSetOf<String>()

                        querySnapshot.documents.forEach { doc ->
                            val messageId = doc.getString("messageId") ?: doc.id
                            firebaseMessageIds.add(messageId)
                            val timestamp = doc.getLong("timestamp") ?: 0L

                            // Skip processing messages that are locally deleted
                            if (locallyDeletedIds.contains(messageId)) {
                                Log.d(TAG, "Skipping locally deleted message: $messageId")
                                return@forEach
                            }

                            val message = ChatMessage(
                                messageId = messageId,
                                roomId = roomId,
                                senderId = doc.getString("senderId") ?: "",
                                senderName = doc.getString("senderName") ?: "Unknown",
                                message = doc.getString("message") ?: "",
                                timestamp = timestamp,
                                isSynced = true,
                                isDeleted = doc.getBoolean("isDeleted") ?: false,
                                likeCount = doc.getLong("likeCount")?.toInt() ?: 0,
                                reportCount = 0
                            )

                            // Update local database
                            withContext(Dispatchers.IO) {
                                val existing = chatDao.getMessageByMessageId(message.messageId)
                                if (existing == null) {
                                    // New message, insert it
                                    chatDao.insertMessage(message)

                                    // Only notify for truly new messages (after startup)
                                    if (message.senderId != currentUserId &&
                                        !processedMessageIds.contains(message.messageId) &&
                                        !isInitialLoad &&
                                        timestamp > startupTimestamp) {

                                        Log.d(TAG, "🔔 Triggering notification for new message: $messageId")
                                        onNewMessageReceived?.invoke(message)
                                        processedMessageIds.add(message.messageId)
                                    } else {
                                        Log.d(TAG, "📝 Not triggering notification: isInitialLoad=$isInitialLoad, " +
                                                "timestamp=$timestamp, startupTimestamp=$startupTimestamp")
                                        processedMessageIds.add(message.messageId)
                                    }
                                } else {
                                    // Only update if not locally deleted and preserve local state
                                    if (!locallyDeletedIds.contains(message.messageId)) {
                                        val updatedMessage = existing.copy(
                                            message = message.message,
                                            isDeleted = message.isDeleted,
                                            likeCount = message.likeCount,
                                            isSynced = true
                                        )
                                        chatDao.updateMessage(updatedMessage)
                                        Log.d(TAG, "Updated message ${message.messageId}: likeCount=${message.likeCount}")
                                    }
                                }
                            }
                        }

                        // IMPORTANT: Clean up messages that were permanently deleted (no longer in Firebase)
                        withContext(Dispatchers.IO) {
                            val messagesToDelete = existingLocalMessages - firebaseMessageIds - locallyDeletedIds
                            messagesToDelete.forEach { messageId ->
                                Log.d(TAG, "🔧 Cleaning up permanently deleted message: $messageId")
                                chatDao.permanentlyDeleteMessageByMessageId(messageId)
                            }
                        }

                        // Mark initial load as complete
                        if (isInitialLoad) {
                            isInitialLoad = false
                            Log.d(TAG, "Initial load complete")
                        }

                        // Load and post all messages
                        loadLocalMessages()
                    }
                }
            }
        Log.d(TAG, "====== MESSAGE LISTENER SETUP COMPLETE ======")
    }

    // Add this to ChatViewModel.kt for debugging
    fun forceCleanupAllStaleUsers() {
        viewModelScope.launch {
            try {
                val currentTime = System.currentTimeMillis()
                val staleThreshold = currentTime - STALE_THRESHOLD

                // Query ALL chat_users documents
                val allUsers = firestore.collection(COLLECTION_CHAT_USERS)
                    .get()
                    .await()

                val batch = firestore.batch()
                var staleCount = 0

                allUsers.documents.forEach { doc ->
                    val lastSeen = doc.getLong("lastSeen") ?: 0L
                    val isOnline = doc.getBoolean("isOnline") ?: false

                    if (isOnline && lastSeen < staleThreshold) {
                        batch.update(doc.reference, mapOf(
                            "isOnline" to false,
                            "isTyping" to false
                        ))
                        staleCount++
                    }
                }

                if (staleCount > 0) {
                    batch.commit().await()
                    Log.d(TAG, "Force cleaned up $staleCount stale users across all rooms")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in force cleanup", e)
            }
        }
    }

    private fun startUsersListener() {
        val roomId = currentRoomId ?: return

        usersListener?.remove()

        usersListener = firestore.collection(COLLECTION_CHAT_USERS)
            .whereEqualTo("roomId", roomId)
            .whereEqualTo("isOnline", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Users listener error", error)
                    return@addSnapshotListener
                }

                snapshot?.let { querySnapshot ->
                    val currentTime = System.currentTimeMillis()
                    val staleThreshold = currentTime - STALE_THRESHOLD

                    val users = querySnapshot.documents.mapNotNull { doc ->
                        try {
                            val userId = doc.getString("userId") ?: ""
                            val lastSeen = doc.getLong("lastSeen") ?: 0L

                            // Double-check: filter out stale users and current user
                            if (userId != currentUserId && lastSeen >= staleThreshold) {
                                ChatUser(
                                    userId = userId,
                                    userName = doc.getString("userName") ?: "Unknown",
                                    roomId = roomId,
                                    isOnline = true,
                                    lastSeen = lastSeen,
                                    isTyping = doc.getBoolean("isTyping") ?: false
                                )
                            } else null
                        } catch (e: Exception) {
                            Log.e(TAG, "Error parsing user", e)
                            null
                        }
                    }

                    _onlineUsers.postValue(users)

                    // Extract typing users
                    val typingUserNames = users.filter { it.isTyping }.map { it.userName }
                    _typingUsers.postValue(typingUserNames)
                }
            }
    }

    fun updateUserPresence(isOnline: Boolean) {
        viewModelScope.launch {
            try {
                val roomId = currentRoomId ?: return@launch
                val userId = currentUserId ?: return@launch
                val userName = currentUserName ?: return@launch

                val userPresence = hashMapOf(
                    "userId" to userId,
                    "userName" to userName,
                    "roomId" to roomId,
                    "isOnline" to isOnline,
                    "lastSeen" to System.currentTimeMillis(), // Always update timestamp
                    "isTyping" to false
                )

                firestore.collection(COLLECTION_CHAT_USERS)
                    .document("${roomId}_${userId}")
                    .set(userPresence)
                    .await()

                // Update local database
                chatDao.updateUserStatus(userId, roomId, isOnline, System.currentTimeMillis())

            } catch (e: Exception) {
                Log.e(TAG, "Error updating user presence", e)
            }
        }
    }

    // Add this function to periodically update presence while in chat
    fun refreshPresence() {
        if (currentRoomId != null && currentUserId != null) {
            viewModelScope.launch {
                updateUserPresence(true)
            }
        }
    }

    private fun loadLocalMessages() {
        currentRoomId?.let { roomId ->
            viewModelScope.launch {
                val messages = withContext(Dispatchers.IO) {
                    chatDao.getMessagesForRoomSync(roomId)
                        // Don't filter out developer deleted messages - let adapter handle them
                        .filter { !it.isDeleted || it.isDeveloperDeleted }
                        .sortedBy { it.timestamp }
                }

                Log.d(TAG, "Loading ${messages.size} messages from local DB (including invisible ones)")

                _currentRoomMessages.postValue(messages)
            }
        }
    }

    fun updateTypingStatus(isTyping: Boolean) {
        viewModelScope.launch {
            try {
                val roomId = currentRoomId ?: return@launch
                val userId = currentUserId ?: return@launch

                firestore.collection(COLLECTION_CHAT_USERS)
                    .document("${roomId}_${userId}")
                    .update("isTyping", isTyping)
                    .await()

            } catch (e: Exception) {
                Log.e(TAG, "Error updating typing status", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        cleanupJob?.cancel()
        messageListener?.remove()
        usersListener?.remove()
        processedMessageIds.clear()

        // Try to mark user as offline when ViewModel is cleared
        currentRoomId?.let { roomId ->
            currentUserId?.let { userId ->
                viewModelScope.launch {
                    try {
                        firestore.collection(COLLECTION_CHAT_USERS)
                            .document("${roomId}_${userId}")
                            .update("isOnline", false)
                            .await()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error marking user offline in onCleared", e)
                    }
                }
            }
        }
    }
}
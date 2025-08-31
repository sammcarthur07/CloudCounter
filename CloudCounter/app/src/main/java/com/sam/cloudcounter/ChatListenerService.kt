package com.sam.cloudcounter

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.*

class ChatListenerService : Service() {
    companion object {
        private const val TAG = "ChatListenerService"
        private const val CHANNEL_ID = "chat_listener_service"
        private const val NOTIFICATION_ID = 12345

        fun startService(context: Context, roomId: String, roomName: String, isSeshChat: Boolean) {
            val intent = Intent(context, ChatListenerService::class.java).apply {
                putExtra("room_id", roomId)
                putExtra("room_name", roomName)
                putExtra("is_sesh_chat", isSeshChat)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, ChatListenerService::class.java))
        }
    }

    private var messageListener: ListenerRegistration? = null
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var notificationHelper: NotificationHelper
    private var lastMessageTimestamp = 0L
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val roomId = intent?.getStringExtra("room_id") ?: return START_NOT_STICKY
        val roomName = intent.getStringExtra("room_name") ?: ""
        val isSeshChat = intent.getBooleanExtra("is_sesh_chat", false)

        Log.d(TAG, "📔 Starting ChatListenerService for room: $roomId")

        // Start as foreground service
        startForeground(NOTIFICATION_ID, createForegroundNotification())

        // FIXED: Get the actual last message timestamp from database instead of current time
        serviceScope.launch {
            try {
                val chatDao = AppDatabase.getDatabase(this@ChatListenerService).chatDao()
                val messages = chatDao.getMessagesForRoomSync(roomId)
                lastMessageTimestamp = messages.maxOfOrNull { it.timestamp } ?: System.currentTimeMillis()

                Log.d(TAG, "📔 Set lastMessageTimestamp to: $lastMessageTimestamp")

                // Start listening to messages
                startListeningToMessages(roomId, roomName, isSeshChat)
            } catch (e: Exception) {
                Log.e(TAG, "📔 Error getting last message timestamp", e)
                lastMessageTimestamp = System.currentTimeMillis()
                startListeningToMessages(roomId, roomName, isSeshChat)
            }
        }

        return START_STICKY
    }

    private fun startListeningToMessages(roomId: String, roomName: String, isSeshChat: Boolean) {
        val currentUserId = auth.currentUser?.uid ?: return

        // Stop any existing listener
        messageListener?.remove()

        Log.d(TAG, "📔 Starting message listener with timestamp filter: $lastMessageTimestamp")

        // FIXED: Only listen to messages newer than the last known message
        messageListener = firestore.collection("chat_messages")
            .whereEqualTo("roomId", roomId)
            .whereGreaterThan("timestamp", lastMessageTimestamp)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "📔 Error listening to messages", error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val data = change.document.data
                        val senderId = data["senderId"] as? String ?: return@forEach
                        val senderName = data["senderName"] as? String ?: "Unknown"
                        val message = data["message"] as? String ?: ""
                        val timestamp = data["timestamp"] as? Long ?: 0L

                        // Update last timestamp
                        if (timestamp > lastMessageTimestamp) {
                            lastMessageTimestamp = timestamp
                        }

                        // FIXED: Don't show notification for own messages AND check if user is in chat
                        if (senderId != currentUserId) {
                            Log.d(TAG, "📔 New message from $senderName: $message")

                            // FIXED: Check notification preferences before showing
                            val prefs = getSharedPreferences("chat_notifications", Context.MODE_PRIVATE)
                            val roomKey = "notifications_${roomId}"
                            val defaultEnabled = isSeshChat // Sesh chat ON by default, Public chat OFF
                            val notificationsEnabled = prefs.getBoolean(roomKey, defaultEnabled)

                            if (notificationsEnabled) {
                                Log.d(TAG, "📔 Showing notification (enabled for room)")
                                // Show notification
                                notificationHelper.showChatMessageNotification(
                                    roomId = roomId,
                                    roomName = roomName,
                                    senderName = senderName,
                                    message = message,
                                    isSeshChat = isSeshChat
                                )
                            } else {
                                Log.d(TAG, "📔 Skipping notification (disabled for room)")
                            }
                        }
                    }
                }
            }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Chat Listener Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps chat notifications running in background"
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chat Active")
            .setContentText("Listening for new messages")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "📔 Stopping ChatListenerService")
        messageListener?.remove()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
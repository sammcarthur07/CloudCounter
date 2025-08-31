package com.sam.cloudcounter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

class ChatReplyReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ChatReplyReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📔 Received reply intent")

        val roomId = intent.getStringExtra("room_id") ?: return
        val roomName = intent.getStringExtra("room_name") ?: ""
        val isSeshChat = intent.getBooleanExtra("is_sesh_chat", false)
        val replyToName = intent.getStringExtra("sender_name") ?: ""

        // Get the reply text from RemoteInput
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence("key_text_reply")?.toString()

        if (replyText.isNullOrEmpty()) {
            Log.w(TAG, "📔 Reply text is empty")
            return
        }

        Log.d(TAG, "📔 Reply text: $replyText")
        Log.d(TAG, "📔 Room ID: $roomId")

        // Send the reply
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val auth = FirebaseAuth.getInstance()
                val currentUser = auth.currentUser

                if (currentUser == null) {
                    Log.e(TAG, "📔 User not signed in, cannot send reply")
                    return@launch
                }

                // Get the sender's name from database
                val db = AppDatabase.getDatabase(context)
                val smokerDao = db.smokerDao()
                val smoker = smokerDao.getSmokerByCloudUserId(currentUser.uid)
                val senderName = smoker?.name ?: currentUser.displayName ?: "Unknown"

                // Create the message
                val messageId = UUID.randomUUID().toString()
                val timestamp = System.currentTimeMillis()

                val message = ChatMessage(
                    messageId = messageId,
                    roomId = roomId,
                    senderId = currentUser.uid,
                    senderName = senderName,
                    message = replyText,
                    timestamp = timestamp,
                    isSynced = false,
                    isDeleted = false,
                    likeCount = 0,
                    reportCount = 0
                )

                // Save to local database
                val chatDao = db.chatDao()
                chatDao.insertMessage(message)

                // Send to Firebase
                val firestore = FirebaseFirestore.getInstance()
                val firebaseMessage = hashMapOf(
                    "messageId" to messageId,
                    "roomId" to roomId,
                    "senderId" to currentUser.uid,
                    "senderName" to senderName,
                    "message" to replyText,
                    "timestamp" to timestamp,
                    "isDeleted" to false,
                    "likeCount" to 0
                )

                firestore.collection("chat_messages")
                    .document(messageId)
                    .set(firebaseMessage)
                    .await()

                Log.d(TAG, "📔 ✅ Reply sent successfully")

                // Show a simple confirmation notification
                withContext(Dispatchers.Main) {
                    showReplyConfirmation(context, roomName, replyText)
                }

            } catch (e: Exception) {
                Log.e(TAG, "📔 ❌ Failed to send reply", e)
            }
        }
    }

    private fun showReplyConfirmation(context: Context, roomName: String, replyText: String) {
        val notificationManager = NotificationManagerCompat.from(context)

        val notification = NotificationCompat.Builder(context, "chat_messages")
            .setSmallIcon(android.R.drawable.ic_menu_send)
            .setContentTitle("Reply sent to $roomName")
            .setContentText("You: $replyText")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(9999, notification)
    }
}
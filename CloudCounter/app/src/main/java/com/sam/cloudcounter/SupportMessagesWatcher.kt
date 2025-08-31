package com.sam.cloudcounter

import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class SupportMessagesWatcher(
    private val context: Context
) : DefaultLifecycleObserver {

    companion object {
        private const val TAG = "SupportMessagesWatcher"
        private const val ADMIN_UID = "diY4ATkGQYhYndv2lQY4rZAUKGl2"
        private const val PREFS_NAME = "support_messages_prefs"
        private const val PREF_LAST_NOTIFIED_ID = "last_notified_id"
    }

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val notificationHelper = NotificationHelper(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var messagesListener: ListenerRegistration? = null
    private var lastNotifiedMessageId: String? = null
    private val notifiedMessageIds = mutableSetOf<String>()

    init {
        // Attach to process lifecycle
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)

        // Load last notified ID
        lastNotifiedMessageId = prefs.getString(PREF_LAST_NOTIFIED_ID, null)
        lastNotifiedMessageId?.let { notifiedMessageIds.add(it) }
    }

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        startListening()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        stopListening()
    }

    fun startListening() {
        // Only start if current user is admin
        val currentUser = auth.currentUser
        if (currentUser?.uid != ADMIN_UID) {
            Log.d(TAG, "Not admin user, skipping message monitoring")
            return
        }

        Log.d(TAG, "Starting support messages monitoring for admin")

        messagesListener = db.collection("support_messages")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(10) // Monitor last 10 messages
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Listen failed", error)
                    return@addSnapshotListener
                }

                snapshot?.documentChanges?.forEach { change ->
                    when (change.type) {
                        com.google.firebase.firestore.DocumentChange.Type.ADDED -> {
                            val message = change.document.toObject(SupportMessage::class.java)
                                .copy(id = change.document.id)

                            // Check if we should notify for this message
                            if (shouldNotifyForMessage(message)) {
                                showNotificationForMessage(message)

                                // Track this message as notified
                                notifiedMessageIds.add(message.id)
                                lastNotifiedMessageId = message.id

                                // Save to preferences
                                prefs.edit()
                                    .putString(PREF_LAST_NOTIFIED_ID, message.id)
                                    .apply()
                            }
                        }
                        else -> {}
                    }
                }
            }
    }

    fun stopListening() {
        Log.d(TAG, "Stopping support messages monitoring")
        messagesListener?.remove()
        messagesListener = null
    }

    private fun shouldNotifyForMessage(message: SupportMessage): Boolean {
        // Don't notify if inbox is currently visible
        if (SupportInboxVisibility.isVisible) {
            Log.d(TAG, "Inbox is visible, suppressing notification")
            return false
        }

        // Don't notify if we've already notified for this message
        if (notifiedMessageIds.contains(message.id)) {
            Log.d(TAG, "Already notified for message ${message.id}")
            return false
        }

        // Don't notify for very old messages (initial load)
        message.createdAt?.let { timestamp ->
            val messageAge = System.currentTimeMillis() - timestamp.toDate().time
            if (messageAge > 60_000) { // Older than 1 minute
                Log.d(TAG, "Message is too old (${messageAge}ms), not notifying")
                notifiedMessageIds.add(message.id) // Mark as seen
                return false
            }
        }

        return true
    }

    private fun showNotificationForMessage(message: SupportMessage) {
        Log.d(TAG, "Showing notification for new support message from ${message.userEmail}")

        notificationHelper.showSupportMessageNotification(
            message = message
        )
    }

    fun clearNotifiedMessages() {
        notifiedMessageIds.clear()
        lastNotifiedMessageId = null
        prefs.edit().remove(PREF_LAST_NOTIFIED_ID).apply()
    }
}
package com.sam.cloudcounter

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.text.DateFormat
import java.util.Date
import android.util.Log
import androidx.core.app.RemoteInput

@Suppress("SpellCheckingInspection")
class NotificationHelper(private val context: Context) {
    companion object {
        // Existing channels
        const val CHANNEL_ID     = "activity_updates"
        private const val CHANNEL_NAME   = "Activity Updates"
        private const val CHANNEL_DESC   = "Notifications for new activity logs"

        // Goal notification channels
        private const val GOAL_PROGRESS_CHANNEL_ID = "goal_progress"
        private const val GOAL_PROGRESS_CHANNEL_NAME = "Goal Progress"
        private const val GOAL_PROGRESS_CHANNEL_DESC = "Notifications for goal progress updates"

        private const val GOAL_COMPLETION_CHANNEL_ID = "goal_completion"
        private const val GOAL_COMPLETION_CHANNEL_NAME = "Goal Completion"
        private const val GOAL_COMPLETION_CHANNEL_DESC = "Notifications when goals are completed"

        // Support messages channel
        private const val SUPPORT_CHANNEL_ID = "support_messages"
        private const val SUPPORT_CHANNEL_NAME = "Support Messages"
        private const val SUPPORT_CHANNEL_DESC = "Notifications for new support messages"
        private const val SUPPORT_NOTIF_ID_BASE = 5000

        // Chat notification channel
        private const val CHAT_MESSAGE_CHANNEL_ID = "chat_messages"
        private const val CHAT_MESSAGE_CHANNEL_NAME = "Chat Messages"
        private const val CHAT_MESSAGE_CHANNEL_DESC = "Notifications for new chat messages"

        private const val NOTIF_ID_BASE  = 1000
        private const val GOAL_NOTIF_ID_BASE = 2000
        private const val CHAT_NOTIF_ID_BASE = 3000

        const val ACTION_ADD_ENTRY         = "com.sam.cloudcounter.ACTION_ADD_ENTRY"
        const val ACTION_REMOVE_LAST_ENTRY = "com.sam.cloudcounter.ACTION_REMOVE_LAST_ENTRY"
        const val ACTION_REWIND_SESSION    = "com.sam.cloudcounter.ACTION_REWIND_SESSION"
        const val EXTRA_TYPE               = "extra_activity_type"

        const val EXTRA_SESSION_SHARE_CODE = "extra_session_share_code"
        const val EXTRA_SMOKER_CLOUD_ID    = "extra_smoker_cloud_id"
    }

    private val messageNotifications = mutableListOf<String>()

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Existing channel
            val activityChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = CHANNEL_DESC }
            notificationManager.createNotificationChannel(activityChannel)

            // Goal progress channel
            val progressChannel = NotificationChannel(
                GOAL_PROGRESS_CHANNEL_ID,
                GOAL_PROGRESS_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = GOAL_PROGRESS_CHANNEL_DESC }
            notificationManager.createNotificationChannel(progressChannel)

            // Goal completion channel
            val completionChannel = NotificationChannel(
                GOAL_COMPLETION_CHANNEL_ID,
                GOAL_COMPLETION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = GOAL_COMPLETION_CHANNEL_DESC }
            notificationManager.createNotificationChannel(completionChannel)

            // Chat message channel with sound
            val chatChannel = NotificationChannel(
                CHAT_MESSAGE_CHANNEL_ID,
                CHAT_MESSAGE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHAT_MESSAGE_CHANNEL_DESC
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
            }
            notificationManager.createNotificationChannel(chatChannel)

            // Support messages channel (NEW)
            val supportChannel = NotificationChannel(
                SUPPORT_CHANNEL_ID,
                SUPPORT_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = SUPPORT_CHANNEL_DESC
                enableVibration(true)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            notificationManager.createNotificationChannel(supportChannel)
        }
    }



    fun clearChatNotifications(roomId: String) {
        // Clear notifications for a specific room
        NotificationManagerCompat.from(context)
            .cancel(CHAT_NOTIF_ID_BASE + roomId.hashCode())

        // Clear message list
        messageNotifications.clear()
    }

    fun showMessageLikedNotification(
        likerName: String,
        messagePreview: String,
        roomId: String,
        roomName: String
    ) {
        Log.d("NotificationHelper", "📔 showMessageLikedNotification called")
        Log.d("NotificationHelper", "📔 Liker: $likerName")

        val title = "Yay! $likerName has liked your message"
        val body = if (messagePreview.length > 50) {
            "\"${messagePreview.take(47)}...\""
        } else {
            "\"$messagePreview\""
        }

        // Create intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat", true)
            putExtra("room_id", roomId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            roomId.hashCode() + 1000, // Different ID from regular chat notifications
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHAT_MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.star_on) // Star icon for likes
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .build()

        if (hasNotificationPermission()) {
            try {
                NotificationManagerCompat.from(context)
                    .notify(4000 + roomId.hashCode(), notification) // Different ID range for like notifications
                Log.d("NotificationHelper", "📔 ✅ Like notification posted successfully")
            } catch (e: Exception) {
                Log.e("NotificationHelper", "📔 ❌ Failed to post like notification", e)
            }
        }
    }

    fun showSupportMessageNotification(message: SupportMessage) {
        val title = "New support message"
        val body = buildString {
            append(message.userEmail)
            append(": ")
            val preview = if (message.message.length > 80) {
                message.message.take(77) + "..."
            } else {
                message.message
            }
            append(preview)
        }

        // Create intent to open the app at the About/Inbox tab
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 7) // 8th tab (index 7)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            SUPPORT_NOTIF_ID_BASE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, SUPPORT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .build()

        if (hasNotificationPermission()) {
            try {
                NotificationManagerCompat.from(context)
                    .notify(SUPPORT_NOTIF_ID_BASE + message.id.hashCode(), notification)
            } catch (e: Exception) {
                Log.e("NotificationHelper", "Failed to post support message notification", e)
            }
        }
    }

    fun showActivityNotification(
        activityType: ActivityType,
        lastTimestamp: Long?,
        conesSinceLastBowl: Int?,
        sessionShareCode: String? = null,
        smokerCloudUserId: String? = null,
        justAdded: Boolean = false,
        addedAt: Long? = null,
        lastSmokerName: String? = null
    ) {
        // If this is not a "just added" confirmation and there is no last activity
        // record (timestamp is null), then do not show the notification at all.
        if (!justAdded && lastTimestamp == null) {
            return
        }

        val title = if (justAdded) {
            when (activityType) {
                ActivityType.JOINT -> "Add a Joint"
                ActivityType.CONE -> "Add a Cone"
                ActivityType.BOWL -> "Add a Bowl"
                ActivityType.SESSION_SUMMARY -> "Session Summary"
            }
        } else {
            when (activityType) {
                ActivityType.JOINT           -> "Last Joint"
                ActivityType.CONE            -> "Last Cone"
                ActivityType.BOWL            -> "Last Bowl"
                ActivityType.SESSION_SUMMARY -> "Session Summary"
            }
        }

        val body = if (justAdded && addedAt != null) {
            "Last add success - ${DateFormat.getDateTimeInstance().format(Date(addedAt))}"
        } else {
            // Normal display logic - include smoker name
            val timeText = lastTimestamp
                ?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
                ?: "No record"

            val smokerText = lastSmokerName?.let { " - $it" } ?: ""

            if (conesSinceLastBowl != null) {
                "$timeText$smokerText — $conesSinceLastBowl since last bowl"
            } else {
                "$timeText$smokerText"
            }
        }

        // Check if session is active (to show/hide rewind button)
        val prefs = context.getSharedPreferences("sesh", Context.MODE_PRIVATE)
        val sessionActive = prefs.getBoolean("sessionActive", false)

        // "–" action (remove) on the left
        val removeIntent = Intent(context, ActivityNotificationReceiver::class.java).apply {
            action = ACTION_REMOVE_LAST_ENTRY
            putExtra(EXTRA_TYPE, activityType)
            sessionShareCode?.let { putExtra(EXTRA_SESSION_SHARE_CODE, it) }
            smokerCloudUserId?.let { putExtra(EXTRA_SMOKER_CLOUD_ID, it) }
        }
        val pendingRemove = PendingIntent.getBroadcast(
            context,
            activityType.ordinal,
            removeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "⏪" action (rewind) in the middle
        val rewindIntent = Intent(context, ActivityNotificationReceiver::class.java).apply {
            action = ACTION_REWIND_SESSION
            putExtra(EXTRA_TYPE, activityType)
            sessionShareCode?.let { putExtra(EXTRA_SESSION_SHARE_CODE, it) }
            smokerCloudUserId?.let { putExtra(EXTRA_SMOKER_CLOUD_ID, it) }
        }
        val pendingRewind = PendingIntent.getBroadcast(
            context,
            activityType.ordinal + 200,
            rewindIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // "+" action (add) on the right
        val addIntent = Intent(context, ActivityNotificationReceiver::class.java).apply {
            action = ACTION_ADD_ENTRY
            putExtra(EXTRA_TYPE, activityType)
            sessionShareCode?.let { putExtra(EXTRA_SESSION_SHARE_CODE, it) }
            smokerCloudUserId?.let { putExtra(EXTRA_SMOKER_CLOUD_ID, it) }
        }
        val pendingAdd = PendingIntent.getBroadcast(
            context,
            activityType.ordinal + 100,
            addIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notifBuilder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setOngoing(true)
            .addAction(android.R.drawable.ic_menu_delete, "–", pendingRemove)

        // Only add rewind button if session is active
        if (sessionActive) {
            notifBuilder.addAction(android.R.drawable.ic_media_rew, "⏪", pendingRewind)
        }

        notifBuilder.addAction(android.R.drawable.ic_input_add, "+", pendingAdd)

        val notif = notifBuilder.build()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            == PackageManager.PERMISSION_GRANTED
        ) {
            NotificationManagerCompat.from(context)
                .notify(NOTIF_ID_BASE + activityType.ordinal, notif)
        }
    }

    // Goal notification methods remain the same
    fun showGoalProgressNotification(goal: Goal, progressPercentage: Int) {
        if (!goal.progressNotificationsEnabled) return

        val title = "Goal Progress: ${progressPercentage}%"
        val body = buildString {
            append(getGoalDescription(goal))
            append("\n")
            append("Progress: ${goal.currentJoints + goal.currentCones + goal.currentBowls} / ")
            append("${goal.targetJoints + goal.targetCones + goal.targetBowls}")
        }

        val notification = NotificationCompat.Builder(context, GOAL_PROGRESS_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()

        if (hasNotificationPermission()) {
            NotificationManagerCompat.from(context)
                .notify(GOAL_NOTIF_ID_BASE + goal.goalId.toInt(), notification)
        }
    }

    fun showInitialGoalNotification(goal: Goal) {
        // Call the main function with isSilentUpdate = false for initial notifications
        showPersistentGoalNotification(goal, isSilentUpdate = false)
    }

    fun showGoalCompletionNotification(goal: Goal) {
        // For completion, always make sound (isSilentUpdate = false)
        showPersistentGoalNotification(goal, isSilentUpdate = false)
    }

    fun showChatMessageNotification(
        roomId: String,
        roomName: String,
        senderName: String,
        message: String,
        isSeshChat: Boolean
    ) {
        Log.d("NotificationHelper", "📔 showChatMessageNotification called")
        Log.d("NotificationHelper", "📔 Room: $roomName, Sender: $senderName")
        Log.d("NotificationHelper", "📔 Message: $message")

        // Build the notification title based on room type
        val title = if (isSeshChat) {
            "Session Chat - $roomName"
        } else {
            "Public Chat"
        }

        // Add to message list for inbox style
        val messageText = "$senderName: $message"
        messageNotifications.add(messageText)

        // Keep only last 5 messages
        if (messageNotifications.size > 5) {
            messageNotifications.removeAt(0)
        }

        // Create intent to open the app
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat", true)
            putExtra("room_id", roomId)
            putExtra("is_sesh_chat", isSeshChat)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            roomId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create RemoteInput for inline reply
        val remoteInput = RemoteInput.Builder("key_text_reply")
            .setLabel("Reply to $senderName")
            .build()

        // Create reply action PendingIntent
        val replyIntent = Intent(context, ChatReplyReceiver::class.java).apply {
            action = "com.sam.cloudcounter.REPLY_ACTION"
            putExtra("room_id", roomId)
            putExtra("room_name", roomName)
            putExtra("is_sesh_chat", isSeshChat)
            putExtra("sender_name", senderName)
        }

        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            roomId.hashCode() + 100,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE // Must be MUTABLE for reply
        )

        // Create reply action
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .build()

        // Build notification with inbox style for multiple messages and reply action
        val notificationBuilder = NotificationCompat.Builder(context, CHAT_MESSAGE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(messageText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 250, 250, 250))
            .addAction(replyAction) // Add the reply action

        // Add inbox style if multiple messages
        if (messageNotifications.size > 1) {
            val inboxStyle = NotificationCompat.InboxStyle()
                .setBigContentTitle(title)
                .setSummaryText("${messageNotifications.size} new messages")

            messageNotifications.forEach { msg ->
                inboxStyle.addLine(msg)
            }

            notificationBuilder.setStyle(inboxStyle)
        }

        val notification = notificationBuilder.build()

        val hasPermission = hasNotificationPermission()
        Log.d("NotificationHelper", "📔 Has notification permission: $hasPermission")

        if (hasPermission) {
            try {
                NotificationManagerCompat.from(context)
                    .notify(CHAT_NOTIF_ID_BASE + roomId.hashCode(), notification)
                Log.d("NotificationHelper", "📔 ✅ Notification with reply posted successfully")
            } catch (e: Exception) {
                Log.e("NotificationHelper", "📔 ❌ Failed to post notification", e)
            }
        } else {
            Log.w("NotificationHelper", "📔 ⚠️ No notification permission")
        }
    }

    private fun getGoalDescription(goal: Goal): String {
        val items = mutableListOf<String>()
        if (goal.targetJoints > 0) items.add("${goal.targetJoints} joints")
        if (goal.targetCones > 0) items.add("${goal.targetCones} cones")
        if (goal.targetBowls > 0) items.add("${goal.targetBowls} bowls")

        return when(goal.goalType) {
            GoalType.CURRENT_SESSION -> "Current session: ${items.joinToString(", ")}"
            GoalType.ALL_SESSIONS -> "All sessions: ${items.joinToString(", ")}"
            GoalType.TIME_BASED -> "${goal.timeDuration} ${goal.timeUnit?.name?.lowercase()}: ${items.joinToString(", ")}"
        }
    }

    fun showPersistentGoalNotification(goal: Goal, isSilentUpdate: Boolean = true) {
        if (!goal.notificationsEnabled) return

        val notificationId = GOAL_NOTIF_ID_BASE + goal.goalId.toInt()

        // Calculate progress
        val totalTarget = goal.targetJoints + goal.targetCones + goal.targetBowls
        val totalCurrent = goal.currentJoints + goal.currentCones + goal.currentBowls
        val progressPercentage = if (totalTarget > 0) {
            (totalCurrent * 100 / totalTarget).coerceAtMost(999)
        } else 0

        // Determine if this is a significant event (not silent)
        val isSignificantEvent = !isSilentUpdate || progressPercentage == 100

        // Build goal name or description
        val goalDisplayName = if (goal.goalName.isBlank()) {
            // Build name from targets if no custom name
            val targetParts = mutableListOf<String>()
            if (goal.targetCones > 0) targetParts.add("${goal.targetCones} Cones")
            if (goal.targetJoints > 0) targetParts.add("${goal.targetJoints} Joints")
            if (goal.targetBowls > 0) targetParts.add("${goal.targetBowls} Bowls")
            targetParts.joinToString(", ")
        } else {
            goal.goalName
        }

        // Format Line 1: Type details with name
        val line1 = when (goal.goalType) {
            GoalType.TIME_BASED -> {
                val now = System.currentTimeMillis()
                val endTime = goal.lastResetAt + getGoalDurationMillis(goal)
                val startDate = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(goal.lastResetAt))
                val endDate = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(endTime))

                val periodText = when (goal.timeUnit) {
                    TimeUnit.DAY -> "Daily"
                    TimeUnit.WEEK -> "Weekly"
                    TimeUnit.FORTNIGHT -> "Fortnightly"
                    TimeUnit.MONTH -> "Monthly"
                    TimeUnit.YEAR -> "Yearly"
                    else -> "${goal.timeDuration} ${goal.timeUnit?.name?.lowercase()}"
                }
                "$periodText ($startDate-$endDate) - $goalDisplayName"
            }
            GoalType.CURRENT_SESSION -> {
                if (goal.isPaused && goal.sessionEndDate != null) {
                    val startDate = goal.sessionStartDate?.let {
                        DateFormat.getDateInstance(DateFormat.SHORT).format(Date(it))
                    } ?: ""
                    val endDate = DateFormat.getDateInstance(DateFormat.SHORT).format(Date(goal.sessionEndDate))
                    "Past Sesh ($startDate-$endDate) - $goalDisplayName"
                } else {
                    "Current Sesh - $goalDisplayName"
                }
            }
            GoalType.ALL_SESSIONS -> {
                val daysSinceStart = ((System.currentTimeMillis() - goal.startedAt) / (1000 * 60 * 60 * 24)).toInt() + 1
                "All sessions (Day $daysSinceStart) - $goalDisplayName"
            }
        }

        // Format Line 2: Progress counts and percentage
        val progressItems = mutableListOf<String>()
        if (goal.targetJoints > 0) progressItems.add("${goal.currentJoints}/${goal.targetJoints} joints")
        if (goal.targetCones > 0) progressItems.add("${goal.currentCones}/${goal.targetCones} cones")
        if (goal.targetBowls > 0) progressItems.add("${goal.currentBowls}/${goal.targetBowls} bowls")

        val line2 = if (progressPercentage >= 100 && goal.completionNotificationsEnabled) {
            "${progressItems.joinToString(", ")} - ${progressPercentage}% - Goal Complete!"
        } else if (progressPercentage >= 75 && progressPercentage < 100) {
            "${progressItems.joinToString(", ")} - ${progressPercentage}% - Almost there!"
        } else {
            "${progressItems.joinToString(", ")} - ${progressPercentage}%"
        }

        // Create intent to open Goals tab
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 6) // Goals tab index
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Choose appropriate channel and priority based on whether this is a silent update
        val channelId = when {
            progressPercentage >= 100 -> GOAL_COMPLETION_CHANNEL_ID
            !isSilentUpdate -> GOAL_PROGRESS_CHANNEL_ID
            else -> GOAL_PROGRESS_CHANNEL_ID
        }

        // Build notification - FIX: Don't use BigTextStyle with duplicate content
        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(if (progressPercentage >= 100) android.R.drawable.star_on else android.R.drawable.ic_menu_info_details)
            .setContentTitle(line1)
            .setContentText(line2)
            .setPriority(if (isSilentUpdate && progressPercentage < 100) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(false)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)

        // Only use BigTextStyle if the content is actually long
        if (line1.length + line2.length > 80) {
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle()
                .bigText("$line1\n$line2"))
        }

        // Set sound/vibration based on whether this is a silent update
        if (isSilentUpdate && progressPercentage < 100) {
            notificationBuilder
                .setSilent(true)
                .setNotificationSilent()
        } else if (isSignificantEvent) {
            notificationBuilder
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setVibrate(longArrayOf(0, 250))
        }

        val notification = notificationBuilder.build()
        notification.flags = notification.flags or android.app.Notification.FLAG_ONLY_ALERT_ONCE

        if (hasNotificationPermission()) {
            NotificationManagerCompat.from(context)
                .notify(notificationId, notification)
        }
    }

    fun cancelGoalNotification(goalId: Long) {
        val notificationId = GOAL_NOTIF_ID_BASE + goalId.toInt()
        NotificationManagerCompat.from(context).cancel(notificationId)
        Log.d("NotificationHelper", "Cancelled notification for goal $goalId")
    }

    fun cancelAllGoalNotifications() {
        // Cancel all possible goal notifications (assuming max 1000 goals)
        for (i in 0..999) {
            NotificationManagerCompat.from(context).cancel(GOAL_NOTIF_ID_BASE + i)
        }
    }

    private fun getGoalDurationMillis(goal: Goal): Long {
        val baseMillis = when (goal.timeUnit) {
            TimeUnit.MINUTE -> 60000L
            TimeUnit.HOUR -> 3600000L
            TimeUnit.DAY -> 86400000L
            TimeUnit.WEEK -> 604800000L
            TimeUnit.FORTNIGHT -> 1209600000L
            TimeUnit.MONTH -> 2592000000L
            TimeUnit.YEAR -> 31536000000L
            null -> 0L
        }
        return baseMillis * (goal.timeDuration ?: 1)
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
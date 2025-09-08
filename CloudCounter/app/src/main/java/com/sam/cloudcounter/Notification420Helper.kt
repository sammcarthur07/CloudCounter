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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

class Notification420Helper(private val context: Context) {
    
    companion object {
        private const val TAG = "Notification420Helper"
        
        private const val CHANNEL_420_ID = "420_notifications"
        private const val CHANNEL_420_NAME = "420 Notifications"
        private const val CHANNEL_420_DESC = "Notifications for 4:20 times around the world"
        
        private const val CHANNEL_420_COUNTDOWN_ID = "420_countdown"
        private const val CHANNEL_420_COUNTDOWN_NAME = "420 Countdown"
        private const val CHANNEL_420_COUNTDOWN_DESC = "City rotation countdown to 4:20"
        
        private const val NOTIF_ID_420_MORNING = 420
        private const val NOTIF_ID_420_AFTERNOON = 1620
        private const val NOTIF_ID_COUNTDOWN = 42000
        private const val NOTIF_ID_ROTATION_BASE = 42100
    }
    
    init {
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            val channel420 = NotificationChannel(
                CHANNEL_420_ID,
                CHANNEL_420_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_420_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 500)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            notificationManager.createNotificationChannel(channel420)
            
            val channelCountdown = NotificationChannel(
                CHANNEL_420_COUNTDOWN_ID,
                CHANNEL_420_COUNTDOWN_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_420_COUNTDOWN_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 100, 250)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION), null)
            }
            notificationManager.createNotificationChannel(channelCountdown)
        }
    }
    
    fun show420Notification(isMorning: Boolean) {
        val title = "It's 4:20!"
        val text = if (isMorning) {
            "Good morning! It's 4:20 AM in your location!"
        } else {
            "It's 4:20 PM in your location!"
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 7)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            if (isMorning) NOTIF_ID_420_MORNING else NOTIF_ID_420_AFTERNOON,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_420_ID)
            .setSmallIcon(android.R.drawable.star_big_on)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 500, 250, 500, 250, 500))
            .build()
        
        if (hasNotificationPermission()) {
            try {
                NotificationManagerCompat.from(context)
                    .notify(if (isMorning) NOTIF_ID_420_MORNING else NOTIF_ID_420_AFTERNOON, notification)
                Log.d(TAG, "420 notification posted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post 420 notification", e)
            }
        }
    }
    
    fun show5MinBeforeNotification(isMorning: Boolean) {
        val title = "4:20 approaching!"
        val text = if (isMorning) {
            "4:20 AM is in 5 minutes! Get ready!"
        } else {
            "4:20 PM is in 5 minutes! Get ready!"
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 7)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID_COUNTDOWN,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notification = NotificationCompat.Builder(context, CHANNEL_420_ID)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setVibrate(longArrayOf(0, 250, 100, 250))
            .build()
        
        if (hasNotificationPermission()) {
            try {
                NotificationManagerCompat.from(context)
                    .notify(NOTIF_ID_COUNTDOWN, notification)
                Log.d(TAG, "5-min before notification posted successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post 5-min before notification", e)
            }
        }
    }
    
    fun showRotatingCityNotification(
        cityName: String,
        secondsUntil420: Int,
        is420Now: Boolean,
        isUserCity: Boolean
    ) {
        val title: String
        val text: String
        
        when {
            is420Now -> {
                title = "It's 4:20 in $cityName!"
                text = if (isUserCity) {
                    "It's 4:20 right now in your location!"
                } else {
                    "It's currently 4:20 in $cityName!"
                }
            }
            secondsUntil420 <= 60 -> {
                val seconds = secondsUntil420
                title = "4:20 in $seconds seconds!"
                text = if (isUserCity) {
                    "Get ready! 4:20 is approaching in your location - $cityName"
                } else {
                    "It will be 4:20 in $cityName in just $seconds seconds!"
                }
            }
            secondsUntil420 <= 120 -> {
                val minutes = secondsUntil420 / 60
                val seconds = secondsUntil420 % 60
                title = "4:20 in ${minutes}m ${seconds}s"
                text = if (isUserCity) {
                    "Your local 4:20 is coming up in $cityName!"
                } else {
                    "Next 4:20 approaching in $cityName!"
                }
            }
            else -> {
                val hours = secondsUntil420 / 3600
                val minutes = (secondsUntil420 % 3600) / 60
                
                title = if (hours > 0) {
                    "4:20 in ${hours}h ${minutes}m"
                } else {
                    "4:20 in ${minutes}m"
                }
                
                text = if (isUserCity) {
                    "Your next 4:20 in $cityName"
                } else {
                    "Next 4:20 will be in $cityName"
                }
            }
        }
        
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_tab", 7)
        }
        
        val notifId = NOTIF_ID_ROTATION_BASE + cityName.hashCode()
        
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val notificationBuilder = NotificationCompat.Builder(context, CHANNEL_420_COUNTDOWN_ID)
            .setSmallIcon(if (is420Now) android.R.drawable.star_big_on else android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(if (is420Now || secondsUntil420 <= 60) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(false)
        
        if (is420Now || (secondsUntil420 <= 20 && isUserCity)) {
            notificationBuilder
                .setVibrate(longArrayOf(0, 500, 250, 500))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
        } else if (secondsUntil420 <= 60) {
            notificationBuilder
                .setVibrate(longArrayOf(0, 250))
        }
        
        if (hasNotificationPermission()) {
            try {
                NotificationManagerCompat.from(context)
                    .notify(NOTIF_ID_COUNTDOWN, notificationBuilder.build())
                Log.d(TAG, "City rotation notification updated: $cityName")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to post city rotation notification", e)
            }
        }
    }
    
    fun cancelCountdownNotification() {
        NotificationManagerCompat.from(context).cancel(NOTIF_ID_COUNTDOWN)
    }
    
    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }
}
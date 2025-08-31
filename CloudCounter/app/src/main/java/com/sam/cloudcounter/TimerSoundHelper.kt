package com.sam.cloudcounter

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class TimerSoundHelper(private val context: Context) {
    companion object {
        private const val TAG = "TimerSoundHelper"
        private const val PREF_NAME = "timer_sound_prefs"
        private const val PREF_SOUND_ENABLED = "sound_enabled"
        private const val PREF_SOUND_URI = "sound_uri"
        const val CHANNEL_ID = "timer_sounds"
        private const val CHANNEL_NAME = "Timer Sounds"
        private const val CHANNEL_DESC = "Sounds for countdown timers"
        private const val NOTIFICATION_ID = 9999
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Delete existing channel to ensure updates take effect
            notificationManager.deleteNotificationChannel(CHANNEL_ID)

            val soundUri = getSelectedSoundUri()

            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT  // CHANGED FROM IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                setSound(soundUri, AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build())
                enableVibration(false)
                setShowBadge(false)
                enableLights(false)
            }

            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Created notification channel with sound: $soundUri")
        }
    }

    fun isSoundEnabled(): Boolean {
        return prefs.getBoolean(PREF_SOUND_ENABLED, true)
    }

    fun setSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(PREF_SOUND_ENABLED, enabled).apply()
        Log.d(TAG, "Sound ${if (enabled) "enabled" else "disabled"}")
    }

    fun getSelectedSoundUri(): Uri {
        val savedUri = prefs.getString(PREF_SOUND_URI, null)
        return if (savedUri != null) {
            try {
                Uri.parse(savedUri)
            } catch (e: Exception) {
                Log.w(TAG, "Invalid saved URI, using default")
                getDefaultTimerSound()
            }
        } else {
            getDefaultTimerSound()
        }
    }

    private fun getDefaultTimerSound(): Uri {
        return try {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: Settings.System.DEFAULT_NOTIFICATION_URI
        } catch (e: Exception) {
            Log.w(TAG, "Could not get notification sound, using system default")
            Settings.System.DEFAULT_NOTIFICATION_URI
        }
    }

    fun setSelectedSoundUri(uri: Uri) {
        prefs.edit().putString(PREF_SOUND_URI, uri.toString()).apply()
        Log.d(TAG, "Sound URI updated: $uri")

        // Recreate the notification channel with new sound
        createNotificationChannel()
    }

    fun playTimerSound() {
        if (!isSoundEnabled()) {
            Log.d(TAG, "Sound disabled, not playing")
            return
        }

        try {
            // Cancel any existing timer notification
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)

            // Use a notification to play the sound (respects channel settings)
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Timer")
                .setContentText("Timer reached zero")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)  // CHANGED FROM PRIORITY_HIGH
                .setAutoCancel(true)
                .build()

            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)

            Log.d(TAG, "Timer notification sent")

            // Auto-dismiss the notification after a reasonable time (e.g., 5 seconds)
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            }, 5000) // 5 seconds should be enough for most sounds

        } catch (e: Exception) {
            Log.e(TAG, "Failed to play timer sound", e)
        }
    }

    fun cleanup() {
        // Cancel any remaining notifications
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
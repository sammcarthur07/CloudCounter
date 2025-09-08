package com.sam.cloudcounter

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar
import java.util.TimeZone

class Notification420Receiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "Notification420Receiver"
        const val ACTION_420_NOTIFICATION = "com.sam.cloudcounter.ACTION_420_NOTIFICATION"
        const val EXTRA_IS_MORNING = "extra_is_morning"
        const val EXTRA_NOTIFICATION_TYPE = "extra_notification_type"
        
        const val TYPE_5MIN_BEFORE = "5_min_before"
        const val TYPE_AT_420 = "at_420"
        
        private const val REQUEST_CODE_MORNING_5MIN = 4200
        private const val REQUEST_CODE_MORNING_420 = 4201
        private const val REQUEST_CODE_AFTERNOON_5MIN = 4202
        private const val REQUEST_CODE_AFTERNOON_420 = 4203
        
        fun schedule420Notifications(context: Context) {
            val prefs = context.getSharedPreferences("420_notifications", Context.MODE_PRIVATE)
            val morningEnabled = prefs.getBoolean("morning_enabled", false)
            val afternoonEnabled = prefs.getBoolean("afternoon_enabled", false)
            val fiveMinBeforeEnabled = prefs.getBoolean("five_min_before_enabled", false)
            
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            
            if (morningEnabled) {
                scheduleNotification(context, alarmManager, 4, 20, true, false)
                if (fiveMinBeforeEnabled) {
                    scheduleNotification(context, alarmManager, 4, 15, true, true)
                }
            } else {
                cancelNotification(context, alarmManager, REQUEST_CODE_MORNING_420)
                cancelNotification(context, alarmManager, REQUEST_CODE_MORNING_5MIN)
            }
            
            if (afternoonEnabled) {
                scheduleNotification(context, alarmManager, 16, 20, false, false)
                if (fiveMinBeforeEnabled) {
                    scheduleNotification(context, alarmManager, 16, 15, false, true)
                }
            } else {
                cancelNotification(context, alarmManager, REQUEST_CODE_AFTERNOON_420)
                cancelNotification(context, alarmManager, REQUEST_CODE_AFTERNOON_5MIN)
            }
        }
        
        private fun scheduleNotification(
            context: Context,
            alarmManager: AlarmManager,
            hour: Int,
            minute: Int,
            isMorning: Boolean,
            is5MinBefore: Boolean
        ) {
            val intent = Intent(context, Notification420Receiver::class.java).apply {
                action = ACTION_420_NOTIFICATION
                putExtra(EXTRA_IS_MORNING, isMorning)
                putExtra(EXTRA_NOTIFICATION_TYPE, if (is5MinBefore) TYPE_5MIN_BEFORE else TYPE_AT_420)
            }
            
            val requestCode = when {
                isMorning && is5MinBefore -> REQUEST_CODE_MORNING_5MIN
                isMorning && !is5MinBefore -> REQUEST_CODE_MORNING_420
                !isMorning && is5MinBefore -> REQUEST_CODE_AFTERNOON_5MIN
                else -> REQUEST_CODE_AFTERNOON_420
            }
            
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pendingIntent
            )
            
            Log.d(TAG, "Scheduled ${if (is5MinBefore) "5-min before" else "at"} 420 notification for $hour:$minute")
        }
        
        private fun cancelNotification(
            context: Context,
            alarmManager: AlarmManager,
            requestCode: Int
        ) {
            val intent = Intent(context, Notification420Receiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
            Log.d(TAG, "Cancelled notification with request code $requestCode")
        }
    }
    
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_420_NOTIFICATION) return
        
        val isMorning = intent.getBooleanExtra(EXTRA_IS_MORNING, true)
        val notificationType = intent.getStringExtra(EXTRA_NOTIFICATION_TYPE) ?: TYPE_AT_420
        
        Log.d(TAG, "Received 420 notification trigger - Morning: $isMorning, Type: $notificationType")
        
        when (notificationType) {
            TYPE_5MIN_BEFORE -> handle5MinBeforeNotification(context, isMorning)
            TYPE_AT_420 -> handleAt420Notification(context, isMorning)
        }
    }
    
    private fun handle5MinBeforeNotification(context: Context, isMorning: Boolean) {
        val notificationHelper = Notification420Helper(context)
        notificationHelper.show5MinBeforeNotification(isMorning)
    }
    
    private fun handleAt420Notification(context: Context, isMorning: Boolean) {
        val notificationHelper = Notification420Helper(context)
        notificationHelper.show420Notification(isMorning)
    }
}
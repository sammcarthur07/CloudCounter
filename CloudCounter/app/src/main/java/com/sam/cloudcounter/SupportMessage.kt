package com.sam.cloudcounter

import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.*


data class SupportMessage(
    val id: String = "",
    val message: String = "",
    val userUid: String = "",
    val userEmail: String = "",
    val userDisplayName: String? = null,
    val createdAt: Timestamp? = null,
    val platform: String = "android",
    val appVersion: String = "",
    val deviceModel: String = "",
    val osVersion: String = "",
    val userLocation: Map<String, Any>? = null
) {
    fun getFormattedTimestamp(): String {
        return createdAt?.let {
            val now = System.currentTimeMillis()
            val messageTime = it.toDate().time
            val diff = now - messageTime

            when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000} minutes ago"
                diff < 86400_000 -> "${diff / 3600_000} hours ago"
                diff < 172800_000 -> "Yesterday"
                else -> {
                    val format = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                    format.format(it.toDate())
                }
            }
        } ?: "Unknown"
    }

    fun getLocationString(): String? {
        return userLocation?.let {
            val city = it["city"] as? String ?: ""
            val country = it["country"] as? String ?: ""
            if (city.isNotEmpty() || country.isNotEmpty()) {
                "$city, $country".trim().trim(',')
            } else {
                null
            }
        }
    }
}
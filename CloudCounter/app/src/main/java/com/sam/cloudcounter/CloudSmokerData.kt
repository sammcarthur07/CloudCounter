package com.sam.cloudcounter

/**
 * Data class representing a cloud smoker profile stored in Firestore
 */
data class CloudSmokerData(
    val userId: String = "",
    val name: String = "",
    val shareCode: String = "",
    val totalCones: Int = 0,
    val totalJoints: Int = 0,
    val totalBowls: Int = 0,
    val lastActivity: Long = 0L,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val passwordHash: String? = null  // New field for password protection
)

/**
 * Data class for search results
 */
data class CloudSmokerSearchResult(
    val userId: String,
    val name: String,
    val shareCode: String,
    val totalActivities: Int,
    val lastActivity: Long,
    val hasPassword: Boolean = false,  // New field to indicate password protection
    val totalCones: Int = 0,  // NEW: Individual count
    val totalJoints: Int = 0, // NEW: Individual count
    val totalBowls: Int = 0,  // NEW: Individual count
    val isOnline: Boolean = false // NEW: Online status indicator
)
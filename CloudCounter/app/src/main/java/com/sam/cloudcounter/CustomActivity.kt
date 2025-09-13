package com.sam.cloudcounter

import java.util.UUID

/**
 * Data class representing a custom user-defined activity
 */
data class CustomActivity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,           // Max 6 chars or icon identifier
    val displayName: String,    // "ADD CUSTOM" format
    val iconResId: Int? = null, // Icon resource if using icon instead of text
    val color: String = "#ff91a4", // Neon candy color
    val position: Int = 4,      // Default position after the 3 core activities
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) {
    
    companion object {
        const val MAX_NAME_LENGTH = 6
        const val MAX_CUSTOM_ACTIVITIES = 1 // Current limit
        const val DEFAULT_COLOR = "#ff91a4" // Neon candy
        
        // Available icons for custom activities
        val AVAILABLE_ICONS = listOf(
            R.drawable.ic_leaf,
            R.drawable.ic_smoke,
            R.drawable.ic_fire,
            R.drawable.ic_bolt,
            android.R.drawable.star_off,
            R.drawable.ic_wave
        )
        
        val ICON_NAMES = listOf(
            "Leaf",
            "Smoke",
            "Fire",
            "Bolt", 
            "Star",
            "Wave"
        )
    }
    
    fun requiresIcon(): Boolean = name.length > MAX_NAME_LENGTH
    
    fun getButtonText(): String {
        return if (requiresIcon()) {
            "ADD" // Just "ADD" when using icon
        } else {
            "ADD ${name.uppercase()}"
        }
    }
}
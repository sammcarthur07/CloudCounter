package com.sam.cloudcounter

import java.util.UUID

/**
 * Data class representing a custom user-defined activity
 */
data class CustomActivity(
    val id: String = UUID.randomUUID().toString(),
    val name: String,           // Max 8 chars or icon identifier
    val displayName: String,    // "ADD CUSTOM" format
    val iconResId: Int? = null, // Icon resource if using icon instead of text
    val color: String = "#ff91a4", // Neon candy color
    val position: Int = 4,      // Default position after the 3 core activities
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) {
    
    companion object {
        const val MAX_NAME_LENGTH = 8
        const val MAX_CUSTOM_ACTIVITIES = 1 // Current limit
        const val DEFAULT_COLOR = "#ff91a4" // Neon candy
        
        // Available icons for custom activities
        val AVAILABLE_ICONS = listOf(
            R.drawable.ic_pills,
            R.drawable.ic_bong,
            R.drawable.ic_cough,
            R.drawable.ic_stretch,
            R.drawable.ic_cigarette,
            R.drawable.ic_water_glass
        )
        
        val ICON_NAMES = listOf(
            "Pills",
            "Bong",
            "Cough",
            "Stretch", 
            "Cigarette",
            "Water"
        )
    }
    
    fun requiresIcon(): Boolean = name.length > MAX_NAME_LENGTH || iconResId != null

    fun getButtonText(): String {
        // For icon-based activities, no text should appear on the button
        if (iconResId != null) return ""
        // For text-based activities (<= MAX_NAME_LENGTH), show two lines
        return "ADD\n${name.uppercase()}"
    }
}

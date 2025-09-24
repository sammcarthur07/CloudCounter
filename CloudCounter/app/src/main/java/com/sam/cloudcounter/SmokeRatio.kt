package com.sam.cloudcounter

import java.util.UUID

data class SmokeRatio(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val type: RatioType,
    val numberOfSmokes: Int,
    val thcPercent: Double,
    val chopAmount: Double,  // Total grams user chops
    val cigarettesPerSmoke: Double = 1.0,  // Cigarettes per bowl/joint activity (not per smoke in ratio)
    val isSelected: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModified: Long = System.currentTimeMillis()
) {
    val gramsPerSmoke: Double
        get() = chopAmount / numberOfSmokes
    
    val totalGrams: Double
        get() = chopAmount
    
    enum class RatioType {
        JOINT,
        BOWL
    }
    
    companion object {
        fun createNew(
            numberOfSmokes: Int,
            thcPercent: Double,
            chopAmount: Double,
            type: RatioType
        ): SmokeRatio {
            // Calculate cigarettes per smoke: 1 cigarette divided by number of smokes
            // e.g., 2 smokes = 0.5 cigarettes per smoke
            val cigarettesPerActivity = 1.0 / numberOfSmokes.toDouble()
            
            return SmokeRatio(
                name = "",
                type = type,
                numberOfSmokes = numberOfSmokes,
                thcPercent = thcPercent,
                chopAmount = chopAmount,
                cigarettesPerSmoke = cigarettesPerActivity
            )
        }
    }
}
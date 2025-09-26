package com.vibecode.cloudcounter

/**
 * Enum representing the different types of activities that can be logged.
 */
enum class ActivityType {
    CONE,
    JOINT,
    BOWL,
    CUSTOM,  // For custom activities - should not interact with stash
    SESSION_SUMMARY,
    CIGARETTE  // For cigarette tracking based on smoke ratios
}

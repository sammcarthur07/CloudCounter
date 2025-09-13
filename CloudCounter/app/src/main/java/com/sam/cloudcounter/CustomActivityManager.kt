package com.sam.cloudcounter

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Manager class for handling custom activities
 */
class CustomActivityManager(private val context: Context) {
    
    companion object {
        private const val TAG = "CUSTOM_ACTIVITY"
        private const val PREFS_NAME = "custom_activities_prefs"
        private const val KEY_CUSTOM_ACTIVITIES = "custom_activities"
        private const val KEY_ACTIVITY_ORDER = "activity_button_order"
        private const val KEY_DISABLED_CORE = "disabled_core_activities"
        private const val DEFAULT_ORDER = "joint,cone,bowl"
        const val MAX_TOTAL_ACTIVITIES = 4
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    
    /**
     * Get all custom activities
     */
    fun getCustomActivities(): List<CustomActivity> {
        Log.d(TAG, "📋 Getting custom activities from storage")
        val json = prefs.getString(KEY_CUSTOM_ACTIVITIES, "[]") ?: "[]"
        val type = object : TypeToken<List<CustomActivity>>() {}.type
        val activities = gson.fromJson<List<CustomActivity>>(json, type)
        Log.d(TAG, "📋 Retrieved ${activities.size} custom activities")
        activities.forEach { 
            Log.d(TAG, "  - ${it.name} (id: ${it.id}, icon: ${it.iconResId != null})")
        }
        return activities
    }
    
    /**
     * Get disabled core activities
     */
    fun getDisabledCoreActivities(): Set<String> {
        val disabled = prefs.getStringSet(KEY_DISABLED_CORE, emptySet()) ?: emptySet()
        Log.d(TAG, "🚫 Disabled core activities: $disabled")
        return disabled
    }
    
    /**
     * Set disabled core activities
     */
    fun setDisabledCoreActivities(disabled: Set<String>) {
        prefs.edit().putStringSet(KEY_DISABLED_CORE, disabled).apply()
        Log.d(TAG, "💾 Saved disabled core activities: $disabled")
    }
    
    /**
     * Get enabled core activities count
     */
    fun getEnabledCoreCount(): Int {
        val disabled = getDisabledCoreActivities()
        return 3 - disabled.size // 3 core activities total
    }
    
    /**
     * Get maximum allowed custom activities based on disabled core
     */
    fun getMaxCustomActivities(): Int {
        val disabledCore = getDisabledCoreActivities().size
        return MAX_TOTAL_ACTIVITIES - (3 - disabledCore)
    }
    
    /**
     * Add a new custom activity
     */
    fun addCustomActivity(activity: CustomActivity): Boolean {
        Log.d(TAG, "➕ Adding custom activity: ${activity.name}")
        val currentActivities = getCustomActivities().toMutableList()
        val maxAllowed = getMaxCustomActivities()
        
        if (currentActivities.size >= maxAllowed) {
            Log.e(TAG, "❌ Cannot add activity - limit reached (max: $maxAllowed)")
            return false
        }
        
        // Check for duplicate names
        if (currentActivities.any { it.name.equals(activity.name, ignoreCase = true) }) {
            Log.e(TAG, "❌ Cannot add activity - duplicate name: ${activity.name}")
            return false
        }
        
        currentActivities.add(activity)
        saveCustomActivities(currentActivities)
        
        // Update activity order to include new activity
        val currentOrder = getActivityOrder()
        if (!currentOrder.contains(activity.id)) {
            val newOrder = currentOrder.toMutableList()
            newOrder.add(activity.id)
            saveActivityOrder(newOrder)
            Log.d(TAG, "📊 Updated activity order: $newOrder")
        }
        
        Log.d(TAG, "✅ Successfully added custom activity: ${activity.name}")
        return true
    }
    
    /**
     * Delete a custom activity
     */
    fun deleteCustomActivity(activityId: String): Boolean {
        Log.d(TAG, "🗑️ Deleting custom activity: $activityId")
        val currentActivities = getCustomActivities().toMutableList()
        val removed = currentActivities.removeAll { it.id == activityId }
        
        if (removed) {
            saveCustomActivities(currentActivities)
            
            // Remove from activity order
            val currentOrder = getActivityOrder().toMutableList()
            currentOrder.remove(activityId)
            saveActivityOrder(currentOrder)
            
            Log.d(TAG, "✅ Successfully deleted custom activity: $activityId")
            return true
        }
        
        Log.e(TAG, "❌ Failed to delete - activity not found: $activityId")
        return false
    }
    
    /**
     * Get activity by ID
     */
    fun getActivityById(activityId: String): CustomActivity? {
        return getCustomActivities().find { it.id == activityId }
    }
    
    /**
     * Get activity button order
     */
    fun getActivityOrder(): List<String> {
        val orderString = prefs.getString(KEY_ACTIVITY_ORDER, DEFAULT_ORDER) ?: DEFAULT_ORDER
        val order = orderString.split(",").toMutableList()
        
        // Get custom activities and disabled core
        val customActivities = getCustomActivities()
        val disabledCore = getDisabledCoreActivities()
        
        // Build the order: custom activities LEFT of core buttons
        val finalOrder = mutableListOf<String>()
        
        // Add custom activities first (they appear to the LEFT)
        customActivities.forEach { activity ->
            if (!finalOrder.contains(activity.id)) {
                finalOrder.add(activity.id)
            }
        }
        
        // Then add enabled core activities in fixed order
        if (!disabledCore.contains("joint")) finalOrder.add("joint")
        if (!disabledCore.contains("cone")) finalOrder.add("cone")
        if (!disabledCore.contains("bowl")) finalOrder.add("bowl")
        
        Log.d(TAG, "📊 Current activity order: $finalOrder")
        return finalOrder
    }
    
    /**
     * Save activity button order
     */
    fun saveActivityOrder(order: List<String>) {
        val orderString = order.joinToString(",")
        prefs.edit().putString(KEY_ACTIVITY_ORDER, orderString).apply()
        Log.d(TAG, "💾 Saved activity order: $orderString")
    }
    
    /**
     * Clear all custom activities and reset to defaults
     */
    fun clearAllCustomActivities() {
        Log.d(TAG, "🧹 Clearing all custom activities and resetting to defaults")
        prefs.edit()
            .remove(KEY_CUSTOM_ACTIVITIES)
            .remove(KEY_DISABLED_CORE)
            .putString(KEY_ACTIVITY_ORDER, DEFAULT_ORDER)
            .apply()
        Log.d(TAG, "✅ All custom activities cleared and core activities restored")
    }
    
    private fun saveCustomActivities(activities: List<CustomActivity>) {
        val json = gson.toJson(activities)
        prefs.edit().putString(KEY_CUSTOM_ACTIVITIES, json).apply()
        Log.d(TAG, "💾 Saved ${activities.size} custom activities to storage")
    }
}
package com.vibecode.cloudcounter

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class SmokeRatioManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("smoke_ratios", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        private const val TAG = "SmokeRatioManager"
        private const val KEY_JOINT_RATIOS = "joint_ratios"
        private const val KEY_BOWL_RATIOS = "bowl_ratios"
        private const val KEY_CIGARETTE_FRACTION = "cigarette_fraction_remainder"
        private const val KEY_LAST_DEFAULTS = "last_ratio_defaults"
        private const val KEY_LAST_TYPE = "last_ratio_type"
    }
    
    fun saveRatio(ratio: SmokeRatio) {
        Log.d(TAG, "Saving ratio: ${ratio.name} (${ratio.type}), numberOfSmokes=${ratio.numberOfSmokes}, cigarettesPerSmoke=${ratio.cigarettesPerSmoke}")
        val key = when (ratio.type) {
            SmokeRatio.RatioType.JOINT -> KEY_JOINT_RATIOS
            SmokeRatio.RatioType.BOWL -> KEY_BOWL_RATIOS
        }
        
        val ratios = getRatiosForType(ratio.type).toMutableList()
        
        val existingIndex = ratios.indexOfFirst { it.id == ratio.id }
        if (existingIndex >= 0) {
            ratios[existingIndex] = ratio.copy(lastModified = System.currentTimeMillis())
        } else {
            val finalRatio = if (ratio.name.isEmpty()) {
                ratio.copy(name = generateAutoName(ratios, ratio.thcPercent))
            } else {
                ratio
            }
            ratios.add(finalRatio)
        }
        
        val json = gson.toJson(ratios)
        prefs.edit().putString(key, json).apply()
        Log.d(TAG, "Saved ${ratios.size} ${ratio.type} ratios")
    }
    
    fun getRatiosForType(type: SmokeRatio.RatioType): List<SmokeRatio> {
        val key = when (type) {
            SmokeRatio.RatioType.JOINT -> KEY_JOINT_RATIOS
            SmokeRatio.RatioType.BOWL -> KEY_BOWL_RATIOS
        }
        
        val json = prefs.getString(key, null) ?: return emptyList()
        return try {
            val typeToken = object : TypeToken<List<SmokeRatio>>() {}.type
            val ratios: List<SmokeRatio> = gson.fromJson(json, typeToken)
            
            // Migrate ratios with incorrect cigarettesPerSmoke values
            val migratedRatios = ratios.map { ratio ->
                if (ratio.cigarettesPerSmoke == 1.0 && ratio.numberOfSmokes > 1) {
                    // This ratio needs migration
                    val correctCigarettesPerSmoke = 1.0 / ratio.numberOfSmokes.toDouble()
                    Log.d(TAG, "Migrating ratio '${ratio.name}': ${ratio.numberOfSmokes} smokes = $correctCigarettesPerSmoke cigs/activity")
                    ratio.copy(cigarettesPerSmoke = correctCigarettesPerSmoke)
                } else {
                    ratio
                }
            }
            
            // Save migrated ratios if any were changed
            if (migratedRatios != ratios) {
                val migratedJson = gson.toJson(migratedRatios)
                prefs.edit().putString(key, migratedJson).apply()
                Log.d(TAG, "Saved ${migratedRatios.size} migrated ratios")
            }
            
            migratedRatios
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ratios", e)
            emptyList()
        }
    }
    
    fun deleteRatio(ratioId: String, type: SmokeRatio.RatioType) {
        Log.d(TAG, "Deleting ratio: $ratioId")
        val key = when (type) {
            SmokeRatio.RatioType.JOINT -> KEY_JOINT_RATIOS
            SmokeRatio.RatioType.BOWL -> KEY_BOWL_RATIOS
        }
        
        val ratios = getRatiosForType(type).filter { it.id != ratioId }
        val json = gson.toJson(ratios)
        prefs.edit().putString(key, json).apply()
    }
    
    fun setSelectedRatio(ratioId: String, type: SmokeRatio.RatioType) {
        Log.d(TAG, "Setting selected ratio: $ratioId for type: $type")
        val ratios = getRatiosForType(type).map { ratio ->
            ratio.copy(isSelected = ratio.id == ratioId)
        }
        
        val key = when (type) {
            SmokeRatio.RatioType.JOINT -> KEY_JOINT_RATIOS
            SmokeRatio.RatioType.BOWL -> KEY_BOWL_RATIOS
        }
        
        val json = gson.toJson(ratios)
        prefs.edit().putString(key, json).apply()
    }
    
    fun getSelectedRatio(type: SmokeRatio.RatioType): SmokeRatio? {
        return getRatiosForType(type).firstOrNull { it.isSelected }
    }
    
    fun clearSelection(type: SmokeRatio.RatioType) {
        Log.d(TAG, "Clearing selection for type: $type")
        val ratios = getRatiosForType(type).map { ratio ->
            ratio.copy(isSelected = false)
        }
        
        val key = when (type) {
            SmokeRatio.RatioType.JOINT -> KEY_JOINT_RATIOS
            SmokeRatio.RatioType.BOWL -> KEY_BOWL_RATIOS
        }
        
        val json = gson.toJson(ratios)
        prefs.edit().putString(key, json).apply()
    }
    
    fun isNameUnique(name: String, type: SmokeRatio.RatioType, excludeId: String? = null): Boolean {
        return getRatiosForType(type).none { 
            it.name.equals(name, ignoreCase = true) && it.id != excludeId
        }
    }
    
    fun generateAutoName(type: SmokeRatio.RatioType, thcPercent: Double): String {
        val existingRatios = getRatiosForType(type)
        return generateAutoName(existingRatios, thcPercent)
    }
    
    private fun generateAutoName(existingRatios: List<SmokeRatio>, thcPercent: Double): String {
        return if (thcPercent > 0) {
            "${thcPercent.toInt()}% THC Mix"
        } else {
            var counter = 1
            var name = "Ratio $counter"
            while (existingRatios.any { it.name == name }) {
                counter++
                name = "Ratio $counter"
            }
            name
        }
    }
    
    fun getCigaretteFraction(smokerId: Long? = null): Double {
        val key = if (smokerId != null) {
            "${KEY_CIGARETTE_FRACTION}_$smokerId"
        } else {
            KEY_CIGARETTE_FRACTION
        }
        return prefs.getFloat(key, 0f).toDouble()
    }
    
    fun saveCigaretteFraction(fraction: Double, smokerId: Long? = null) {
        val key = if (smokerId != null) {
            "${KEY_CIGARETTE_FRACTION}_$smokerId"
        } else {
            KEY_CIGARETTE_FRACTION
        }
        Log.d(TAG, "Saving cigarette fraction: $fraction for smoker: $smokerId")
        prefs.edit().putFloat(key, fraction.toFloat()).apply()
    }
    
    fun saveLastDefaults(smokes: Int, thcPercent: Double, chopAmount: Double, type: SmokeRatio.RatioType) {
        Log.d(TAG, "Saving defaults: smokes=$smokes, thc=$thcPercent, chop=$chopAmount, type=$type")
        prefs.edit()
            .putInt("${KEY_LAST_DEFAULTS}_smokes", smokes)
            .putFloat("${KEY_LAST_DEFAULTS}_thc", thcPercent.toFloat())
            .putFloat("${KEY_LAST_DEFAULTS}_chop", chopAmount.toFloat())
            .putString(KEY_LAST_TYPE, type.name)
            .apply()
    }
    
    fun getLastDefaults(): Triple<Int, Double, Double>? {
        if (!prefs.contains("${KEY_LAST_DEFAULTS}_smokes")) return null
        return Triple(
            prefs.getInt("${KEY_LAST_DEFAULTS}_smokes", 3),
            prefs.getFloat("${KEY_LAST_DEFAULTS}_thc", 20f).toDouble(),
            prefs.getFloat("${KEY_LAST_DEFAULTS}_chop", 0.75f).toDouble()
        )
    }
    
    fun getLastType(): SmokeRatio.RatioType {
        val typeName = prefs.getString(KEY_LAST_TYPE, SmokeRatio.RatioType.JOINT.name)
        return try {
            SmokeRatio.RatioType.valueOf(typeName!!)
        } catch (e: Exception) {
            SmokeRatio.RatioType.JOINT
        }
    }
}
package com.sam.cloudcounter

import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.*
import java.util.*
import kotlin.math.*

class City420RotationManager(private val context: Context) {
    
    companion object {
        private const val TAG = "City420RotationManager"
        private const val ROTATION_INTERVAL_MS = 5000L // Switch every 5 seconds
        
        data class CityInfo(
            val name: String,
            val timezone: String,
            val latitude: Double,
            val longitude: Double
        )
        
        private val CITIES = listOf(
            CityInfo("Los Angeles, USA", "America/Los_Angeles", 34.0522, -118.2437),
            CityInfo("San Francisco, USA", "America/Los_Angeles", 37.7749, -122.4194),
            CityInfo("Seattle, USA", "America/Los_Angeles", 47.6062, -122.3321),
            CityInfo("Denver, USA", "America/Denver", 39.7392, -104.9903),
            CityInfo("Chicago, USA", "America/Chicago", 41.8781, -87.6298),
            CityInfo("Houston, USA", "America/Chicago", 29.7604, -95.3698),
            CityInfo("New York, USA", "America/New_York", 40.7128, -74.0060),
            CityInfo("Toronto, Canada", "America/Toronto", 43.6532, -79.3832),
            CityInfo("Miami, USA", "America/New_York", 25.7617, -80.1918),
            CityInfo("London, UK", "Europe/London", 51.5074, -0.1278),
            CityInfo("Paris, France", "Europe/Paris", 48.8566, 2.3522),
            CityInfo("Amsterdam, Netherlands", "Europe/Amsterdam", 52.3676, 4.9041),
            CityInfo("Berlin, Germany", "Europe/Berlin", 52.5200, 13.4050),
            CityInfo("Rome, Italy", "Europe/Rome", 41.9028, 12.4964),
            CityInfo("Tokyo, Japan", "Asia/Tokyo", 35.6762, 139.6503),
            CityInfo("Sydney, Australia", "Australia/Sydney", -33.8688, 151.2093),
            CityInfo("Melbourne, Australia", "Australia/Melbourne", -37.8136, 144.9631),
            CityInfo("Auckland, New Zealand", "Pacific/Auckland", -36.8485, 174.7633),
            CityInfo("Dubai, UAE", "Asia/Dubai", 25.2048, 55.2708),
            CityInfo("Mumbai, India", "Asia/Kolkata", 19.0760, 72.8777),
            CityInfo("Bangkok, Thailand", "Asia/Bangkok", 13.7563, 100.5018),
            CityInfo("Singapore", "Asia/Singapore", 1.3521, 103.8198),
            CityInfo("Hong Kong", "Asia/Hong_Kong", 22.3193, 114.1694),
            CityInfo("Beijing, China", "Asia/Shanghai", 39.9042, 116.4074),
            CityInfo("Cape Town, South Africa", "Africa/Johannesburg", -33.9249, 18.4241),
            CityInfo("São Paulo, Brazil", "America/Sao_Paulo", -23.5505, -46.6333),
            CityInfo("Mexico City, Mexico", "America/Mexico_City", 19.4326, -99.1332),
            CityInfo("Buenos Aires, Argentina", "America/Argentina/Buenos_Aires", -34.6037, -58.3816),
            CityInfo("Moscow, Russia", "Europe/Moscow", 55.7558, 37.6173),
            CityInfo("Cairo, Egypt", "Africa/Cairo", 30.0444, 31.2357)
        )
        
        private var rotationHandler: Handler? = null
        private var rotationRunnable: Runnable? = null
        private var currentCityIndex = 0
        private var lastUserCityShown = false
        private var userCity: CityInfo? = null
        private var eligibleCities = mutableListOf<CityWithTime>()
        
        data class CityWithTime(
            val city: CityInfo,
            val secondsUntil420: Int,
            val is420Now: Boolean
        )
    }
    
    fun startRotationNotifications(isMorning: Boolean) {
        Log.d(TAG, "Starting city rotation notifications")
        
        rotationHandler = Handler(Looper.getMainLooper())
        
        CoroutineScope(Dispatchers.IO).launch {
            userCity = getUserCityInfo()
            updateEligibleCities(isMorning)
            
            withContext(Dispatchers.Main) {
                startRotation()
            }
        }
    }
    
    fun stopRotationNotifications() {
        Log.d(TAG, "Stopping city rotation notifications")
        rotationRunnable?.let { rotationHandler?.removeCallbacks(it) }
        rotationHandler = null
        rotationRunnable = null
    }
    
    private fun startRotation() {
        rotationRunnable = object : Runnable {
            override fun run() {
                showNextCity()
                rotationHandler?.postDelayed(this, ROTATION_INTERVAL_MS)
            }
        }
        rotationRunnable?.run()
    }
    
    private fun showNextCity() {
        if (eligibleCities.isEmpty()) {
            Log.d(TAG, "❌ SHOW_NEXT: No eligible cities for rotation")
            return
        }
        
        Log.d(TAG, "🔄 SHOW_NEXT: Selecting next city to show")
        Log.d(TAG, "   Current index: $currentCityIndex/${eligibleCities.size}")
        Log.d(TAG, "   Last user city shown: $lastUserCityShown")
        
        val cityToShow: CityWithTime
        val isUserCity: Boolean
        
        if (userCity != null && shouldShowUserCity()) {
            val userCityWithTime = eligibleCities.find { it.city.name == userCity?.name }
            if (userCityWithTime != null) {
                cityToShow = userCityWithTime
                isUserCity = true
                lastUserCityShown = true
                Log.d(TAG, "👤 SHOWING_USER_CITY: ${cityToShow.city.name}")
            } else {
                // Add randomness: 70% sequential, 30% random jump
                if (kotlin.random.Random.nextFloat() < 0.3) {
                    currentCityIndex = kotlin.random.Random.nextInt(eligibleCities.size)
                    Log.d(TAG, "🎲 Random jump to index $currentCityIndex")
                }
                cityToShow = eligibleCities[currentCityIndex]
                isUserCity = false
                currentCityIndex = (currentCityIndex + 1) % eligibleCities.size
                lastUserCityShown = false
                Log.d(TAG, "🌍 SHOWING_OTHER_CITY: ${cityToShow.city.name} (no user city in eligible list)")
            }
        } else {
            // Add randomness: 70% sequential, 30% random jump
            if (kotlin.random.Random.nextFloat() < 0.3) {
                currentCityIndex = kotlin.random.Random.nextInt(eligibleCities.size)
                Log.d(TAG, "🎲 Random jump to index $currentCityIndex")
            }
            cityToShow = eligibleCities[currentCityIndex]
            isUserCity = false
            currentCityIndex = (currentCityIndex + 1) % eligibleCities.size
            lastUserCityShown = false
            Log.d(TAG, "🌍 SHOWING_OTHER_CITY: ${cityToShow.city.name}")
        }
        
        val timeDisplay = if (cityToShow.is420Now) {
            "NOW!"
        } else {
            "${cityToShow.secondsUntil420}s (${cityToShow.secondsUntil420/60}m ${cityToShow.secondsUntil420%60}s)"
        }
        
        Log.d(TAG, "📱 NOTIFICATION_UPDATE: ${cityToShow.city.name} - $timeDisplay ${if (isUserCity) "[USER]" else ""}")
        
        // Don't show rotating city notifications with vibrations
        // This prevents the continuous vibrations during the 2 minutes before 4:20
        // The actual 5-minute and 4:20 notifications will still work as expected
    }
    
    private fun shouldShowUserCity(): Boolean {
        return !lastUserCityShown
    }
    
    private suspend fun getUserCityInfo(): CityInfo? = withContext(Dispatchers.IO) {
        try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            location?.let {
                val geocoder = Geocoder(context, Locale.getDefault())
                val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)
                
                if (addresses?.isNotEmpty() == true) {
                    val address = addresses[0]
                    val cityName = address.locality ?: address.adminArea ?: "Unknown"
                    val countryName = address.countryName ?: ""
                    
                    val timezone = TimeZone.getDefault().id
                    
                    return@withContext CityInfo(
                        "$cityName, $countryName",
                        timezone,
                        it.latitude,
                        it.longitude
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get user location", e)
        }
        return@withContext null
    }
    
    private fun updateEligibleCities(isMorning: Boolean) {
        eligibleCities.clear()
        val now = System.currentTimeMillis()
        
        Log.d(TAG, "🔍 UPDATE_ELIGIBLE_CITIES: Starting calculation")
        Log.d(TAG, "🔍   Morning: $isMorning")
        Log.d(TAG, "🔍   Current time: ${Date(now)}")
        
        val allCitiesWithTimes = mutableListOf<CityWithTime>()
        
        // Check ALL cities for their next 4:20 (both AM and PM)
        for (city in CITIES) {
            val tz = TimeZone.getTimeZone(city.timezone)
            val cal = Calendar.getInstance(tz)
            cal.timeInMillis = now
            
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val currentMinute = cal.get(Calendar.MINUTE)
            val currentSecond = cal.get(Calendar.SECOND)
            
            // Check if currently at 4:20 (within 2 minute window)
            val is420Now = (currentHour == 4 || currentHour == 16) && currentMinute in 18..22
            
            // Find the NEXT 4:20 (could be AM or PM)
            val next420AM = Calendar.getInstance(tz).apply {
                set(Calendar.HOUR_OF_DAY, 4)
                set(Calendar.MINUTE, 20)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            val next420PM = Calendar.getInstance(tz).apply {
                set(Calendar.HOUR_OF_DAY, 16)
                set(Calendar.MINUTE, 20)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            // Use the closest 4:20 (whether AM or PM)
            val closestTarget = if (next420AM.timeInMillis < next420PM.timeInMillis) next420AM else next420PM
            val secondsUntil = if (is420Now) 0 else ((closestTarget.timeInMillis - now) / 1000).toInt()
            
            allCitiesWithTimes.add(CityWithTime(city, secondsUntil, is420Now))
            
            // Log cities that are close
            if (secondsUntil <= 300) {
                Log.d(TAG, "🌍 CLOSE_CITY: ${city.name} - ${if (is420Now) "NOW!" else "${secondsUntil}s"} (${currentHour}:${String.format("%02d", currentMinute)}:${String.format("%02d", currentSecond)})")
            }
        }
        
        // Add user's city if available
        userCity?.let { user ->
            val tz = TimeZone.getTimeZone(user.timezone)
            val cal = Calendar.getInstance(tz)
            cal.timeInMillis = now
            
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val currentMinute = cal.get(Calendar.MINUTE)
            
            val is420Now = (currentHour == 4 || currentHour == 16) && currentMinute in 18..22
            
            val next420AM = Calendar.getInstance(tz).apply {
                set(Calendar.HOUR_OF_DAY, 4)
                set(Calendar.MINUTE, 20)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            val next420PM = Calendar.getInstance(tz).apply {
                set(Calendar.HOUR_OF_DAY, 16)
                set(Calendar.MINUTE, 20)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            val closestTarget = if (next420AM.timeInMillis < next420PM.timeInMillis) next420AM else next420PM
            val secondsUntil = if (is420Now) 0 else ((closestTarget.timeInMillis - now) / 1000).toInt()
            
            allCitiesWithTimes.add(CityWithTime(user, secondsUntil, is420Now))
            Log.d(TAG, "👤 USER_CITY: ${user.name} - ${if (is420Now) "NOW!" else "${secondsUntil}s"}")
        }
        
        // Shuffle cities within time windows before sorting
        val timeWindows = mutableMapOf<Int, MutableList<CityWithTime>>()
        allCitiesWithTimes.forEach { city ->
            val windowKey = city.secondsUntil420 / 300 // 5 minute windows
            timeWindows.getOrPut(windowKey) { mutableListOf() }.add(city)
        }
        
        // Clear and repopulate with shuffled cities within each window
        allCitiesWithTimes.clear()
        timeWindows.keys.sorted().forEach { windowKey ->
            val citiesInWindow = timeWindows[windowKey] ?: emptyList()
            allCitiesWithTimes.addAll(citiesInWindow.shuffled())
        }
        
        // Sort by seconds until 4:20 (maintains order between windows)
        allCitiesWithTimes.sortBy { it.secondsUntil420 }
        
        Log.d(TAG, "🔢 SORTED_CITIES: Top 10 closest to 4:20:")
        allCitiesWithTimes.take(10).forEachIndexed { index, city ->
            val timeStr = if (city.is420Now) "NOW!" else "${city.secondsUntil420}s (${city.secondsUntil420/60}m ${city.secondsUntil420%60}s)"
            Log.d(TAG, "   ${index + 1}. ${city.city.name}: $timeStr")
        }
        
        // Apply tiered threshold selection with randomization
        val thresholds = listOf(120, 240, 360, 480, 600, 900, 1200, 1800)
        var foundCities = false
        
        for (threshold in thresholds) {
            val citiesWithinThreshold = allCitiesWithTimes.filter { 
                it.secondsUntil420 <= threshold 
            }
            if (citiesWithinThreshold.isNotEmpty()) {
                // Shuffle before taking to add variety
                val shuffledCities = citiesWithinThreshold.shuffled()
                eligibleCities.addAll(shuffledCities.take(10))
                Log.d(TAG, "✅ THRESHOLD_SELECTED: Using ${citiesWithinThreshold.size} cities within ${threshold}s (${threshold/60}m) - shuffled")
                foundCities = true
                break
            }
        }
        
        // Fallback - use closest 5 cities but shuffled for variety
        if (!foundCities || eligibleCities.isEmpty()) {
            val closest5 = allCitiesWithTimes.take(5).shuffled()
            eligibleCities.addAll(closest5)
            Log.d(TAG, "⚠️ FALLBACK: Using closest 5 cities (shuffled) regardless of distance")
        }
        
        Log.d(TAG, "📋 FINAL_ELIGIBLE: ${eligibleCities.size} cities selected for rotation:")
        eligibleCities.forEach { 
            val timeStr = if (it.is420Now) "NOW!" else "${it.secondsUntil420}s"
            Log.d(TAG, "   - ${it.city.name}: $timeStr")
        }
    }
}
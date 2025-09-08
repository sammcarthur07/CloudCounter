package com.sam.cloudcounter

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.ListPopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class SmokerManager(
    private val context: Context,
    private val repository: ActivityRepository,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val authManager: FirebaseAuthManager,
    private val cloudSyncService: CloudSyncService,
    private val sessionSyncService: SessionSyncService
) {
    companion object {
        private const val TAG = "SmokerManager"
        private val NEON_COLORS = listOf(
            Color.parseColor("#FFFF66"),
            Color.parseColor("#BF7EFF"),
            Color.parseColor("#98FB98"),
            Color.parseColor("#66B2FF"),
            Color.parseColor("#FFA366")
        )
    }

    private val fontList = listOf(
        R.font.bitcount_prop_double,
        R.font.exile,
        R.font.modak,
        R.font.oi,
        R.font.rubik_glitch,
        R.font.sankofa_display,
        R.font.silkscreen,
        R.font.rubik_puddles,
        R.font.rubik_beastly,
        R.font.sixtyfour,
        R.font.monoton,
        R.font.sedgwick_ave_display,
        R.font.splash
    )

    private val smokerFontMap = mutableMapOf<Long, Typeface>()
    private val smokerColorMap = mutableMapOf<Long, Int>()
    private val smokerFontIndexMap = mutableMapOf<Long, Int>()  // Track font indices for each smoker
    private var defaultFont: Typeface? = null
    private var lastSelectedFontIndex = -1
    private var globalLockedFontIndex = -1  // Track the global locked font index

    // Global lock values - these persist across all smokers when locked
    private var globalLockedFont: Typeface? = null
    private var globalLockedColor: Int? = null

    var randomFontsEnabled = true
    var colorChangingEnabled = true
    var currentShareCode: String? = null
    var pausedSmokerIds = mutableListOf<String>()
    var awaySmokers = mutableListOf<String>()

    var onSyncCloudSmoker: ((Smoker) -> Unit)? = null
    var onRefreshCloudSmokerName: ((Smoker) -> Unit)? = null
    var onEditSmoker: ((Smoker) -> Unit)? = null
    var onChangePassword: ((Smoker) -> Unit)? = null
    var onTogglePause: ((Smoker) -> Unit)? = null
    var onDeleteSmoker: ((Smoker) -> Unit)? = null
    var onUpdateSyncStatusDot: ((View?, Smoker) -> Unit)? = null

    private var spinnerHoldStartTime = 0L
    private var spinnerLongPressHandler: Handler? = null
    private var spinnerLongPressRunnable: Runnable? = null
    private var fontCycleHandler: Handler? = null
    private var fontCycleRunnable: Runnable? = null

    fun getFontForSmoker(smokerId: Long): Typeface {
        Log.d(TAG, "🎨 GET_FONT_FOR_SMOKER: ID=$smokerId")
        Log.d(TAG, "🎨   randomFontsEnabled: $randomFontsEnabled")
        Log.d(TAG, "🎨   globalLockedFont: ${globalLockedFont != null}")

        // When fonts are locked, return the global locked font for ALL smokers
        if (!randomFontsEnabled && globalLockedFont != null) {
            Log.d(TAG, "🎨   FONTS LOCKED - Returning global locked font for all smokers")
            smokerFontIndexMap[smokerId] = globalLockedFontIndex  // Track the locked font index
            return globalLockedFont!!
        }

        if (!randomFontsEnabled) {
            // Fonts locked but no global font set yet, use default
            val defaultFont = getDefaultFont()
            Log.d(TAG, "🎨   FONTS LOCKED - No global font set, using default")
            return defaultFont
        }

        // Random fonts enabled - use per-smoker logic
        val cachedFont = smokerFontMap[smokerId]
        if (cachedFont != null) {
            Log.d(TAG, "🎨   FONTS UNLOCKED - Found cached font for smoker $smokerId")
            return cachedFont
        }

        // Generate new random font for this smoker
        var randomIndex = Random.nextInt(fontList.size)
        if (randomIndex == lastSelectedFontIndex && fontList.size > 1) {
            randomIndex = (randomIndex + 1) % fontList.size
        }
        lastSelectedFontIndex = randomIndex
        smokerFontIndexMap[smokerId] = randomIndex  // Track the font index

        val font = try {
            val newFont = ResourcesCompat.getFont(context, fontList[randomIndex])
            if (newFont != null) {
                smokerFontMap[smokerId] = newFont
                Log.d(TAG, "🎨   Generated new font for smoker $smokerId at index $randomIndex")
                newFont
            } else {
                getDefaultFont()
            }
        } catch (e: Exception) {
            Log.e(TAG, "🎨   Error loading font at index $randomIndex", e)
            getDefaultFont()
        }

        return font
    }

    fun getColorForSmoker(smokerId: Long): Int {
        Log.d(TAG, "🌈 GET_COLOR_FOR_SMOKER: ID=$smokerId")
        Log.d(TAG, "🌈   colorChangingEnabled: $colorChangingEnabled")
        Log.d(TAG, "🌈   globalLockedColor: $globalLockedColor")

        // When colors are locked, return the global locked color for ALL smokers
        if (!colorChangingEnabled && globalLockedColor != null) {
            Log.d(TAG, "🌈   COLORS LOCKED - Returning global locked color for all smokers")
            return globalLockedColor!!
        }

        if (!colorChangingEnabled) {
            // Colors locked but no global color set yet, use white
            Log.d(TAG, "🌈   COLORS LOCKED - No global color set, using white")
            return Color.WHITE
        }

        // Random colors enabled - use per-smoker logic
        val cachedColor = smokerColorMap[smokerId]
        if (cachedColor != null && cachedColor != Color.WHITE) {
            Log.d(TAG, "🌈   COLORS UNLOCKED - Found cached color for smoker $smokerId")
            return cachedColor
        }

        // Generate new random color for this smoker
        val randomColor = NEON_COLORS.random()
        smokerColorMap[smokerId] = randomColor
        Log.d(TAG, "🌈   Generated new color for smoker $smokerId")
        return randomColor
    }

    fun clearFontCache(smokerId: Long) {
        if (randomFontsEnabled) {
            smokerFontMap.remove(smokerId)
        }
    }

    fun clearColorCache(smokerId: Long) {
        if (colorChangingEnabled) {
            smokerColorMap.remove(smokerId)
        }
    }

    fun clearAllFontCaches() {
        smokerFontMap.clear()
        // Clear global locked font only if unlocked
        if (randomFontsEnabled) {
            globalLockedFont = null
        }
    }

    fun clearAllColorCaches() {
        smokerColorMap.clear()
        // Clear global locked color only if unlocked
        if (colorChangingEnabled) {
            globalLockedColor = null
        }
    }

    fun toggleFontLock() {
        randomFontsEnabled = !randomFontsEnabled
        // Clear global lock when unlocking
        if (randomFontsEnabled) {
            globalLockedFont = null
            Log.d(TAG, "🎨 Font lock DISABLED - cleared global locked font")
        } else {
            Log.d(TAG, "🎨 Font lock ENABLED")
        }
    }

    fun toggleColorLock() {
        colorChangingEnabled = !colorChangingEnabled
        // Clear global lock when unlocking
        if (colorChangingEnabled) {
            globalLockedColor = null
            Log.d(TAG, "🌈 Color lock DISABLED - cleared global locked color")
        } else {
            Log.d(TAG, "🌈 Color lock ENABLED")
        }
    }

    fun toggleFontAndColorLock() {
        randomFontsEnabled = !randomFontsEnabled
        colorChangingEnabled = !colorChangingEnabled
        // Clear global locks when unlocking
        if (randomFontsEnabled) {
            globalLockedFont = null
            Log.d(TAG, "🎨 Font lock DISABLED - cleared global locked font")
        }
        if (colorChangingEnabled) {
            globalLockedColor = null
            Log.d(TAG, "🌈 Color lock DISABLED - cleared global locked color")
        }
    }

    fun handleLongPress(v: View, event: android.view.MotionEvent, spinner: Spinner, adapter: SmokerAdapter): Boolean {
        var shouldShowDropdown = true
        var wasAbove7Seconds = false

        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                spinnerHoldStartTime = System.currentTimeMillis()
                shouldShowDropdown = true
                wasAbove7Seconds = false

                spinnerLongPressHandler = Handler(Looper.getMainLooper())
                spinnerLongPressRunnable = Runnable {
                    val holdDuration = System.currentTimeMillis() - spinnerHoldStartTime
                    val currentPosition = spinner.selectedItemPosition
                    val selectedSmoker = if (currentPosition >= 0) adapter.getItem(currentPosition) else null

                    // Always capture current font and color for potential locking
                    val spinnerView = spinner.selectedView
                    val textView = spinnerView?.findViewById<TextView>(R.id.textName)
                    val currentFont = textView?.typeface
                    val currentColor = textView?.currentTextColor ?: Color.WHITE

                    when {
                        holdDuration >= 7000 -> {
                            shouldShowDropdown = false
                            wasAbove7Seconds = true
                            if (fontCycleHandler == null) {
                                fontCycleHandler = Handler(Looper.getMainLooper())
                                fontCycleRunnable = object : Runnable {
                                    override fun run() {
                                        selectedSmoker?.let { smoker ->
                                            val nextFont = cycleToNextFont(smoker.smokerId)
                                            // Update the global locked font as we cycle
                                            globalLockedFont = nextFont
                                            val spinnerView = spinner.selectedView
                                            val textView = spinnerView?.findViewById<TextView>(R.id.textName)
                                            textView?.typeface = nextFont
                                            textView?.setTextColor(globalLockedColor ?: currentColor)
                                        }
                                        vibrateFeedback(30)
                                        fontCycleHandler?.postDelayed(this, 2000)
                                    }
                                }
                                fontCycleHandler?.post(fontCycleRunnable!!)
                                Toast.makeText(context, "Font cycling started (every 2s)", Toast.LENGTH_SHORT).show()
                            }
                        }
                        holdDuration >= 5000 -> {
                            shouldShowDropdown = false
                            // Lock both font and color globally
                            toggleFontAndColorLock()
                            if (!randomFontsEnabled) {
                                globalLockedFont = currentFont
                                Log.d(TAG, "🔒 Locked global font at 5s hold")
                            }
                            if (!colorChangingEnabled) {
                                globalLockedColor = currentColor
                                Log.d(TAG, "🔒 Locked global color at 5s hold: $currentColor")
                            }
                            (context.getSharedPreferences("sesh", Context.MODE_PRIVATE)).edit()
                                .putBoolean("random_fonts_enabled", randomFontsEnabled)
                                .putBoolean("color_changing_enabled", colorChangingEnabled)
                                .apply()
                            Toast.makeText(context, getFontAndColorLockStatusMessage(), Toast.LENGTH_SHORT).show()
                            vibrateFeedback(50)
                            spinnerLongPressHandler?.postDelayed({ spinnerLongPressRunnable?.run() }, 2000)
                        }
                        holdDuration >= 3000 -> {
                            shouldShowDropdown = false
                            // Lock font globally
                            toggleFontLock()
                            if (!randomFontsEnabled) {
                                globalLockedFont = currentFont
                                Log.d(TAG, "🔒 Locked global font at 3s hold")
                            }
                            (context.getSharedPreferences("sesh", Context.MODE_PRIVATE)).edit()
                                .putBoolean("random_fonts_enabled", randomFontsEnabled)
                                .apply()
                            Toast.makeText(context, getFontLockStatusMessage(), Toast.LENGTH_SHORT).show()
                            vibrateFeedback(50)
                            spinnerLongPressHandler?.postDelayed({ spinnerLongPressRunnable?.run() }, 2000)
                        }
                        holdDuration >= 1500 -> {
                            shouldShowDropdown = false
                            // Lock color globally
                            toggleColorLock()
                            if (!colorChangingEnabled) {
                                globalLockedColor = currentColor
                                Log.d(TAG, "🔒 Locked global color at 1.5s hold: $currentColor")
                            }
                            (context.getSharedPreferences("sesh", Context.MODE_PRIVATE)).edit()
                                .putBoolean("color_changing_enabled", colorChangingEnabled)
                                .apply()
                            Toast.makeText(context, getColorLockStatusMessage(), Toast.LENGTH_SHORT).show()
                            vibrateFeedback(50)
                            spinnerLongPressHandler?.postDelayed({ spinnerLongPressRunnable?.run() }, 1500)
                        }
                        else -> {
                            spinnerLongPressHandler?.postDelayed({ spinnerLongPressRunnable?.run() }, 100)
                        }
                    }
                }
                spinnerLongPressHandler?.postDelayed(spinnerLongPressRunnable!!, 1500)
            }
            android.view.MotionEvent.ACTION_UP,
            android.view.MotionEvent.ACTION_CANCEL -> {
                fontCycleRunnable?.let { fontCycleHandler?.removeCallbacks(it) }
                fontCycleHandler = null
                fontCycleRunnable = null

                if (wasAbove7Seconds) {
                    // When releasing after 7 seconds, lock with the last cycled font
                    toggleFontAndColorLock()
                    // globalLockedFont is already set during cycling
                    // Capture current color from the view
                    val spinnerView = spinner.selectedView
                    val textView = spinnerView?.findViewById<TextView>(R.id.textName)
                    if (!colorChangingEnabled) {
                        globalLockedColor = textView?.currentTextColor ?: Color.WHITE
                        Log.d(TAG, "🔒 Locked color after 7s cycle: $globalLockedColor")
                    }

                    (context.getSharedPreferences("sesh", Context.MODE_PRIVATE)).edit()
                        .putBoolean("random_fonts_enabled", randomFontsEnabled)
                        .putBoolean("color_changing_enabled", colorChangingEnabled)
                        .apply()
                    Toast.makeText(context, "Font & color locked", Toast.LENGTH_SHORT).show()
                }

                spinnerLongPressRunnable?.let { spinnerLongPressHandler?.removeCallbacks(it) }
                spinnerLongPressHandler = null
                spinnerLongPressRunnable = null

                if (event.action == android.view.MotionEvent.ACTION_UP && !shouldShowDropdown) {
                    spinnerHoldStartTime = 0L
                    return true
                }

                spinnerHoldStartTime = 0L
                shouldShowDropdown = true
            }
        }
        return false
    }

    fun cycleToNextFont(smokerId: Long): Typeface {
        lastSelectedFontIndex = (lastSelectedFontIndex + 1) % fontList.size
        val nextFont = try {
            ResourcesCompat.getFont(context, fontList[lastSelectedFontIndex])
        } catch (e: Exception) {
            getDefaultFont()
        }

        if (nextFont != null) {
            smokerFontMap[smokerId] = nextFont
        }
        return nextFont ?: getDefaultFont()
    }

    private fun getDefaultFont(): Typeface {
        if (defaultFont == null) {
            defaultFont = try {
                ResourcesCompat.getFont(context, R.font.sedgwick_ave_display)!!
            } catch (e: Exception) {
                Typeface.DEFAULT
            }
        }
        return defaultFont!!
    }

    fun getDefaultFontPublic(): Typeface {
        return getDefaultFont()
    }

    fun getFontLockStatusMessage(): String {
        return if (randomFontsEnabled) "Font randomization ON" else "Font randomization OFF"
    }

    fun getColorLockStatusMessage(): String {
        return if (colorChangingEnabled) "Color randomization UNLOCKED 🌈" else "Color LOCKED"
    }

    fun getFontAndColorLockStatusMessage(): String {
        return if (!randomFontsEnabled && !colorChangingEnabled) "Font & color LOCKED" else "Font & color UNLOCKED"
    }

    private fun vibrateFeedback(duration: Long) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(android.os.VibrationEffect.createOneShot(duration, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(duration)
        }
    }

    // Keep all your other existing functions unchanged below this line...
    fun formatSmokerNameWithStatus(smoker: Smoker): String {
        val smokerId = if (smoker.isCloudSmoker) smoker.cloudUserId else "local_${smoker.smokerId}"

        return buildString {
            append(smoker.name)

            if (currentShareCode != null) {
                if (awaySmokers.contains(smoker.cloudUserId)) {
                    append(" 💤")
                }
            }

            if (smoker.isCloudSmoker) append(" ☁️")

            if (smoker.passwordHash != null) {
                append(" 🔒")
                if (!smoker.isPasswordVerified) append(" ❓")
            }
        }
    }

    fun getSmokerAlpha(smokerId: String?, cloudUserId: String?): Float {
        return if (currentShareCode != null &&
            (pausedSmokerIds.contains(smokerId) || awaySmokers.contains(cloudUserId))) {
            0.5f
        } else {
            1.0f
        }
    }

    fun setupSmokerDropdownButtons(
        smoker: Smoker,
        btnDelete: ImageButton,
        btnSync: ImageButton,
        btnEdit: ImageButton,
        btnPassword: ImageButton,
        btnPausePlay: ImageButton,
        syncDot: View,
        dismissDropdown: () -> Unit
    ) {
        val currentUserId = authManager.getCurrentUserId()
        val isCurrentUser = smoker.isCloudSmoker && smoker.cloudUserId == currentUserId
        val isLocalSmoker = !smoker.isCloudSmoker
        val isOtherCloudSmoker = smoker.isCloudSmoker && smoker.cloudUserId != currentUserId
        val smokerId = if (smoker.isCloudSmoker) smoker.cloudUserId else "local_${smoker.smokerId}"
        val isSmokerPaused = pausedSmokerIds.contains(smokerId)

        btnPassword.setColorFilter(ContextCompat.getColor(context, R.color.neon_orange))
        btnSync.setColorFilter(ContextCompat.getColor(context, R.color.neon_purple))
        btnEdit.setColorFilter(ContextCompat.getColor(context, R.color.neon_yellow))
        btnDelete.setColorFilter(ContextCompat.getColor(context, R.color.neon_red))
        btnPausePlay.setColorFilter(ContextCompat.getColor(context, R.color.my_light_primary))

        btnPausePlay.visibility = if (currentShareCode != null) View.VISIBLE else View.GONE
        if (isSmokerPaused) {
            btnPausePlay.setImageResource(android.R.drawable.ic_media_play)
            btnPausePlay.contentDescription = "Resume ${smoker.name}"
        } else {
            btnPausePlay.setImageResource(android.R.drawable.ic_media_pause)
            btnPausePlay.contentDescription = "Pause ${smoker.name}"
        }
        btnPausePlay.setOnClickListener {
            dismissDropdown()
            onTogglePause?.invoke(smoker)
        }

        btnSync.visibility = when {
            isCurrentUser -> View.VISIBLE
            isOtherCloudSmoker -> View.VISIBLE
            else -> View.GONE
        }

        if (isCurrentUser) {
            btnSync.setImageResource(android.R.drawable.ic_popup_sync)
            btnSync.contentDescription = "Sync name from Google account"
        } else if (isOtherCloudSmoker) {
            btnSync.setImageResource(android.R.drawable.ic_menu_rotate)
            btnSync.contentDescription = "Refresh name from cloud"
        }

        btnSync.setOnClickListener {
            dismissDropdown()
            if (isCurrentUser) {
                onSyncCloudSmoker?.invoke(smoker)
            } else if (isOtherCloudSmoker) {
                onRefreshCloudSmokerName?.invoke(smoker)
            }
        }

        btnEdit.visibility = if (isLocalSmoker || isCurrentUser) View.VISIBLE else View.GONE
        btnEdit.setOnClickListener {
            dismissDropdown()
            onEditSmoker?.invoke(smoker)
        }

        btnPassword.visibility = if (smoker.isCloudSmoker && smoker.isOwner) View.VISIBLE else View.GONE
        btnPassword.setOnClickListener {
            dismissDropdown()
            onChangePassword?.invoke(smoker)
        }

        btnDelete.setOnClickListener {
            dismissDropdown()
            onDeleteSmoker?.invoke(smoker)
        }

        onUpdateSyncStatusDot?.invoke(syncDot, smoker)
    }

    fun dismissSpinnerDropDown() {
        try {
            val spinner = (context as? MainActivity)?.findViewById<Spinner>(R.id.spinnerSmoker)
            spinner?.let {
                val popupField = it.javaClass.getDeclaredField("mPopup").apply { isAccessible = true }
                val popup = popupField.get(it)
                if (popup is ListPopupWindow) popup.dismiss()
            }
        } catch (_: Exception) {
        }
    }

    suspend fun addSmoker(smoker: Smoker): Long {
        return withContext(Dispatchers.IO) {
            repository.insertSmoker(smoker)
        }
    }

    suspend fun updateSmoker(smoker: Smoker) {
        withContext(Dispatchers.IO) {
            repository.updateSmoker(smoker)
        }
    }

    suspend fun deleteSmoker(smoker: Smoker) {
        withContext(Dispatchers.IO) {
            repository.deleteSmoker(smoker)
        }
    }

    fun syncLocalSmokersToRoom(shareCode: String, localSmokers: List<Smoker>) {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch
            sessionSyncService.syncLocalSmokersToRoom(userId, shareCode, localSmokers)
        }
    }
    
    fun setFontForSmoker(smokerId: Long, font: Typeface) {
        smokerFontMap[smokerId] = font
        Log.d(TAG, "🔤 Set font for smoker $smokerId")
    }
    
    fun setColorForSmoker(smokerId: Long, color: Int) {
        smokerColorMap[smokerId] = color
        Log.d(TAG, "🎨 Set color for smoker $smokerId: $color")
    }
    
    fun getFontIndexForSmoker(smokerId: Long): Int {
        // If we have a cached index, return it
        val cachedIndex = smokerFontIndexMap[smokerId]
        if (cachedIndex != null) {
            return cachedIndex
        }
        
        // If font is locked, return the global locked index
        if (!randomFontsEnabled && globalLockedFontIndex != -1) {
            smokerFontIndexMap[smokerId] = globalLockedFontIndex
            return globalLockedFontIndex
        }
        
        // Try to find the index from the current font
        val currentFont = smokerFontMap[smokerId]
        if (currentFont != null) {
            for (i in fontList.indices) {
                try {
                    val testFont = ResourcesCompat.getFont(context, fontList[i])
                    if (testFont == currentFont) {
                        smokerFontIndexMap[smokerId] = i
                        return i
                    }
                } catch (e: Exception) {
                    // Continue checking other fonts
                }
            }
        }
        
        // Default to 0 if nothing found
        return 0
    }
    
    fun setFontIndexForSmoker(smokerId: Long, index: Int) {
        smokerFontIndexMap[smokerId] = index
        // Also update the font itself
        try {
            val font = ResourcesCompat.getFont(context, fontList.getOrElse(index) { fontList[0] })
            if (font != null) {
                smokerFontMap[smokerId] = font
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting font index $index for smoker $smokerId", e)
        }
    }
    
    fun setGlobalLockedFontIndex(index: Int) {
        globalLockedFontIndex = index
        try {
            globalLockedFont = ResourcesCompat.getFont(context, fontList.getOrElse(index) { fontList[0] })
        } catch (e: Exception) {
            Log.e(TAG, "Error setting global locked font index $index", e)
        }
    }
    
    fun setGlobalLockedColor(color: Int) {
        globalLockedColor = color
        Log.d(TAG, "Set global locked color: $color")
    }
}
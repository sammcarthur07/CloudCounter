package com.sam.cloudcounter

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import androidx.core.content.ContextCompat

/**
 * Animates a TextView using a Handler, which is more robust against UI refresh cycles.
 *
 * @param textView The TextView to animate.
 * @param fontResIds A list of font resource IDs to cycle through.
 */
class ShimmerTextAnimation(private val textView: TextView, private val fontResIds: List<Int>) {

    private val handler = Handler(Looper.getMainLooper())
    private var isRunning = false

    private val neonColors = intArrayOf(
        Color.parseColor("#98FB98"),  // Green (my_light_primary)
        Color.parseColor("#FFD700"),  // Yellow (neon_yellow)
        Color.parseColor("#FFA500"),  // Orange (neon_orange)
        Color.parseColor("#9370DB"),  // Purple (medium purple)
        Color.parseColor("#00CED1"),  // Blue/Teal (design_default_color_secondary)
    )

    init {
        // Start the animation cycle.
        start()
    }

    /**
     * This function applies the style and then reschedules itself to run again,
     * creating a continuous loop until stopShimmer() is called.
     */
    private fun runAnimationStep() {
        // The isRunning flag ensures that the loop breaks if stopShimmer() is called.
        if (!isRunning) return

        applyRandomStyle()
        // Schedule the next run of this same function.
        handler.postDelayed(::runAnimationStep, 3000L) // 3-second delay
    }

    private fun applyRandomStyle() {
        // Pick a truly random color and font.
        val randomColor = neonColors.random()
        textView.setTextColor(randomColor)

        if (fontResIds.isNotEmpty()) {
            val randomFontResId = fontResIds.random()
            try {
                val typeface = ResourcesCompat.getFont(textView.context, randomFontResId)
                textView.typeface = typeface
            } catch (e: Exception) {
                Log.e("ShimmerTextAnimation", "Failed to load font: $randomFontResId", e)
                textView.typeface = Typeface.DEFAULT
            }
        }
    }

    private fun start() {
        if (!isRunning) {
            isRunning = true
            // Post the first run to the handler. It will then reschedule itself.
            handler.post(::runAnimationStep)
        }
    }

    fun stopShimmer() {
        isRunning = false
        // By setting isRunning to false, we prevent runAnimationStep from posting again.
        // We also remove any pending posts to be safe.
        handler.removeCallbacks(::runAnimationStep)
    }
}
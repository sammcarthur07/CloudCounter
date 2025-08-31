package com.sam.cloudcounter

import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import nl.dionsegijn.konfetti.core.Party
import nl.dionsegijn.konfetti.core.Position
import nl.dionsegijn.konfetti.core.emitter.Emitter
import nl.dionsegijn.konfetti.core.models.Shape
import nl.dionsegijn.konfetti.core.models.Size
import nl.dionsegijn.konfetti.xml.KonfettiView
import java.util.concurrent.TimeUnit
import kotlin.math.cos
import kotlin.math.sin

class ConfettiHelper(private val context: Context) {

    private var konfettiView: KonfettiView? = null
    private var activity: MainActivity? = null

    /**
     * Initialize the konfetti overlay programmatically
     */
    fun setupKonfettiOverlay(activity: MainActivity) {
        this.activity = activity
        // Get the activity's content view (the root of everything)
        val decorView = activity.window.decorView as ViewGroup

        // Create KonfettiView programmatically
        konfettiView = KonfettiView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // Make it non-clickable so it doesn't interfere with buttons
            isClickable = false
            isFocusable = false
            // Make it transparent to touches
            setOnTouchListener { _, _ -> false }
        }

        // Add as overlay to the window's decor view (appears on top of everything)
        decorView.addView(konfettiView)
    }

    /**
     * Show mini confetti from a button (smaller effect for frequent actions)
     */
    fun showMiniConfettiFromButton(button: View) {
        val konfetti = konfettiView ?: return

        // Get button's position on screen
        val location = IntArray(2)
        button.getLocationOnScreen(location)

        // Get konfetti view's position
        val konfettiLocation = IntArray(2)
        konfetti.getLocationOnScreen(konfettiLocation)

        // Calculate relative position
        val x = (location[0] - konfettiLocation[0] + button.width / 2).toFloat()
        val y = (location[1] - konfettiLocation[1] + button.height / 2).toFloat()

        // Create mini confetti party from button position
        val party = Party(
            speed = 0f,
            maxSpeed = 20f,
            damping = 0.9f,
            spread = 30,
            colors = listOf(
                ContextCompat.getColor(context, R.color.my_light_primary),
                ContextCompat.getColor(context, android.R.color.holo_green_light),
                ContextCompat.getColor(context, android.R.color.holo_blue_light)
            ),
            emitter = Emitter(duration = 50, TimeUnit.MILLISECONDS).max(25),
            position = Position.Absolute(x, y)
        )

        konfetti.start(party)
    }

    /**
     * Show special confetti for overflow milestones (200%, 300%, etc.)
     */
    fun showOverflowMilestoneConfetti(milestonePercent: Int) {
        val konfetti = konfettiView ?: return

        // Create a special burst pattern for milestones
        val parties = mutableListOf<Party>()

        // Center burst
        val centerX = konfetti.width / 2f
        val centerY = konfetti.height / 2f

        // Create multiple bursts in a circle pattern
        for (angle in 0 until 360 step 45) {
            val radians = Math.toRadians(angle.toDouble())
            val burstX = centerX + (100 * cos(radians)).toFloat()
            val burstY = centerY + (100 * sin(radians)).toFloat()

            parties.add(Party(
                speed = 30f,
                maxSpeed = 50f,
                damping = 0.9f,
                angle = angle,
                spread = 60,
                colors = listOf(
                    Color.parseColor("#FFD700"), // Gold
                    Color.parseColor("#FFA500"), // Orange
                    Color.parseColor("#FF6347"), // Tomato
                    Color.parseColor("#98FB98")  // Your green
                ),
                emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(20),
                position = Position.Absolute(burstX, burstY),
                shapes = listOf(Shape.Square, Shape.Circle),
                size = listOf(Size.SMALL, Size.MEDIUM),
                timeToLive = 3000L
            ))
        }

        konfetti.start(parties)

        // Show the milestone number briefly
        android.widget.Toast.makeText(context, "🎉 ${milestonePercent}% Achieved! 🎉", android.widget.Toast.LENGTH_SHORT).show()
    }

    /**
     * Show success confetti (green burst from center)
     */
    fun showSuccessConfetti() {
        val konfetti = konfettiView ?: return

        // Get center of screen
        val centerX = konfetti.width / 2f
        val centerY = konfetti.height / 2f

        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(
                Color.GREEN,
                Color.parseColor("#90EE90"),
                Color.parseColor("#00FF00")
            ),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(50),
            position = Position.Absolute(centerX, centerY),
            shapes = listOf(Shape.Square, Shape.Circle),
            size = listOf(Size.SMALL, Size.MEDIUM)
        )

        konfetti.start(party)
    }

    /**
     * Show celebration fountains that reach the top of screen and float down
     */
    fun showRainConfetti() {
        val konfetti = konfettiView ?: return

        val parties = listOf(
            // Left fountain
            Party(
                speed = 40f,  // Reduced to reach just screen top
                maxSpeed = 45f,
                damping = 0.92f,  // Less damping for smoother arc
                angle = 265,  // Slightly angled
                spread = 30,
                colors = listOf(
                    Color.GREEN,
                    Color.parseColor("#90EE90"),
                    Color.YELLOW
                ),
                emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(75),
                position = Position.Relative(0.3, 1.0),  // Bottom left
                shapes = listOf(Shape.Square, Shape.Circle),
                size = listOf(Size.SMALL, Size.MEDIUM),
                timeToLive = 4000L
            ),
            // Center fountain - Main burst
            Party(
                speed = 45f,  // Just enough to reach screen top
                maxSpeed = 50f,
                damping = 0.92f,
                angle = 270,  // Straight up
                spread = 40,
                colors = listOf(
                    Color.GREEN,
                    Color.parseColor("#00FF00"),
                    Color.parseColor("#FFD700"),
                    Color.YELLOW
                ),
                emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(100),
                position = Position.Relative(0.5, 1.0),  // Bottom center
                shapes = listOf(Shape.Square, Shape.Circle),
                size = listOf(Size.SMALL, Size.MEDIUM, Size.LARGE),
                timeToLive = 4000L
            ),
            // Right fountain
            Party(
                speed = 40f,  // Reduced to reach just screen top
                maxSpeed = 45f,
                damping = 0.92f,
                angle = 275,  // Slightly angled
                spread = 30,
                colors = listOf(
                    Color.GREEN,
                    Color.parseColor("#32CD32"),
                    Color.YELLOW
                ),
                emitter = Emitter(duration = 150, TimeUnit.MILLISECONDS).max(75),
                position = Position.Relative(0.7, 1.0),  // Bottom right
                shapes = listOf(Shape.Square, Shape.Circle),
                size = listOf(Size.SMALL, Size.MEDIUM),
                timeToLive = 4000L
            )
        )

        konfetti.start(parties)
    }

    /**
     * Show confetti from a specific button location
     */
    fun showConfettiFromButton(button: View) {
        val konfetti = konfettiView ?: return

        // Get button's position on screen
        val location = IntArray(2)
        button.getLocationOnScreen(location)

        // Get konfetti view's position
        val konfettiLocation = IntArray(2)
        konfetti.getLocationOnScreen(konfettiLocation)

        // Calculate relative position
        val x = (location[0] - konfettiLocation[0] + button.width / 2).toFloat()
        val y = (location[1] - konfettiLocation[1] + button.height / 2).toFloat()

        // Create confetti party from button position
        val party = Party(
            speed = 0f,
            maxSpeed = 30f,
            damping = 0.9f,
            spread = 45,
            colors = listOf(
                ContextCompat.getColor(context, R.color.my_light_primary),
                ContextCompat.getColor(context, android.R.color.holo_green_light),
                ContextCompat.getColor(context, android.R.color.holo_orange_light),
                ContextCompat.getColor(context, android.R.color.holo_blue_light)
            ),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(50),
            position = Position.Absolute(x, y)
        )

        konfetti.start(party)
    }

    /**
     * Show a celebration burst (bigger effect)
     */
    /**
     * Show a celebration burst (bigger effect) with directional support
     * @param button The view to show confetti from
     * @param previousTabIndex Previous tab position (-1 if unknown)
     * @param currentTabIndex Current tab position
     */
    fun showCelebrationBurst(button: View, previousTabIndex: Int = -1, currentTabIndex: Int = -1) {
        val konfetti = konfettiView ?: return

        // Get button's position
        val location = IntArray(2)
        button.getLocationOnScreen(location)

        val konfettiLocation = IntArray(2)
        konfetti.getLocationOnScreen(konfettiLocation)

        val x = (location[0] - konfettiLocation[0] + button.width / 2).toFloat()
        val y = (location[1] - konfettiLocation[1] + button.height / 2).toFloat()

        // Determine confetti direction based on tab change
        val angle: Int
        val spread: Int

        when {
            previousTabIndex < 0 || currentTabIndex < 0 -> {
                // No direction info, use default omnidirectional
                angle = 0
                spread = 70
            }
            currentTabIndex > previousTabIndex -> {
                // Swiped left (moved to right tab), confetti shoots left
                angle = 180  // Left direction
                spread = 45
            }
            currentTabIndex < previousTabIndex -> {
                // Swiped right (moved to left tab), confetti shoots right
                angle = 0    // Right direction
                spread = 45
            }
            else -> {
                // Same tab (shouldn't happen but handle it)
                angle = 0
                spread = 70
            }
        }

        // Bigger celebration effect with directional support
        val party = Party(
            speed = 0f,
            maxSpeed = 50f,
            damping = 0.9f,
            angle = angle,
            spread = spread,
            colors = listOf(
                ContextCompat.getColor(context, R.color.my_light_primary),
                ContextCompat.getColor(context, android.R.color.holo_green_light),
                ContextCompat.getColor(context, android.R.color.holo_orange_light),
                ContextCompat.getColor(context, android.R.color.holo_blue_light),
                ContextCompat.getColor(context, android.R.color.holo_red_light),
                ContextCompat.getColor(context, android.R.color.holo_purple)
            ),
            emitter = Emitter(duration = 200, TimeUnit.MILLISECONDS).max(100),
            position = Position.Absolute(x, y)
        )

        konfetti.start(party)
    }

    // Keep the original function signature for backward compatibility
    fun showCelebrationBurst(button: View) {
        showCelebrationBurst(button, -1, -1)
    }

    /**
     * Show explosion confetti effect
     */
    fun showExplosionConfetti(view: View) {
        val konfetti = konfettiView ?: return

        val centerX = konfetti.width / 2f
        val centerY = konfetti.height / 2f

        val party = Party(
            speed = 0f,
            maxSpeed = 60f,
            damping = 0.9f,
            spread = 360,
            colors = listOf(
                Color.RED,
                Color.YELLOW,
                Color.parseColor("#FFA500"),
                Color.parseColor("#FF6347")
            ),
            emitter = Emitter(duration = 100, TimeUnit.MILLISECONDS).max(100),
            position = Position.Absolute(centerX, centerY),
            shapes = listOf(Shape.Square, Shape.Circle),
            size = listOf(Size.SMALL, Size.MEDIUM, Size.LARGE)
        )

        konfetti.start(party)
    }

    /**
     * Show REDUCED mini confetti burst from progress bar tip
     * Direction changes based on progress percentage (follows the circle)
     */
    fun showProgressTipConfetti(x: Float, y: Float, parentView: View) {
        val konfetti = konfettiView ?: return

        // Get konfetti view's position
        val konfettiLocation = IntArray(2)
        konfetti.getLocationOnScreen(konfettiLocation)

        // The x,y passed in are already in screen coordinates
        // Calculate relative position to konfetti view
        val relativeX = x - konfettiLocation[0]
        val relativeY = y - konfettiLocation[1]

        // Get the progress percentage from the parent view if it's a CustomCircularProgressBar
        val progressBar = parentView as? com.sam.cloudcounter.CustomCircularProgressBar
        val currentProgress = progressBar?.getProgress() ?: 0

        // Calculate the confetti direction based on progress
        // Progress bar starts at top (270°) and goes clockwise
        // 0% = top (270°), 25% = right (0°), 50% = bottom (90°), 75% = left (180°), 100% = top (270°)
        val progressAngle = (currentProgress % 100) * 3.6f // Convert percentage to degrees (0-360)
        val confettiAngle = (270f + progressAngle) % 360f // Start from top and rotate clockwise

        // Create MUCH SMALLER directional confetti
        val party = Party(
            speed = 15f,          // REDUCED from 30f - gentler initial speed
            maxSpeed = 20f,       // REDUCED from 40f - lower max speed
            damping = 0.85f,      // Faster slowdown
            angle = confettiAngle.toInt(), // Direction based on progress position
            spread = 25,          // REDUCED from 45 - tighter cone
            colors = listOf(
                Color.parseColor("#FFD700"), // Gold
                Color.parseColor("#FFA500"), // Orange
                Color.parseColor("#98FB98")  // Your main green
            ),
            emitter = Emitter(duration = 20, TimeUnit.MILLISECONDS).max(3), // REDUCED from 8 particles to 3
            position = Position.Absolute(relativeX, relativeY),
            shapes = listOf(Shape.Square, Shape.Circle),
            size = listOf(Size.SMALL), // Only small size
            timeToLive = 1500L    // Shorter life
        )

        konfetti.start(party)
    }

    /**
     * Cleanup when activity is destroyed
     */
    fun cleanup() {
        konfettiView?.let { view ->
            (view.parent as? ViewGroup)?.removeView(view)
        }
        konfettiView = null
        activity = null
    }
}
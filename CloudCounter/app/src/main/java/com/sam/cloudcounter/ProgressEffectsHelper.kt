package com.sam.cloudcounter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class ProgressEffectsHelper(private val context: Context) {

    companion object {
        private const val TAG = "ProgressEffects"
        private const val PARTICLE_DURATION = 2000L // 2 seconds per particle
        private const val PARTICLE_SIZE = 2 // Very small - 2dp
        private const val SPAWN_RATE = 30L // Spawn particles every 30ms
        private const val MIN_PARTICLES = 3 // Min particles per spawn
        private const val MAX_PARTICLES = 6 // Max particles per spawn
    }

    private val handler = Handler(Looper.getMainLooper())
    private var continuousSpawnRunnable: Runnable? = null
    private var rootContainer: ViewGroup? = null
    private val activeAnimators = mutableListOf<ValueAnimator>()
    private val activeParticles = mutableListOf<View>()

    /**
     * Starts continuous particle spawning during animation
     */
    fun startContinuousParticles(
        parentView: ViewGroup,
        progressView: CustomCircularProgressBar,
        duration: Long = 5000L
    ) {
        Log.d(TAG, "Starting continuous particle spawning for ${duration}ms")

        // Stop any existing animations first
        stopContinuousParticles()

        // Find the root container
        rootContainer = findRootContainer(parentView)

        if (rootContainer == null) {
            Log.e(TAG, "Could not find root container!")
            return
        }

        // Disable clipping on all parent views
        var currentParent: ViewGroup? = parentView
        while (currentParent != null) {
            currentParent.clipChildren = false
            currentParent.clipToPadding = false
            currentParent = currentParent.parent as? ViewGroup
        }

        var elapsedTime = 0L

        continuousSpawnRunnable = object : Runnable {
            override fun run() {
                if (elapsedTime < duration) {
                    try {
                        // Get tip position
                        val (tipX, tipY) = progressView.getProgressTipPosition()

                        // Calculate position relative to root container
                        val location = IntArray(2)
                        progressView.getLocationInWindow(location)

                        val rootLocation = IntArray(2)
                        rootContainer?.getLocationInWindow(rootLocation)

                        val finalX = location[0] - (rootLocation?.get(0) ?: 0) + tipX
                        val finalY = location[1] - (rootLocation?.get(1) ?: 0) + tipY

                        // Spawn random number of particles
                        val particleCount = Random.nextInt(MIN_PARTICLES, MAX_PARTICLES + 1)
                        repeat(particleCount) {
                            // Add small random offset to spawn position
                            val offsetX = Random.nextFloat() * 10 - 5
                            val offsetY = Random.nextFloat() * 10 - 5
                            createCurvedSpark(finalX + offsetX, finalY + offsetY)
                        }

                        elapsedTime += SPAWN_RATE
                        handler.postDelayed(this, SPAWN_RATE)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error spawning particles", e)
                    }
                } else {
                    Log.d(TAG, "Continuous particle spawning completed")
                    continuousSpawnRunnable = null
                }
            }
        }

        handler.post(continuousSpawnRunnable!!)
    }

    /**
     * Stops continuous particle spawning
     */
    fun stopContinuousParticles() {
        continuousSpawnRunnable?.let {
            handler.removeCallbacks(it)
            continuousSpawnRunnable = null
            Log.d(TAG, "Stopped continuous particle spawning")
        }

        // Clean up animators - make a copy to avoid concurrent modification
        val animatorsCopy = activeAnimators.toList()
        animatorsCopy.forEach { animator ->
            animator.cancel()
        }
        activeAnimators.clear()

        // Clean up particles
        val particlesCopy = activeParticles.toList()
        particlesCopy.forEach { particle ->
            (particle.parent as? ViewGroup)?.removeView(particle)
        }
        activeParticles.clear()
    }

    /**
     * Creates a single spark with curved trajectory
     */
    private fun createCurvedSpark(startX: Float, startY: Float) {
        rootContainer?.let { container ->
            // Create tiny pixel
            val spark = View(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    dpToPx(PARTICLE_SIZE),
                    dpToPx(PARTICLE_SIZE)
                )

                // Random fire color with emphasis on hot colors
                val colors = arrayOf(
                    "#FFFFFF", "#FFFFFF", "#FFFFFF", // White (hot) - highest chance
                    "#FFFF88", "#FFFF88", // Light yellow
                    "#FFFF00", "#FFFF00", // Yellow
                    "#FFA500", // Orange
                    "#FF6B00"  // Deep orange
                )
                setBackgroundColor(Color.parseColor(colors.random()))

                // Initial position
                x = startX
                y = startY

                // Maximum elevation
                elevation = 2000f
                z = 200000f

                // Start with high opacity
                alpha = 0.9f + Random.nextFloat() * 0.1f
            }

            // Add to root container
            container.addView(spark)
            spark.bringToFront()
            activeParticles.add(spark)

            // Create curved path animation
            animateCurvedPath(spark, container, startX, startY)
        }
    }

    /**
     * Animates a spark along a curved path
     */
    private fun animateCurvedPath(
        spark: View,
        container: ViewGroup,
        startX: Float,
        startY: Float
    ) {
        // Random initial direction within 30-degree cone upward
        val baseAngle = -90f // Straight up
        val angleVariation = 30f // ±15 degrees
        val initialAngle = baseAngle + (Random.nextFloat() - 0.5f) * angleVariation
        val initialAngleRad = Math.toRadians(initialAngle.toDouble())

        // Initial velocity (faster)
        val initialSpeed = 300f + Random.nextFloat() * 200f // 300-500 pixels per second

        // Random curve parameters
        val curveStrength = Random.nextFloat() * 0.8f + 0.2f
        val wobbleFrequency = 3f + Random.nextFloat() * 3f
        val wobbleAmplitude = 30f + Random.nextFloat() * 50f

        // Distance before starting to curve upward
        val straightDistance = 60f + Random.nextFloat() * 40f

        val animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = PARTICLE_DURATION

            var currentX = startX
            var currentY = startY
            var velocityX = (cos(initialAngleRad) * initialSpeed).toFloat()
            var velocityY = (sin(initialAngleRad) * initialSpeed).toFloat()

            addUpdateListener { animation ->
                val progress = animation.animatedValue as Float
                val deltaTime = 0.016f // Assume 60fps

                // Calculate distance traveled
                val time = progress * PARTICLE_DURATION / 1000f
                val distanceTraveled = initialSpeed * time

                // Gradually curve toward vertical after initial straight section
                if (distanceTraveled > straightDistance) {
                    val curveProgress = ((distanceTraveled - straightDistance) / 200f).coerceIn(0f, 1f)

                    // Curve toward vertical
                    velocityX *= (1f - curveProgress * 0.03f)
                    velocityY = -initialSpeed * (1f + curveProgress * 0.5f) // Accelerate upward
                }

                // Add wobble that increases with height
                val wobbleX = (sin(time.toDouble() * wobbleFrequency) * wobbleAmplitude * (0.5f + progress)).toFloat()
                val wobbleY = (cos(time.toDouble() * wobbleFrequency * 0.7) * wobbleAmplitude * 0.3f * progress).toFloat()

                // Update position
                currentX += (velocityX * deltaTime) + (wobbleX * deltaTime)
                currentY += (velocityY * deltaTime) + (wobbleY * deltaTime)

                spark.x = currentX
                spark.y = currentY

                // Fade out gradually
                spark.alpha = when {
                    progress < 0.6f -> 0.9f // Stay bright
                    progress < 0.85f -> 0.9f - ((progress - 0.6f) * 3f) // Start fading
                    else -> (1f - progress) * 6f // Quick fade at end
                }

                // Subtle flicker
                if (Random.nextFloat() < 0.05f) {
                    spark.alpha *= (0.7f + Random.nextFloat() * 0.3f)
                }
            }

            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    // Remove from tracking lists
                    activeAnimators.remove(animation as? ValueAnimator)
                    activeParticles.remove(spark)
                    // Remove from view
                    container.removeView(spark)
                }

                override fun onAnimationCancel(animation: android.animation.Animator) {
                    // Clean up on cancel too
                    activeAnimators.remove(animation as? ValueAnimator)
                    activeParticles.remove(spark)
                    container.removeView(spark)
                }
            })
        }

        activeAnimators.add(animator)
        animator.start()
    }

    /**
     * Creates ember burst for milestones
     */
    fun createEmberBurst(
        parentView: ViewGroup,
        x: Float,
        y: Float,
        count: Int = 40
    ) {
        rootContainer = findRootContainer(parentView)
        rootContainer?.let { container ->
            // Calculate absolute position
            val location = IntArray(2)
            parentView.getLocationInWindow(location)

            val rootLocation = IntArray(2)
            container.getLocationInWindow(rootLocation)

            val absoluteX = location[0] - rootLocation[0] + x
            val absoluteY = location[1] - rootLocation[1] + y

            // Create staggered burst
            repeat(count) { i ->
                handler.postDelayed({
                    // Random position within burst radius
                    val angle = Random.nextFloat() * 360f
                    val radius = Random.nextFloat() * 25f
                    val offsetX = (cos(Math.toRadians(angle.toDouble())) * radius).toFloat()
                    val offsetY = (sin(Math.toRadians(angle.toDouble())) * radius).toFloat()

                    createCurvedSpark(absoluteX + offsetX, absoluteY + offsetY)
                }, (i * 15L)) // Stagger spawning
            }
        }
    }

    /**
     * Creates a pulsing glow effect
     */
    fun createGlowPulse(
        parentView: ViewGroup,
        progressView: View,
        currentProgress: Int
    ) {
        Log.d(TAG, "Creating glow pulse at $currentProgress%")

        rootContainer = findRootContainer(parentView)
        rootContainer?.let { container ->
            val location = IntArray(2)
            progressView.getLocationInWindow(location)

            val rootLocation = IntArray(2)
            container.getLocationInWindow(rootLocation)

            val centerX = location[0] - rootLocation[0] + progressView.width / 2f
            val centerY = location[1] - rootLocation[1] + progressView.height / 2f
            val radius = progressView.width / 2f - 30f

            val angle = Math.toRadians(-90.0 + (360.0 * currentProgress / 100))
            val glowX = centerX + (radius * cos(angle)).toFloat()
            val glowY = centerY + (radius * sin(angle)).toFloat()

            // Create burst at milestone
            createEmberBurst(parentView, glowX - location[0] + rootLocation[0],
                glowY - location[1] + rootLocation[1], 25)
        }
    }

    /**
     * Find the root container
     */
    private fun findRootContainer(view: View): ViewGroup? {
        var current = view.parent
        var lastValidContainer: ViewGroup? = null

        while (current != null) {
            if (current is ViewGroup) {
                lastValidContainer = current
                if (current.id == android.R.id.content) {
                    return current
                }
            }
            current = current.parent
        }

        return lastValidContainer
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
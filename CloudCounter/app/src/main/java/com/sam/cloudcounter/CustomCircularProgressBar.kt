package com.sam.cloudcounter

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import android.view.MotionEvent
import kotlin.math.abs


class CustomCircularProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "CustomCircularProgress"
        private const val ANIMATION_DURATION = 5000L // 5 seconds
        private const val FRAME_RATE = 60 // 60 FPS
        private const val FRAME_DELAY = 1000L / FRAME_RATE
        private const val GLOW_PADDING = 22 // Midway between 15 and 30
        private const val BASE_GLOW_INTENSITY = 0.35f // Midway between 0.3f and 0.4f
        private const val MAX_GLOW_INTENSITY = 0.675f // Midway between 0.45f and 0.9f

        // STATIC tracking of all goals' actual progress values
        // This persists across view recycling
        private val GLOBAL_GOAL_PROGRESS = mutableMapOf<Long, Int>()

        // Track which goals have been displayed at least once
        private val GOALS_DISPLAYED = mutableSetOf<Long>()
    }

    private var targetProgress = 0
    private var displayProgress = 0f
    private var allowOverflow = true
    private val strokeWidth = 20f

    // touch confetti
    private var touchHandler: Handler? = null
    private var touchConfettiRunnable: Runnable? = null
    private var onTouchConfetti: ((Float, Float) -> Unit)? = null
    private var isTouching = false

    // Manual animation handling
    private var animationStartTime = 0L
    private var animationFromValue = 0f
    private var animationToValue = 0f
    private var isAnimating = false
    private val animationHandler = Handler(Looper.getMainLooper())
    private var animationRunnable: Runnable? = null

    // Track animation state more robustly
    private var currentAnimationId = 0L
    private var boundToGoalId = -1L

    // Burning effect variables - ENHANCED GLOW SYSTEM
    private var glowIntensity = BASE_GLOW_INTENSITY
    private var glowAnimator: ValueAnimator? = null
    private var shouldShowGlow = true // Always show some level of glow

    // Colors
    private val blueColor = Color.parseColor("#2196F3")
    private val greenColor = Color.parseColor("#4CAF50")
    private val orangeColor = Color.parseColor("#FF9800")

    // Burning effect colors
    private val burnYellow = Color.parseColor("#FFEB3B")
    private val burnOrange = Color.parseColor("#FF9800")
    private val burnRed = Color.parseColor("#FF5722")
    private val burnWhite = Color.parseColor("#FFFFFF")

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = this@CustomCircularProgressBar.strokeWidth
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val burnPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = this@CustomCircularProgressBar.strokeWidth + 10f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val tipPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
        textSize = 36f
        color = Color.parseColor("#4CAF50")
    }

    private val rect = RectF()
    private val burnRect = RectF()

    // Callbacks
    var onProgressAnimating: ((Float, Boolean) -> Unit)? = null
    var onAnimationStart: (() -> Unit)? = null
    var onAnimationEnd: (() -> Unit)? = null

    private val interpolator = DecelerateInterpolator(1.5f)

    init {
        // IMPORTANT: Disable hardware acceleration for this view to ensure glow renders properly
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        // Ensure this view draws on top
        z = 10f
        elevation = 10f
        // Start with base glow
        glowIntensity = BASE_GLOW_INTENSITY
    }

    // Override onMeasure to add padding for the glow
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = MeasureSpec.getSize(heightMeasureSpec)

        // Ensure the view is square
        val size = min(desiredWidth, desiredHeight)
        setMeasuredDimension(size, size)

        // Set padding to allow glow to be visible
        setPadding(GLOW_PADDING, GLOW_PADDING, GLOW_PADDING, GLOW_PADDING)
    }

    fun setBoundGoalId(goalId: Long) {
        if (boundToGoalId != goalId) {
            Log.d(TAG, "🔄 BINDING: View switching from goal $boundToGoalId to goal $goalId")

            // If we're switching goals, stop any animation immediately
            if (isAnimating) {
                Log.d(TAG, "   ⏹️ Stopping animation for old goal $boundToGoalId")
                stopAnimation()
            }

            // CRITICAL: Reset display progress when binding to a new goal
            // This ensures recycled views don't show the wrong progress
            if (boundToGoalId != -1L && boundToGoalId != goalId) {
                // View is being recycled for a different goal
                val newGoalProgress = GLOBAL_GOAL_PROGRESS[goalId]
                if (newGoalProgress != null) {
                    displayProgress = newGoalProgress.toFloat()
                    Log.d(TAG, "   🔄 RECYCLED: Reset display to $displayProgress for goal $goalId")
                } else {
                    displayProgress = 0f
                    Log.d(TAG, "   🔄 RECYCLED: Reset display to 0 for new goal $goalId")
                }
                invalidate()
            }

            boundToGoalId = goalId
        }
    }

    fun isCurrentlyAnimating(): Boolean = isAnimating

    fun getCurrentAnimationTarget(): Float = if (isAnimating) animationToValue else -1f

    fun setProgress(progress: Int, allowOverflow: Boolean = false, animate: Boolean = true, isUndo: Boolean = false) {
        Log.d(TAG, "📊 SET_PROGRESS: Goal=$boundToGoalId, NewProgress=$progress, Animate=$animate, IsUndo=$isUndo, CurrentDisplay=$displayProgress, IsAnimating=$isAnimating")

        // CRITICAL: Don't interrupt if we're already at the right value or animating to it
        if (abs(displayProgress - progress) < 0.5f) {
            Log.d(TAG, "   ✅ Already at progress $progress, skipping")
            return
        }

        if (isAnimating && abs(animationToValue - progress) < 0.5f) {
            Log.d(TAG, "   🎯 Already animating to $progress, not interrupting")
            return
        }

        this.allowOverflow = allowOverflow
        this.targetProgress = progress

        // Get the last known progress for this goal from global tracking
        val lastKnownProgress = GLOBAL_GOAL_PROGRESS[boundToGoalId]
        val progressChanged = lastKnownProgress != null && lastKnownProgress != progress

        Log.d(TAG, "   📍 LastKnown=$lastKnownProgress, Changed=$progressChanged")

        // Update global tracking BEFORE processing
        val previousProgress = GLOBAL_GOAL_PROGRESS[boundToGoalId]
        GLOBAL_GOAL_PROGRESS[boundToGoalId] = progress
        GOALS_DISPLAYED.add(boundToGoalId)

        when {
            previousProgress == null -> {
                // First time we're tracking this goal's progress
                if (animate && progress > 0 && displayProgress < 1f) {
                    // This is likely a new goal getting its first activity
                    Log.d(TAG, "   🎯 NEW GOAL FIRST ACTIVITY: Animating 0 → $progress")
                    displayProgress = 0f
                    startManualAnimation(0f, progress.toFloat())
                } else {
                    // Just set it directly (e.g., restoring from saved state)
                    Log.d(TAG, "   📌 INITIAL SET: Goal $boundToGoalId to $progress")
                    displayProgress = progress.toFloat()
                    invalidate()
                }
            }

            previousProgress == progress -> {
                // Same progress value being set again
                Log.d(TAG, "   ⏸️ NO CHANGE: Progress still at $progress")
                // Make sure display matches if it's off (but don't stop animations)
                if (!isAnimating && abs(displayProgress - progress) > 0.5f) {
                    Log.d(TAG, "      🔧 Fixing display from $displayProgress to $progress")
                    displayProgress = progress.toFloat()
                    invalidate()
                }
            }

            // MODIFIED: Add condition for UNDO animation (progress decreased)
            animate && isUndo && previousProgress > progress && (previousProgress - progress) <= 50 -> {
                // Progress decreased due to undo and we should animate backwards
                Log.d(TAG, "   ↩️ UNDO ANIMATING: $previousProgress → $progress (${previousProgress - progress} decrease)")

                // Make sure we start from the right place
                if (abs(displayProgress - previousProgress) > 1f) {
                    Log.d(TAG, "      🔧 Correcting start position from $displayProgress to $previousProgress")
                    displayProgress = previousProgress.toFloat()
                }

                startManualAnimation(displayProgress, progress.toFloat(), isReverse = true)
            }

            animate && previousProgress < progress && (progress - previousProgress) <= 50 -> {
                // Progress increased and we should animate (small increase only)
                Log.d(TAG, "   ✅ ANIMATING: $previousProgress → $progress (${progress - previousProgress} increase)")

                // Make sure we start from the right place
                if (abs(displayProgress - previousProgress) > 1f) {
                    Log.d(TAG, "      🔧 Correcting start position from $displayProgress to $previousProgress")
                    displayProgress = previousProgress.toFloat()
                }

                startManualAnimation(displayProgress, progress.toFloat())
            }

            else -> {
                // Large change, decrease (non-undo), or non-animated update
                Log.d(TAG, "   ⏭️ JUMPING: Direct set to $progress (from $previousProgress)")
                stopAnimation()
                displayProgress = progress.toFloat()
                invalidate()
            }
        }

        // Log final state
        Log.d(TAG, "   📈 RESULT: Display=$displayProgress, Animating=$isAnimating")

        // Ensure base glow (no throbbing)
        if (!isAnimating) {
            glowIntensity = BASE_GLOW_INTENSITY
        }
    }

    private fun startManualAnimation(from: Float, to: Float, isReverse: Boolean = false) {
        Log.d(TAG, "🎬 STARTING ANIMATION: Goal $boundToGoalId from $from to $to${if (isReverse) " (REVERSE)" else ""}")

        // Stop any existing animation first
        stopAnimation()

        // Generate a unique animation ID for this animation
        currentAnimationId = System.currentTimeMillis()
        val thisAnimationId = currentAnimationId
        val thisGoalId = boundToGoalId

        // Start with base glow for animation
        glowIntensity = BASE_GLOW_INTENSITY

        // Store animation parameters
        animationStartTime = System.currentTimeMillis()
        animationFromValue = from
        animationToValue = to
        isAnimating = true

        // Notify start
        onAnimationStart?.invoke()

        // Create animation runnable
        animationRunnable = object : Runnable {
            override fun run() {
                // Check if this is still the current animation AND we're still bound to the same goal
                if (!isAnimating || currentAnimationId != thisAnimationId || boundToGoalId != thisGoalId) {
                    Log.d(TAG, "   ⏹️ Animation cancelled (goal changed or stopped)")
                    return
                }

                val elapsed = System.currentTimeMillis() - animationStartTime

                if (elapsed >= ANIMATION_DURATION) {
                    // Animation complete
                    displayProgress = animationToValue
                    isAnimating = false
                    // Keep slightly elevated glow after animation
                    glowIntensity = BASE_GLOW_INTENSITY * 1.2f
                    Log.d(TAG, "   ✅ ANIMATION COMPLETE: Goal $boundToGoalId reached ${displayProgress.toInt()}%")
                    onAnimationEnd?.invoke()
                    invalidate()
                } else {
                    // Calculate progress
                    val rawFraction = elapsed.toFloat() / ANIMATION_DURATION
                    val interpolatedFraction = interpolator.getInterpolation(rawFraction)

                    displayProgress = animationFromValue + (animationToValue - animationFromValue) * interpolatedFraction

                    // For reverse animations, use a different glow pattern (optional)
                    if (isReverse) {
                        // Subtle pulsing for reverse animation
                        val timeInSeconds = elapsed / 1000f
                        glowIntensity = BASE_GLOW_INTENSITY * (1f + 0.2f * sin(timeInSeconds * Math.PI * 3).toFloat())
                    } else {
                        // Normal throbbing glow for forward animation
                        val timeInSeconds = elapsed / 1000f
                        val baseIntensity = when {
                            timeInSeconds < 0.5f -> {
                                BASE_GLOW_INTENSITY + (timeInSeconds / 0.5f) * (MAX_GLOW_INTENSITY - BASE_GLOW_INTENSITY)
                            }
                            timeInSeconds < 4.0f -> {
                                MAX_GLOW_INTENSITY
                            }
                            else -> {
                                val fadeProgress = (timeInSeconds - 4.0f) / 1.0f
                                MAX_GLOW_INTENSITY - (fadeProgress * (MAX_GLOW_INTENSITY - BASE_GLOW_INTENSITY * 1.2f))
                            }
                        }.coerceIn(BASE_GLOW_INTENSITY, MAX_GLOW_INTENSITY)

                        glowIntensity = if (timeInSeconds in 0.5f..4.0f) {
                            val throb = sin(timeInSeconds * Math.PI * 2).toFloat()
                            val throbAmount = 0.1f
                            (baseIntensity + throb * throbAmount * baseIntensity).coerceIn(BASE_GLOW_INTENSITY, MAX_GLOW_INTENSITY)
                        } else {
                            baseIntensity
                        }
                    }

                    // Callback
                    onProgressAnimating?.invoke(displayProgress, !isReverse)

                    // Log progress every second
                    if (elapsed % 1000 < FRAME_DELAY) {
                        Log.d(TAG, "   🔄 ANIMATING${if (isReverse) " (REVERSE)" else ""}: Goal $boundToGoalId at ${displayProgress.toInt()}% (${elapsed}ms)")
                    }

                    // Invalidate and schedule next frame
                    invalidate()
                    animationHandler.postDelayed(this, FRAME_DELAY)
                }
            }
        }

        // Start the animation loop
        animationHandler.post(animationRunnable!!)
    }

    private fun stopAnimation() {
        if (isAnimating) {
            Log.d(TAG, "⏹️ STOPPING animation for goal $boundToGoalId at ${displayProgress.toInt()}%")
        }
        animationRunnable?.let {
            animationHandler.removeCallbacks(it)
            animationRunnable = null
        }
        isAnimating = false
        // Increment animation ID to invalidate any pending animations
        currentAnimationId++
    }

    // Override parent to prevent clipping of glow
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        Log.d(TAG, "📎 View ATTACHED for goal $boundToGoalId")

        // Ensure parent doesn't clip our glow
        val parentView = parent as? ViewGroup
        parentView?.clipChildren = false
        parentView?.clipToPadding = false

        // Set steady base glow
        glowIntensity = BASE_GLOW_INTENSITY
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Log.d(TAG, "🔌 View DETACHED for goal $boundToGoalId")

        if (isAnimating) {
            stopAnimation()
        }

        glowAnimator?.cancel()
    }

    fun getProgress(): Int = displayProgress.toInt()

    fun getProgressTipPosition(): Pair<Float, Float> {
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        val centerX = paddingLeft + availableWidth / 2f
        val centerY = paddingTop + availableHeight / 2f
        val radius = min(availableWidth, availableHeight) / 2f - strokeWidth

        // For overflow, keep rotating around the circle
        val effectiveProgress = if (displayProgress > 100) {
            displayProgress
        } else {
            displayProgress
        }

        // Calculate angle - allow it to go beyond 360 degrees for multiple rotations
        val angle = Math.toRadians(-90.0 + (360.0 * effectiveProgress / 100))
        val x = centerX + (radius * cos(angle)).toFloat()
        val y = centerY + (radius * sin(angle)).toFloat()

        return Pair(x, y)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        val centerX = paddingLeft + availableWidth / 2f
        val centerY = paddingTop + availableHeight / 2f
        val radius = min(availableWidth, availableHeight) / 2f - strokeWidth

        rect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        burnRect.set(
            centerX - radius - 5,
            centerY - radius - 5,
            centerX + radius + 5,
            centerY + radius + 5
        )

        when {
            displayProgress == 0f -> {
                paint.color = blueColor
                canvas.drawCircle(centerX, centerY, radius, paint)
            }
            displayProgress <= 100 -> {
                val greenAngle = 360f * displayProgress / 100
                val blueAngle = 360f - greenAngle

                if (blueAngle > 0) {
                    paint.color = blueColor
                    canvas.drawArc(rect, -90f + greenAngle, blueAngle, false, paint)
                }

                if (greenAngle > 0) {
                    paint.color = greenColor
                    canvas.drawArc(rect, -90f, greenAngle, false, paint)

                    // Show glow if we have progress
                    if (shouldShowGlow && glowIntensity >= BASE_GLOW_INTENSITY * 0.8f) {
                        drawBurningEffect(canvas, centerX, centerY, radius, greenAngle)
                    }
                }
            }
            else -> {
                // For overflow, handle multiple rotations
                val fullRotations = (displayProgress / 100).toInt()
                val remainingProgress = displayProgress % 100

                // Draw the base color
                if (fullRotations > 0) {
                    paint.color = orangeColor
                    canvas.drawCircle(centerX, centerY, radius, paint)
                }

                // Draw the current progress on top
                if (remainingProgress > 0) {
                    val overflowAngle = 360f * remainingProgress / 100
                    paint.color = orangeColor
                    canvas.drawArc(rect, -90f, overflowAngle, false, paint)
                }

                // Show burning effect for overflow
                if (shouldShowGlow && glowIntensity >= BASE_GLOW_INTENSITY * 0.8f) {
                    val tipAngle = 360f * displayProgress / 100
                    drawBurningEffectForOverflow(canvas, centerX, centerY, radius, tipAngle)
                }
            }
        }

        // Draw the text - this always shows the current displayProgress
        canvas.drawText(
            "${displayProgress.toInt()}%",
            centerX,
            centerY + textPaint.textSize / 3,
            textPaint
        )
    }

    fun setOnTouchConfetti(callback: (Float, Float) -> Unit) {
        onTouchConfetti = callback
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val centerX = width / 2f
                val centerY = height / 2f
                val radius = min(width, height) / 2f

                val distance = kotlin.math.sqrt(
                    (event.x - centerX) * (event.x - centerX) +
                            (event.y - centerY) * (event.y - centerY)
                )

                if (distance <= radius) {
                    startTouchConfetti()
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                stopTouchConfetti()
            }
        }
        return super.onTouchEvent(event)
    }

    private fun startTouchConfetti() {
        if (isTouching) return

        isTouching = true
        touchHandler = Handler(Looper.getMainLooper())

        touchConfettiRunnable = object : Runnable {
            override fun run() {
                if (!isTouching) return

                val (currentTipX, currentTipY) = getProgressTipPosition()
                onTouchConfetti?.invoke(currentTipX, currentTipY)

                touchHandler?.postDelayed(this, 150)
            }
        }

        touchHandler?.post(touchConfettiRunnable!!)
    }

    private fun stopTouchConfetti() {
        isTouching = false
        touchConfettiRunnable?.let {
            touchHandler?.removeCallbacks(it)
        }
        touchConfettiRunnable = null
        touchHandler = null
    }

    private fun drawBurningEffectForOverflow(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        progressAngle: Float
    ) {
        val tipAngle = Math.toRadians(-90.0 + progressAngle.toDouble())
        val tipX = centerX + (radius * cos(tipAngle)).toFloat()
        val tipY = centerY + (radius * sin(tipAngle)).toFloat()

        // Midway intensity multiplier
        val intensityMultiplier = if (isAnimating) 1.85f else 1.2f

        // Glow layers - midway between previous sizes
        val glowLayers = listOf(
            Triple(30f, 0.5f, listOf(burnRed, Color.TRANSPARENT)),      // Midway: was 20f, originally 40f
            Triple(22.5f, 0.7f, listOf(burnOrange, burnRed, Color.TRANSPARENT)),  // Midway: was 15f, originally 30f
            Triple(16.5f, 0.85f, listOf(burnYellow, burnOrange, Color.TRANSPARENT)), // Midway: was 11f, originally 22f
            Triple(11.25f, 1f, listOf(burnWhite, burnYellow, Color.TRANSPARENT))     // Midway: was 7.5f, originally 15f
        )

        for ((size, intensity, colors) in glowLayers) {
            val glowRadius = size * glowIntensity * intensityMultiplier
            val alphaMultiplier = intensity * glowIntensity * intensityMultiplier

            // Skip if radius would be too small
            if (glowRadius < 2f) continue

            val gradient = RadialGradient(
                tipX, tipY, glowRadius.coerceAtLeast(2f),
                colors.map { adjustAlpha(it, (255 * alphaMultiplier).toInt().coerceIn(0, 255)) }.toIntArray(),
                FloatArray(colors.size) { it.toFloat() / (colors.size - 1) },
                Shader.TileMode.CLAMP
            )

            tipPaint.shader = gradient
            canvas.drawCircle(tipX, tipY, glowRadius, tipPaint)
        }
    }

    private fun drawBurningEffect(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        progressAngle: Float
    ) {
        val tipAngle = Math.toRadians(-90.0 + progressAngle.toDouble())
        val tipX = centerX + (radius * cos(tipAngle)).toFloat()
        val tipY = centerY + (radius * sin(tipAngle)).toFloat()

        // Midway intensity multiplier
        val intensityMultiplier = if (isAnimating) 1.85f else 1.2f

        // Glow layers - midway between previous sizes
        val glowLayers = listOf(
            Triple(30f, 0.5f, listOf(burnRed, Color.TRANSPARENT)),      // Midway: was 20f, originally 40f
            Triple(22.5f, 0.7f, listOf(burnOrange, burnRed, Color.TRANSPARENT)),  // Midway: was 15f, originally 30f
            Triple(16.5f, 0.85f, listOf(burnYellow, burnOrange, Color.TRANSPARENT)), // Midway: was 11f, originally 22f
            Triple(11.25f, 1f, listOf(burnWhite, burnYellow, Color.TRANSPARENT))     // Midway: was 7.5f, originally 15f
        )

        for ((size, intensity, colors) in glowLayers) {
            val glowRadius = size * glowIntensity * intensityMultiplier
            val alphaMultiplier = intensity * glowIntensity * intensityMultiplier

            // Skip if radius would be too small (prevents crash)
            if (glowRadius < 2f) continue

            val gradient = RadialGradient(
                tipX, tipY, glowRadius.coerceAtLeast(2f), // Ensure minimum radius of 2
                colors.map { adjustAlpha(it, (255 * alphaMultiplier).toInt().coerceIn(0, 255)) }.toIntArray(),
                FloatArray(colors.size) { it.toFloat() / (colors.size - 1) },
                Shader.TileMode.CLAMP
            )

            tipPaint.shader = gradient
            canvas.drawCircle(tipX, tipY, glowRadius, tipPaint)
        }
    }

    private fun adjustAlpha(color: Int, alpha: Int): Int {
        if (color == Color.TRANSPARENT) return color
        return Color.argb(
            alpha.coerceIn(0, 255),
            Color.red(color),
            Color.green(color),
            Color.blue(color)
        )
    }

    // Clean up old progress data for goals that no longer exist
    fun cleanupOldGoals(activeGoalIds: List<Long>) {
        val keysToRemove = GLOBAL_GOAL_PROGRESS.keys.filter { it !in activeGoalIds }
        keysToRemove.forEach {
            Log.d(TAG, "🗑️ CLEANUP: Removing old goal $it from tracking")
            GLOBAL_GOAL_PROGRESS.remove(it)
            GOALS_DISPLAYED.remove(it)
        }
    }

    // Debug method to see all tracked goals
    fun debugPrintAllGoals() {
        Log.d(TAG, "📊 ALL TRACKED GOALS: ${GLOBAL_GOAL_PROGRESS.entries.joinToString { "${it.key}=${it.value}%" }}")
        Log.d(TAG, "📊 DISPLAYED GOALS: ${GOALS_DISPLAYED.joinToString()}")
    }
}
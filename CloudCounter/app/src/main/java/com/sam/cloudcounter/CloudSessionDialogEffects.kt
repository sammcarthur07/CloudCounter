// Add this class to handle all the dramatic effects
package com.sam.cloudcounter

import android.animation.*
import android.app.Dialog
import android.content.Context
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlin.random.Random

class CloudSessionDialogEffects(
    private val dialog: Dialog,
    private val context: Context
) {
    private val particleViews = mutableListOf<View>()
    private val animators = mutableListOf<Animator>()
    private var effectJob: Job? = null

    fun startAllEffects() {
        val dialogView = dialog.findViewById<View>(android.R.id.content) ?: return
        val mainCard = dialogView.findViewById<View>(R.id.mainCard) ?: return
        val particleContainer = dialogView.findViewById<FrameLayout>(R.id.particleContainer) ?: return
        val electricSurge = dialogView.findViewById<View>(R.id.electricSurgeEffect) ?: return
        val dissolveEffect = dialogView.findViewById<View>(R.id.dissolveEffect) ?: return
        val animatedBorder = dialogView.findViewById<View>(R.id.animatedBorder) ?: return

        // Start particle burst effect
        startParticleBurst(particleContainer, mainCard)

        // Start electric surge animation
        startElectricSurge(electricSurge)

        // Start dissolve effect
        startDissolveEffect(dissolveEffect)

        // Start border animation
        startBorderAnimation(animatedBorder)

        // Start shimmer on option cards
        startShimmerEffects(dialogView)

        // Start title glow pulse
        dialogView.findViewById<View>(R.id.titleGlow)?.let { startTitleGlow(it) }

        // Start pulsing dots
        startPulsingDots(dialogView)
    }

    private fun startParticleBurst(container: FrameLayout, card: View) {
        effectJob = CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                // Create particles from all edges
                createEdgeParticles(container, card)
                delay(100) // New burst every 100ms
            }
        }
    }

    private fun createEdgeParticles(container: FrameLayout, card: View) {
        val particleCount = 8
        val cardRect = Rect()
        card.getGlobalVisibleRect(cardRect)

        for (i in 0 until particleCount) {
            val particle = createParticleView()
            container.addView(particle)
            particleViews.add(particle)

            // Randomly choose an edge
            val edge = Random.nextInt(4)
            val startX: Float
            val startY: Float
            val endX: Float
            val endY: Float

            when (edge) {
                0 -> { // Top edge
                    startX = Random.nextFloat() * card.width
                    startY = 0f
                    endX = startX + Random.nextFloat() * 200 - 100
                    endY = -200f
                }
                1 -> { // Right edge
                    startX = card.width.toFloat()
                    startY = Random.nextFloat() * card.height
                    endX = startX + 200f
                    endY = startY + Random.nextFloat() * 200 - 100
                }
                2 -> { // Bottom edge
                    startX = Random.nextFloat() * card.width
                    startY = card.height.toFloat()
                    endX = startX + Random.nextFloat() * 200 - 100
                    endY = startY + 200f
                }
                else -> { // Left edge
                    startX = 0f
                    startY = Random.nextFloat() * card.height
                    endX = -200f
                    endY = startY + Random.nextFloat() * 200 - 100
                }
            }

            animateParticle(particle, startX, startY, endX, endY)
        }
    }

    private fun createParticleView(): View {
        val particle = View(context)
        val size = Random.nextInt(4, 12)
        particle.layoutParams = FrameLayout.LayoutParams(size, size)

        // Create gradient drawable for particle
        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(context, R.color.my_light_primary))
        }
        particle.background = drawable
        particle.alpha = 0f

        return particle
    }

    private fun animateParticle(particle: View, startX: Float, startY: Float, endX: Float, endY: Float) {
        particle.x = startX
        particle.y = startY

        val animatorSet = AnimatorSet()

        // Movement animation
        val translateX = ObjectAnimator.ofFloat(particle, "x", startX, endX)
        val translateY = ObjectAnimator.ofFloat(particle, "y", startY, endY)

        // Fade animation
        val fadeIn = ObjectAnimator.ofFloat(particle, "alpha", 0f, 1f)
        fadeIn.duration = 200

        val fadeOut = ObjectAnimator.ofFloat(particle, "alpha", 1f, 0f)
        fadeOut.startDelay = 200
        fadeOut.duration = 800

        // Scale animation
        val scaleX = ObjectAnimator.ofFloat(particle, "scaleX", 0.5f, 1.5f)
        val scaleY = ObjectAnimator.ofFloat(particle, "scaleY", 0.5f, 1.5f)

        animatorSet.playTogether(translateX, translateY, fadeIn, fadeOut, scaleX, scaleY)
        animatorSet.duration = 1000
        animatorSet.interpolator = android.view.animation.AccelerateDecelerateInterpolator()

        animatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                (particle.parent as? ViewGroup)?.removeView(particle)
                particleViews.remove(particle)
            }
        })

        animatorSet.start()
        animators.add(animatorSet)
    }

    private fun startElectricSurge(electricView: View) {
        val rotateAnimator = ObjectAnimator.ofFloat(electricView, "rotation", 0f, 360f).apply {
            duration = 3000
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }

        val scaleXAnimator = ObjectAnimator.ofFloat(electricView, "scaleX", 1f, 1.1f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
        }

        val scaleYAnimator = ObjectAnimator.ofFloat(electricView, "scaleY", 1f, 1.1f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
        }

        val alphaAnimator = ObjectAnimator.ofFloat(electricView, "alpha", 0.3f, 0.8f, 0.3f).apply {
            duration = 1000
            repeatCount = ValueAnimator.INFINITE
        }

        AnimatorSet().apply {
            playTogether(rotateAnimator, scaleXAnimator, scaleYAnimator, alphaAnimator)
            start()
        }.also { animators.add(it) }
    }

    private fun startDissolveEffect(dissolveView: View) {
        // Create dissolve animation that moves pixels upward
        val translateY = ObjectAnimator.ofFloat(dissolveView, "translationY", 0f, -50f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
        }

        val alpha = ObjectAnimator.ofFloat(dissolveView, "alpha", 0f, 0.5f, 0f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
        }

        AnimatorSet().apply {
            playTogether(translateY, alpha)
            start()
        }.also { animators.add(it) }
    }

    private fun startBorderAnimation(borderView: View) {
        val animator = ObjectAnimator.ofFloat(borderView, "rotation", 0f, 360f).apply {
            duration = 10000
            repeatCount = ValueAnimator.INFINITE
            interpolator = android.view.animation.LinearInterpolator()
        }
        animator.start()
        animators.add(animator)
    }

    private fun startShimmerEffects(dialogView: View) {
        val shimmerViews = listOf(
            dialogView.findViewById<View>(R.id.shimmerNewRoom),
            dialogView.findViewById<View>(R.id.shimmerExistingRoom),
            dialogView.findViewById<View>(R.id.shimmerJoinByCode)
        )

        shimmerViews.forEach { view ->
            view?.let { startShimmer(it) }
        }
    }

    private fun startShimmer(view: View) {
        val shimmer = ObjectAnimator.ofFloat(view, "translationX", -view.width.toFloat(), view.width.toFloat()).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            startDelay = Random.nextLong(0, 1000)
        }
        shimmer.start()
        animators.add(shimmer)
    }

    private fun startTitleGlow(glowView: View) {
        val pulseX = ObjectAnimator.ofFloat(glowView, "scaleX", 1f, 1.3f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
        }
        val pulseY = ObjectAnimator.ofFloat(glowView, "scaleY", 1f, 1.3f, 1f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
        }

        val alpha = ObjectAnimator.ofFloat(glowView, "alpha", 0.3f, 0.8f, 0.3f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
        }

        AnimatorSet().apply {
            playTogether(pulseX, pulseY, alpha)
            start()
        }.also { animators.add(it) }
    }

    private fun startPulsingDots(dialogView: View) {
        // Implementation for pulsing indicator dots
        // This would animate the small green dots on each option
    }

    fun stopAllEffects() {
        effectJob?.cancel()
        animators.forEach { it.cancel() }
        animators.clear()
        particleViews.forEach { (it.parent as? ViewGroup)?.removeView(it) }
        particleViews.clear()
    }
}
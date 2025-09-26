package com.vibecode.cloudcounter

import android.app.Dialog
import android.content.Context
import android.util.Log

class SystemPermissionsGuideDialog(
    private val context: Context,
    private val onContinue: () -> Unit
) {
    companion object {
        private const val TAG = "SystemPermissionsGuide"
    }

    fun show() {
        Log.d(TAG, "📱 Showing System Permissions Guide dialog")

        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val dialogView = createThemedGuideDialog(dialog)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#80000000")))
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialog.setCancelable(false) // User must click Continue
        
        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(dialogView, 1000L)
    }

    private fun createThemedGuideDialog(dialog: Dialog): android.view.View {
        // Root container - full screen
        val rootContainer = android.widget.FrameLayout(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
        }

        // Create a vertical LinearLayout to hold spacer and card
        val contentWrapper = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // INVISIBLE SPACER - Takes up top space
        val topSpacer = android.view.View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f  // Weight 1 = takes all available space
            )
        }
        contentWrapper.addView(topSpacer)

        // Main card at bottom - RAISED BY 180dp (matching AddSmokerDialog style)
        val mainCard = androidx.cardview.widget.CardView(context).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = 12.dpToPx().toFloat()
            setCardBackgroundColor(android.graphics.Color.parseColor("#E64A4A4A"))

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16.dpToPx(), 0, 16.dpToPx(), 180.dpToPx())
            }
        }

        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Title
        val titleText = android.widget.TextView(context).apply {
            text = "WELCOME TO CLOUDCOUNTER!"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Intro message
        val introText = android.widget.TextView(context).apply {
            text = "CloudCounter needs a few permissions to work properly. After clicking Continue, you'll see these system popups:"
            textSize = 14f
            setTextColor(android.graphics.Color.parseColor("#CCCCCC"))
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 30.dpToPx()
            }
        }
        contentLayout.addView(introText)

        // Permission cards
        // Notifications permission card
        val notificationCard = createPermissionCard(
            "🔔",
            "Notifications",
            "For session reminders and activity alerts",
            android.graphics.Color.parseColor("#3366B2FF")
        )
        contentLayout.addView(notificationCard)

        // Location permission card
        val locationCard = createPermissionCard(
            "📍",
            "Location Access", 
            "For 4:20 countdown feature",
            android.graphics.Color.parseColor("#3398FB98")
        )
        contentLayout.addView(locationCard)

        // Note about permissions
        val noteText = android.widget.TextView(context).apply {
            text = "These permissions won't be asked again after initial setup."
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#888888"))
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 20.dpToPx()
                bottomMargin = 30.dpToPx()
            }
        }
        contentLayout.addView(noteText)

        // Continue button (styled like AddSmokerDialog primary button)
        val continueButton = createContinueButton(dialog)
        contentLayout.addView(continueButton)

        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        return rootContainer
    }

    private fun createPermissionCard(
        emoji: String,
        title: String,
        subtitle: String,
        bgColor: Int
    ): android.view.View {
        val cardContainer = androidx.cardview.widget.CardView(context).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                70.dpToPx()
            ).apply {
                bottomMargin = 12.dpToPx()
            }
        }

        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
        }

        // Emoji icon with background
        val iconBackground = android.widget.TextView(context).apply {
            text = emoji
            textSize = 24f
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(
                48.dpToPx(),
                48.dpToPx()
            )
            val bgDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 12.dpToPx().toFloat()
                setColor(bgColor)
            }
            background = bgDrawable
        }

        // Text container
        val textContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                0,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 16.dpToPx()
            }
        }

        val titleText = android.widget.TextView(context).apply {
            text = title
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        val subtitleText = android.widget.TextView(context).apply {
            text = subtitle
            textSize = 12f
            setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
        }

        textContainer.addView(titleText)
        textContainer.addView(subtitleText)

        contentLayout.addView(iconBackground)
        contentLayout.addView(textContainer)

        cardContainer.addView(contentLayout)
        return cardContainer
    }

    private fun createContinueButton(dialog: Dialog): android.view.View {
        val cardContainer = androidx.cardview.widget.CardView(context).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                70.dpToPx()
            ).apply {
                topMargin = 10.dpToPx()
            }

            isClickable = true
            isFocusable = true
        }

        // Create shimmer container for animation
        val shimmerContainer = android.widget.FrameLayout(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // Image view for pressed state (initially hidden)
        val imageView = android.widget.ImageView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            setImageResource(R.drawable.button_pressed_background)
            visibility = android.view.View.GONE
        }

        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val buttonText = android.widget.TextView(context).apply {
            text = "I UNDERSTAND, CONTINUE"
            textSize = 16f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            letterSpacing = 0.1f
        }

        contentLayout.addView(buttonText)
        shimmerContainer.addView(imageView)
        shimmerContainer.addView(contentLayout)
        cardContainer.addView(shimmerContainer)

        // Add click listener with animation
        cardContainer.setOnClickListener {
            Log.d(TAG, "Continue button clicked")
            animateCardSelection(dialog) {
                dialog.dismiss()
                onContinue()
            }
        }

        // Apply throbbing animation to primary button
        applyThrobbingAnimation(cardContainer)

        return cardContainer
    }

    private fun applyThrobbingAnimation(view: android.view.View) {
        val scaleAnimator = android.animation.ValueAnimator.ofFloat(1f, 1.02f, 1f).apply {
            duration = 2000
            repeatCount = android.animation.ValueAnimator.INFINITE
            addUpdateListener { animator ->
                val scale = animator.animatedValue as Float
                view.scaleX = scale
                view.scaleY = scale
            }
        }
        scaleAnimator.start()
    }

    private fun animateCardSelection(dialog: Dialog, action: () -> Unit) {
        // Simple fade out and action
        dialog.window?.decorView?.animate()
            ?.alpha(0f)
            ?.setDuration(200)
            ?.withEndAction {
                action()
            }
            ?.start()
    }

    private fun performManualFadeIn(view: android.view.View, duration: Long) {
        val alphaAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
            this.duration = duration
            addUpdateListener { animator ->
                view.alpha = animator.animatedValue as Float
            }
        }
        alphaAnimator.start()
    }

    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
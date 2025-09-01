// app/src/main/java/com/sam/cloudcounter/AddSmokerDialog.kt
package com.sam.cloudcounter

import android.app.Dialog
import android.content.Context
import android.text.InputFilter
import android.util.Log
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.sam.cloudcounter.databinding.DialogSearchResultsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.TextView
import android.view.WindowManager
import android.graphics.Rect
import android.view.ViewTreeObserver

class AddSmokerDialog(
    private val context: Context,
    private val cloudSyncService: CloudSyncService,
    private val authManager: FirebaseAuthManager,
    private val googleSignInLauncher: ActivityResultLauncher<android.content.Intent>,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onSmokerAdded: (Smoker) -> Unit,
    private val getCurrentShareCode: () -> String?,
    private val sessionSyncService: SessionSyncService,
    private val repository: ActivityRepository
) {
    companion object {
        private const val TAG = "AddSmokerDialog"
        private const val PREFS_NAME = "smoker_passwords"
    }

    private val passwordDialog = PasswordDialog(context)

    fun show() {
        Log.d(TAG, "📱 showAddSmokerDialog called")

        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val dialogView = createThemedAddSmokerDialog(dialog)
        dialog.setContentView(dialogView)

        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#80000000")))
            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialog.setOnDismissListener {
            Log.d(TAG, "📱 Add smoker dialog dismissed")
        }

        // Set initial alpha to 0 for fade-in
        dialogView.alpha = 0f

        dialog.show()

        // Add keyboard handling
        dialog.window?.apply {
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )

            // Add a global layout listener to adjust for keyboard
            decorView?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    val rect = Rect()
                    decorView.getWindowVisibleDisplayFrame(rect)
                    val screenHeight = decorView.height
                    val keypadHeight = screenHeight - rect.bottom

                    if (keypadHeight > screenHeight * 0.15) {
                        // Keyboard is showing, adjust dialog position
                        val params = attributes
                        params.y = -keypadHeight / 3 // Raise by 1/3 of keyboard height
                        attributes = params
                    } else {
                        // Keyboard hidden, reset position
                        val params = attributes
                        params.y = 0
                        attributes = params
                    }
                }
            })
        }




        // Apply fade-in animation with 2-second duration
        performManualFadeIn(dialogView, 2000L)
    }

    private fun createThemedAddSmokerDialog(dialog: Dialog): android.view.View {
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

        // Main card at bottom - RAISED BY 180dp
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

        // Store card for animation reference
        rootContainer.tag = mainCard

        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Title
        val titleText = android.widget.TextView(context).apply {
            text = "ADD SMOKER"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 40.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Create option cards
        // Add Local Smoker button (primary with throbbing)
        val addLocalCard = createAddSmokerOptionCard("👤", "Add Local Smoker", "Create device-only smoker", true, true) {
            animateCardSelection(dialog) { showAddLocalSmokerDialog() }
        }
        contentLayout.addView(addLocalCard)

        // Login as Cloud Smoker button (primary with throbbing)
        val loginCloudCard = createAddSmokerOptionCard("☁️", "Login as Cloud Smoker", "Sign in with Google", true, true) {
            animateCardSelection(dialog) {
                lifecycleScope.launch {
                    authManager.signOut()
                    handleGoogleSignIn()
                }
            }
        }
        contentLayout.addView(loginCloudCard)

        // Search by Name button (secondary)
        val searchNameCard = createAddSmokerOptionCard("🔍", "Search by Name", "Find smoker by name", false, false) {
            animateCardSelection(dialog) { showSearchByNameDialog() }
        }
        contentLayout.addView(searchNameCard)

        // Search by Code button (secondary)
        val searchCodeCard = createAddSmokerOptionCard("🔢", "Search by Code", "Enter 6-character code", false, false) {
            animateCardSelection(dialog) { showSearchByCodeDialog() }
        }
        contentLayout.addView(searchCodeCard)

        // Browse All Smokers button (secondary)
        val browseAllCard = createAddSmokerOptionCard("👥", "Browse All Smokers", "View all available smokers", false, false) {
            animateCardSelection(dialog) { showBrowseAllSmokersDialog() }
        }
        contentLayout.addView(browseAllCard)

        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        return rootContainer
    }

    private fun createAddSmokerOptionCard(
        emoji: String,
        title: String,
        subtitle: String,
        isPrimary: Boolean,
        shouldThrob: Boolean,
        onClick: () -> Unit
    ): android.view.View {
        val cardContainer = androidx.cardview.widget.CardView(context).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(
                if (isPrimary) android.graphics.Color.parseColor("#33FFFFFF")
                else android.graphics.Color.parseColor("#33FFFFFF")
            )

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                70.dpToPx()
            ).apply {
                bottomMargin = 12.dpToPx()
            }

            isClickable = true
            isFocusable = true
        }

        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
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
                setColor(android.graphics.Color.parseColor("#3398FB98"))
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
            setTextColor(android.graphics.Color.parseColor("#D3D3D3"))
        }

        textContainer.addView(titleText)
        textContainer.addView(subtitleText)

        // Indicator dot
        val indicatorDot = android.view.View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                8.dpToPx(),
                8.dpToPx()
            )
            val dotDrawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(android.graphics.Color.parseColor("#98FB98"))
            }
            background = dotDrawable

            // Add pulsing animation
            android.animation.ObjectAnimator.ofFloat(this, "alpha", 1f, 0.3f, 1f).apply {
                duration = 1500
                repeatCount = android.animation.ValueAnimator.INFINITE
                start()
            }
        }

        contentLayout.addView(iconBackground)
        contentLayout.addView(textContainer)
        contentLayout.addView(indicatorDot)

        // Create a frame to hold everything
        val frameLayout = android.widget.FrameLayout(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        frameLayout.addView(imageView)
        frameLayout.addView(contentLayout)
        cardContainer.addView(frameLayout)

        // Add throbbing animation for primary buttons
        if (shouldThrob) {
            addThrobbingAnimation(cardContainer)
        }

        // Store original colors
        val originalBackgroundColor = android.graphics.Color.parseColor("#33FFFFFF")

        // Handle touch events
        cardContainer.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Show image background
                    cardContainer.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                    imageView.visibility = android.view.View.VISIBLE

                    // Add shadow to text for visibility
                    titleText.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                    subtitleText.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Restore original background
                    imageView.visibility = android.view.View.GONE
                    // Don't restore background color if it's animating (for throbbing buttons)
                    if (!shouldThrob) {
                        cardContainer.setCardBackgroundColor(originalBackgroundColor)
                    }

                    // Remove shadows
                    titleText.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)
                    subtitleText.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)

                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        cardContainer.setOnClickListener {
            onClick()
        }

        return cardContainer
    }

    private fun animateCardSelection(dialog: Dialog, onComplete: () -> Unit) {
        val contentView = dialog.window?.decorView?.findViewById<android.view.View>(android.R.id.content)
        val container = contentView as? android.view.ViewGroup
        val mainCard = container?.tag as? android.view.View ?: container?.getChildAt(0) ?: contentView

        Log.d(TAG, "📱 Starting card selection fade-out animation")

        val fadeOut = android.animation.ObjectAnimator.ofFloat(mainCard, "alpha", 1f, 0f)
        fadeOut.duration = 300L  // Faster than fade-in
        fadeOut.interpolator = android.view.animation.AccelerateInterpolator()

        fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) {
                Log.d(TAG, "📱 Card selection fade-out completed")
                dialog.dismiss()

                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    onComplete()
                }, 100)
            }
        })

        fadeOut.start()
    }

    private fun performManualFadeIn(view: android.view.View, durationMs: Long) {
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs

        val frameDelayMs = 16L // ~60 FPS

        Log.d(TAG, "📱 Starting manual fade animation - Duration: ${durationMs}ms")

        val fadeRunnable = object : Runnable {
            override fun run() {
                val currentTime = System.currentTimeMillis()
                val elapsed = currentTime - startTime
                val progress = kotlin.math.min(elapsed.toFloat() / durationMs.toFloat(), 1f)

                // Apply easing (decelerate interpolation)
                val easedProgress = 1f - (1f - progress) * (1f - progress)

                view.alpha = easedProgress

                if (currentTime < endTime) {
                    // Continue animation
                    handler.postDelayed(this, frameDelayMs)
                } else {
                    // Animation complete - ensure final state
                    view.alpha = 1f
                    Log.d(TAG, "📱 Manual fade COMPLETED")
                }
            }
        }

        // Start the animation
        handler.post(fadeRunnable)
    }

    private fun addThrobbingAnimation(view: android.view.View) {
        val cardView = view as? androidx.cardview.widget.CardView ?: return

        val colors = intArrayOf(
            android.graphics.Color.parseColor("#33FFFFFF"),
            android.graphics.Color.parseColor("#3398FB98"),
            android.graphics.Color.parseColor("#33FFFFFF")
        )

        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        var animationProgress = 0f
        var increasing = true

        val animationRunnable = object : Runnable {
            override fun run() {
                // Update progress
                if (increasing) {
                    animationProgress += 0.02f
                    if (animationProgress >= 1f) {
                        animationProgress = 1f
                        increasing = false
                    }
                } else {
                    animationProgress -= 0.02f
                    if (animationProgress <= 0f) {
                        animationProgress = 0f
                        increasing = true
                    }
                }

                // Calculate color based on progress
                val color = if (animationProgress <= 0.5f) {
                    blendColors(colors[0], colors[1], animationProgress * 2)
                } else {
                    blendColors(colors[1], colors[2], (animationProgress - 0.5f) * 2)
                }

                cardView.setCardBackgroundColor(color)

                // Continue animation
                handler.postDelayed(this, 50) // Update every 50ms for smooth animation
            }
        }

        handler.post(animationRunnable)
    }

    private fun blendColors(from: Int, to: Int, ratio: Float): Int {
        val inverseRatio = 1f - ratio
        val a = (android.graphics.Color.alpha(from) * inverseRatio + android.graphics.Color.alpha(to) * ratio).toInt()
        val r = (android.graphics.Color.red(from) * inverseRatio + android.graphics.Color.red(to) * ratio).toInt()
        val g = (android.graphics.Color.green(from) * inverseRatio + android.graphics.Color.green(to) * ratio).toInt()
        val b = (android.graphics.Color.blue(from) * inverseRatio + android.graphics.Color.blue(to) * ratio).toInt()
        return android.graphics.Color.argb(a, r, g, b)
    }

    // Helper extension function
    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    // ALL EXISTING METHODS BELOW (unchanged from original):

    private fun showAddLocalSmokerDialog() {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

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
                1f
            )
        }
        contentWrapper.addView(topSpacer)

        // Main card at bottom - Initially positioned at bottom with margin
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

        // Store card and wrapper for animation reference
        rootContainer.tag = mainCard
        mainCard.tag = contentWrapper  // Store wrapper reference for margin adjustment

        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Title
        val titleText = android.widget.TextView(context).apply {
            text = "Add Local Smoker"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Input field
        val inputCard = androidx.cardview.widget.CardView(context).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }

        val input = EditText(context).apply {
            hint = "Enter smoker name"
            setHintTextColor(android.graphics.Color.parseColor("#B0B0B0"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        inputCard.addView(input)
        contentLayout.addView(inputCard)

        // Button container
        val buttonContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Cancel button
        val cancelButton = createThemedDialogButton("Cancel", false) {
            animateCardSelection(dialog) {}
        }
        cancelButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            48.dpToPx(),
            1f
        ).apply {
            marginEnd = 8.dpToPx()
        }
        buttonContainer.addView(cancelButton)

        // Add button
        val addButton = createThemedDialogButton("Add", true) {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                animateCardSelection(dialog) {
                    addLocalSmokerWithRoomSync(name)
                }
            } else {
                Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
            }
        }
        addButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            48.dpToPx(),
            1f
        ).apply {
            marginStart = 8.dpToPx()
        }
        buttonContainer.addView(addButton)

        contentLayout.addView(buttonContainer)
        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        dialog.setContentView(rootContainer)

        dialog.window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#80000000"))
            )
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )

            // IMPORTANT: Set soft input mode to adjust resize
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )

            // Add keyboard adjustment listener
            decorView?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                private var originalBottomMargin = 180.dpToPx()

                override fun onGlobalLayout() {
                    val rect = Rect()
                    decorView.getWindowVisibleDisplayFrame(rect)
                    val screenHeight = decorView.height
                    val keypadHeight = screenHeight - rect.bottom

                    // Get the card's layout params
                    val cardParams = mainCard.layoutParams as? android.widget.LinearLayout.LayoutParams

                    if (keypadHeight > screenHeight * 0.15) {
                        // Keyboard is showing
                        // Calculate new bottom margin to place dialog above keyboard
                        // Add extra 20dp padding above keyboard for better visibility
                        val newBottomMargin = keypadHeight + 20.dpToPx()

                        cardParams?.bottomMargin = newBottomMargin
                        mainCard.layoutParams = cardParams

                        // Force layout update
                        mainCard.requestLayout()
                        contentWrapper.requestLayout()

                        Log.d(TAG, "Keyboard shown - Adjusted bottom margin to: $newBottomMargin")
                    } else {
                        // Keyboard hidden, reset to original position
                        cardParams?.bottomMargin = originalBottomMargin
                        mainCard.layoutParams = cardParams

                        // Force layout update
                        mainCard.requestLayout()
                        contentWrapper.requestLayout()

                        Log.d(TAG, "Keyboard hidden - Reset bottom margin to: $originalBottomMargin")
                    }
                }
            })
        }

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 2000L)

        // Request focus and show keyboard after a short delay to ensure dialog is ready
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            input.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }

    private fun addLocalSmokerWithRoomSync(name: String) {
        lifecycleScope.launch {
            val shareCode = getCurrentShareCode()
            val currentUserId = authManager.getCurrentUserId()

            if (shareCode != null && currentUserId != null) {
                Log.d(TAG, "In a room. Finding or creating smoker '$name'...")
                sessionSyncService.findOrCreateSharedSmoker(shareCode, currentUserId, name)
                    .fold(
                        onSuccess = { smokerFromCloud ->
                            onSmokerAdded(smokerFromCloud)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "'$name' is ready.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onFailure = { error ->
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error: ${error.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
            } else {
                Log.d(TAG, "Not in a room. Adding local-only smoker '$name'.")
                val smoker = Smoker(name = name)
                onSmokerAdded(smoker)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Added local smoker: $name", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun handleGoogleSignIn() {
        try {
            googleSignInLauncher.launch(authManager.getSignInIntent())
        } catch (e: Exception) {
            Toast.makeText(context, "Error launching Google Sign-In: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun onGoogleSignInComplete() {
        android.util.Log.d("WELCOME_DEBUG", "🌟 onGoogleSignInComplete() called")
        debugPasswordStorage()
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId()
            val userEmail = authManager.getCurrentUserEmail()
            android.util.Log.d("WELCOME_DEBUG", "📧 Google sign-in - userId: $userId, email: $userEmail")
            
            if (userId == null || userEmail == null) {
                android.util.Log.d("WELCOME_DEBUG", "❌ Authentication failed - null userId or email")
                Toast.makeText(context, "Authentication failed.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Check if we already have this smoker locally
            val existingSmoker = withContext(Dispatchers.IO) {
                repository.getSmokerByCloudUserId(userId)
            }
            android.util.Log.d("WELCOME_DEBUG", "🔍 Existing smoker: ${existingSmoker?.name}, verified: ${existingSmoker?.isPasswordVerified}")

            // Check cloud profile
            val cloudProfile = cloudSyncService.getCloudSmokerProfile(userId).getOrNull()
            android.util.Log.d("WELCOME_DEBUG", "☁️ Cloud profile exists: ${cloudProfile != null}")

            // Check if this is potentially the first cloud smoker (for logging)
            val allSmokers = withContext(Dispatchers.IO) {
                repository.getAllSmokersSync()
            }
            val cloudSmokerCount = allSmokers.count { it.isCloudSmoker }
            android.util.Log.d("WELCOME_DEBUG", "📊 Current cloud smoker count before adding: $cloudSmokerCount")

            // If smoker already exists and is properly set up, just return
            if (existingSmoker != null && existingSmoker.isPasswordVerified) {
                Log.d(TAG, "Smoker already exists and is verified, skipping dialog")
                android.util.Log.d("WELCOME_DEBUG", "⚠️ Smoker already verified, skipping add dialog")
                return@launch
            }

            val userName = authManager.getCurrentUserName() ?: ""
            val isExistingUser = cloudProfile != null  // Check cloud profile, not just local

            val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_smoker_or_login, null)
            val nameInput = dialogView.findViewById<EditText>(R.id.editSmokerName)
            val passwordInput = dialogView.findViewById<EditText>(R.id.editSmokerPassword)
            val accountTextView = dialogView.findViewById<TextView>(R.id.accountTextView)

            accountTextView.text = "Account: $userEmail"

            // Set name from cloud profile or existing smoker
            nameInput.setText(cloudProfile?.name ?: existingSmoker?.name ?: userName)

            // FIXED: Get saved password from SharedPreferences
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedPassword = prefs.getString(userId, null)

            if (savedPassword != null) {
                passwordInput.setText(savedPassword)
                Log.d(TAG, "Pre-filled saved password for user $userId")
            } else {
                Log.d(TAG, "No saved password found for user $userId")
            }

            val dialogTitle = if (isExistingUser) "Login to Cloud Smoker" else "Create Cloud Smoker"
            val positiveButtonText = if (isExistingUser) "Login" else "Create"

            AlertDialog.Builder(context)
                .setTitle(dialogTitle)
                .setView(dialogView)
                .setPositiveButton(positiveButtonText) { _, _ ->
                    val name = nameInput.text.toString().trim()
                    val password = passwordInput.text.toString()

                    if (name.isNotEmpty()) {
                        // Save the password immediately when user clicks login/create
                        if (password.isNotEmpty()) {
                            prefs.edit().putString(userId, password).apply()
                            Log.d(TAG, "Saved password for user $userId")
                        }

                        if (isExistingUser) {
                            // For existing cloud profile, create or update local smoker
                            handleExistingCloudProfile(cloudProfile!!, name, password)
                        } else {
                            // Create new cloud profile
                            createCloudSmoker(name, password)
                        }
                    } else {
                        Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private suspend fun checkAndShowWelcomeScreen() {
        android.util.Log.d("WELCOME_DEBUG", "🔎 Checking if should show welcome screen...")
        
        // Check how many cloud smokers we have now
        val allSmokers = withContext(Dispatchers.IO) {
            repository.getAllSmokersSync()
        }
        val cloudSmokerCount = allSmokers.count { it.isCloudSmoker }
        android.util.Log.d("WELCOME_DEBUG", "📊 Cloud smoker count after adding: $cloudSmokerCount")
        
        // Show welcome screen if this is the first cloud smoker
        if (cloudSmokerCount == 1) {
            android.util.Log.d("WELCOME_DEBUG", "🎊 This is the first cloud smoker! Triggering welcome screen...")
            withContext(Dispatchers.Main) {
                (context as? MainActivity)?.showWelcomeScreenForFirstCloudSmoker()
            }
        } else {
            android.util.Log.d("WELCOME_DEBUG", "🔢 Not the first cloud smoker (count: $cloudSmokerCount)")
        }
    }
    
    private fun handleExistingCloudProfile(cloudProfile: CloudSmokerData, name: String, password: String) {
        lifecycleScope.launch {
            val userId = authManager.getCurrentUserId() ?: return@launch

            // Check if password is correct (if profile has password)
            if (cloudProfile.passwordHash != null && password.isNotEmpty()) {
                val isValid = cloudSyncService.verifyCloudSmokerPassword(userId, password).getOrNull() ?: false
                if (!isValid) {
                    Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                    return@launch
                }
            }

            // Create or update local smoker
            val existingSmoker = withContext(Dispatchers.IO) {
                repository.getSmokerByCloudUserId(userId)
            }

            if (existingSmoker != null) {
                // Update existing
                val updated = existingSmoker.copy(
                    name = name,
                    isPasswordVerified = true,
                    passwordHash = cloudProfile.passwordHash
                )
                withContext(Dispatchers.IO) {
                    repository.updateSmoker(updated)
                }
                onSmokerAdded(updated)
                android.util.Log.d("WELCOME_DEBUG", "📝 Updated existing smoker")
            } else {
                // Create new local entry
                val newSmoker = Smoker(
                    name = name,
                    isCloudSmoker = true,
                    cloudUserId = userId,
                    shareCode = cloudProfile.shareCode,
                    passwordHash = cloudProfile.passwordHash,
                    isPasswordVerified = true,
                    isOwner = true,
                    lastSyncTime = System.currentTimeMillis()
                )
                val id = withContext(Dispatchers.IO) {
                    repository.insertSmoker(newSmoker)
                }
                onSmokerAdded(newSmoker.copy(smokerId = id))
                android.util.Log.d("WELCOME_DEBUG", "✨ Created new cloud smoker: $name")
                
                // Check if this was the first cloud smoker and show welcome
                checkAndShowWelcomeScreen()
            }

            Toast.makeText(context, "Logged in as $name", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showCloudSmokerDialog(existingSmoker: Smoker?) {
        val userId = authManager.getCurrentUserId()
        val userEmail = authManager.getCurrentUserEmail()
        val userName = authManager.getCurrentUserName() ?: ""
        val isExistingUser = existingSmoker != null

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_add_smoker_or_login, null)
        val nameInput = dialogView.findViewById<EditText>(R.id.editSmokerName)
        val passwordInput = dialogView.findViewById<EditText>(R.id.editSmokerPassword)
        val accountTextView = dialogView.findViewById<TextView>(R.id.accountTextView)

        accountTextView.text = "Account: $userEmail"
        nameInput.setText(existingSmoker?.name ?: userName)

        if (isExistingUser && existingSmoker?.cloudUserId != null) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedPassword = prefs.getString(existingSmoker.cloudUserId, null)
            passwordInput.setText(savedPassword)
        }

        val dialogTitle = if (isExistingUser) "Login to Cloud Smoker" else "Create Cloud Smoker"
        val positiveButtonText = if (isExistingUser) "Login" else "Create"

        AlertDialog.Builder(context)
            .setTitle(dialogTitle)
            .setView(dialogView)
            .setPositiveButton(positiveButtonText) { _, _ ->
                val name = nameInput.text.toString().trim()
                val password = passwordInput.text.toString()

                if (name.isNotEmpty()) {
                    if (isExistingUser) {
                        loginCloudSmoker(existingSmoker!!, name, password)
                    } else {
                        createCloudSmoker(name, password)
                    }
                } else {
                    Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loginCloudSmoker(smoker: Smoker, newName: String, password: String) {
        lifecycleScope.launch {
            // If smoker has no password, just update and proceed
            if (smoker.passwordHash == null) {
                val updatedSmoker = smoker.copy(
                    name = newName,
                    isPasswordVerified = true
                )
                withContext(Dispatchers.IO) {
                    repository.updateSmoker(updatedSmoker)
                }
                onSmokerAdded(updatedSmoker)
                Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Verify password if one exists
            if (PasswordUtils.verifyPassword(password, smoker.passwordHash)) {
                // Store the password for future use
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs.edit().putString(smoker.cloudUserId, password).apply()

                val updatedSmoker = smoker.copy(
                    name = newName,
                    isPasswordVerified = true
                )
                withContext(Dispatchers.IO) {
                    repository.updateSmoker(updatedSmoker)
                }
                onSmokerAdded(updatedSmoker)
                Toast.makeText(context, "Login successful", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun createCloudSmoker(name: String, password: String?) {
        val userId = authManager.getCurrentUserId()
        if (userId == null) {
            Toast.makeText(context, "Not signed in", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            try {
                val existingSmoker = withContext(Dispatchers.IO) {
                    repository.getSmokerByCloudUserId(userId)
                }

                if (existingSmoker != null) {
                    loginCloudSmoker(existingSmoker, name, password ?: "")
                    return@launch
                }

                val passwordHash = password?.takeIf { it.isNotEmpty() }?.let { PasswordUtils.hashPassword(it) }

                Log.d(TAG, "Creating new cloud smoker for user $userId")

                val cloudSmoker = cloudSyncService.createCloudSmoker(
                    userId = userId,
                    name = name,
                    passwordHash = passwordHash
                ).getOrThrow()

                val newSmoker = Smoker(
                    name = name,
                    isCloudSmoker = true,
                    cloudUserId = userId,
                    shareCode = cloudSmoker.shareCode,
                    passwordHash = passwordHash,
                    isPasswordVerified = true,
                    isOwner = true,
                    needsSync = false,
                    lastSyncTime = System.currentTimeMillis()
                )

                val newSmokerId = withContext(Dispatchers.IO) {
                    repository.insertSmoker(newSmoker)
                }

                val finalSmoker = newSmoker.copy(smokerId = newSmokerId)

                withContext(Dispatchers.Main) {
                    onSmokerAdded(finalSmoker)
                    Toast.makeText(context, "Created cloud smoker: $name", Toast.LENGTH_SHORT).show()
                    android.util.Log.d("WELCOME_DEBUG", "🆕 Created brand new cloud smoker: $name")

                    // Save the password for future use
                    if (!password.isNullOrEmpty()) {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        prefs.edit().putString(userId, password).apply()
                        Log.d(TAG, "Saved password for user $userId")
                    }
                    
                    // Check if this was the first cloud smoker and show welcome
                    checkAndShowWelcomeScreen()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Failed to create cloud smoker: ${e.message}", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "Error creating cloud smoker", e)
                }
            }
        }
    }

    private fun searchSmokersByName(name: String) {
        lifecycleScope.launch {
            cloudSyncService.searchSmokersByName(name)
                .fold(
                    onSuccess = { list ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val activeRooms = sessionSyncService.getActiveRooms().getOrNull() ?: emptyList()
                            val onlineUserIds = mutableSetOf<String>()

                            activeRooms.forEach { room ->
                                onlineUserIds.addAll(room.activeParticipants)
                            }

                            val smokersWithStatus = list.map { smoker ->
                                smoker.copy(isOnline = onlineUserIds.contains(smoker.userId))
                            }

                            withContext(Dispatchers.Main) {
                                showSearchResults(smokersWithStatus, "Results for '$name'")
                            }
                        }
                    },
                    onFailure = { err ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Search failed: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
        }
    }

    private fun searchSmokerByCode(code: String) {
        lifecycleScope.launch {
            cloudSyncService.searchSmokerByCode(code)
                .fold(
                    onSuccess = { item ->
                        withContext(Dispatchers.Main) {
                            if (item == null) {
                                Toast.makeText(context, "No smoker found for code: $code", Toast.LENGTH_SHORT).show()
                            } else {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    val activeRooms = sessionSyncService.getActiveRooms().getOrNull() ?: emptyList()
                                    val onlineUserIds = mutableSetOf<String>()

                                    activeRooms.forEach { room ->
                                        onlineUserIds.addAll(room.activeParticipants)
                                    }

                                    val smokerWithStatus = item.copy(isOnline = onlineUserIds.contains(item.userId))

                                    withContext(Dispatchers.Main) {
                                        showSearchResults(listOf(smokerWithStatus), "Smoker Found")
                                    }
                                }
                            }
                        }
                    },
                    onFailure = { err ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Search failed: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
        }
    }

    private fun showSearchResults(results: List<CloudSmokerSearchResult>, title: String) {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        // Root container - full screen with transparent background
        val rootContainer = android.widget.FrameLayout(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // Add click to dismiss on background
            setOnClickListener {
                dialog.dismiss()
            }
        }

        // RecyclerView directly on transparent background - starts from top
        val recyclerView = androidx.recyclerview.widget.RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = CloudSmokerSearchAdapter(results, repository) { selected ->
                dialog.dismiss()
                handleSelection(selected)
            }
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                // Add padding at top for status bar and bottom for close button
                topMargin = 24.dpToPx() // Small margin from top
                bottomMargin = 80.dpToPx() // Space for close button
            }
            // Make RecyclerView background transparent
            setBackgroundColor(android.graphics.Color.TRANSPARENT)

            // Add some padding to the RecyclerView for the content
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            clipToPadding = false
        }

        // Close button floating at bottom - raised above nav bar
        val closeButton = androidx.cardview.widget.CardView(context).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = 4.dpToPx().toFloat()
            setCardBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))

            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                48.dpToPx()
            ).apply {
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
                setMargins(16.dpToPx(), 0, 16.dpToPx(), 100.dpToPx()) // Raised 100dp from bottom to avoid nav bar
            }

            isClickable = true
            isFocusable = true
        }

        // Create frame for button image background
        val buttonFrame = android.widget.FrameLayout(context).apply {
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

        val closeText = android.widget.TextView(context).apply {
            text = "Close"
            textSize = 14f
            setTextColor(android.graphics.Color.WHITE)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        buttonFrame.addView(imageView)
        buttonFrame.addView(closeText)
        closeButton.addView(buttonFrame)

        // Handle touch events for close button
        closeButton.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    closeButton.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                    imageView.visibility = android.view.View.VISIBLE
                    closeText.setTextColor(android.graphics.Color.WHITE)
                    closeText.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    imageView.visibility = android.view.View.GONE
                    closeButton.setCardBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))
                    closeText.setTextColor(android.graphics.Color.WHITE)
                    closeText.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)

                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        // Add views directly to root container
        rootContainer.addView(recyclerView)
        rootContainer.addView(closeButton)

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.setContentView(rootContainer)

        dialog.window?.apply {
            setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.MATCH_PARENT)
            // Use TRANSPARENT for the window background to show through
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT))

            // Add dim behind flag for subtle darkening
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            attributes.dimAmount = 0.5f // 50% dim

            setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )
        }

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 500L) // Faster fade-in for results
    }

    private fun createThemedDialogButton(text: String, isPrimary: Boolean, onClick: () -> Unit): android.view.View {
        val buttonCard = androidx.cardview.widget.CardView(context).apply {
            radius = 20.dpToPx().toFloat()
            cardElevation = if (isPrimary) 4.dpToPx().toFloat() else 0f
            setCardBackgroundColor(
                if (isPrimary) android.graphics.Color.parseColor("#98FB98")
                else android.graphics.Color.parseColor("#33FFFFFF")
            )

            isClickable = true
            isFocusable = true
        }

        // Create frame for image background
        val frameLayout = android.widget.FrameLayout(context).apply {
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

        val buttonText = android.widget.TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(
                if (isPrimary) android.graphics.Color.parseColor("#424242")
                else android.graphics.Color.WHITE
            )
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        frameLayout.addView(imageView)
        frameLayout.addView(buttonText)
        buttonCard.addView(frameLayout)

        // Store original colors
        val originalBackgroundColor = if (isPrimary)
            android.graphics.Color.parseColor("#98FB98")
        else
            android.graphics.Color.parseColor("#33FFFFFF")
        val originalTextColor = if (isPrimary)
            android.graphics.Color.parseColor("#424242")
        else
            android.graphics.Color.WHITE

        // Handle touch events
        buttonCard.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    // Show image, hide solid color
                    buttonCard.setCardBackgroundColor(android.graphics.Color.TRANSPARENT)
                    imageView.visibility = android.view.View.VISIBLE

                    // Change text color to white for visibility on image
                    buttonText.setTextColor(android.graphics.Color.WHITE)
                    buttonText.setShadowLayer(4f, 2f, 2f, android.graphics.Color.BLACK)
                    true
                }
                android.view.MotionEvent.ACTION_UP,
                android.view.MotionEvent.ACTION_CANCEL -> {
                    // Hide image, restore solid color
                    imageView.visibility = android.view.View.GONE
                    buttonCard.setCardBackgroundColor(originalBackgroundColor)

                    // Restore original text color
                    buttonText.setTextColor(originalTextColor)
                    buttonText.setShadowLayer(0f, 0f, 0f, android.graphics.Color.TRANSPARENT)

                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }

        buttonCard.setOnClickListener {
            onClick()
        }

        return buttonCard
    }

    private fun handleSelection(item: CloudSmokerSearchResult) {
        val shareCode = getCurrentShareCode()

        if (shareCode != null && item.userId.isNotEmpty()) {
            lifecycleScope.launch {
                cloudSyncService.sendSessionInvitation(
                    toUserId = item.userId,
                    fromUserName = authManager.getCurrentUserName() ?: "Someone",
                    sessionShareCode = shareCode
                ).fold(
                    onSuccess = {
                        Log.d(TAG, "Session invitation sent to ${item.name}")
                    },
                    onFailure = { error: Throwable ->
                        Log.e(TAG, "Failed to send invitation: ${error.message}")
                    }
                )
            }
        }

        if (item.hasPassword) {
            passwordDialog.showVerifyPasswordDialog(
                smokerName = item.name,
                onPasswordEntered = { pwd -> verifyPasswordAndAddSmoker(item, pwd) }
            )
        } else {
            addCloudSmokerToLocal(item, null)
        }
    }

    private fun verifyPasswordAndAddSmoker(item: CloudSmokerSearchResult, password: String) {
        lifecycleScope.launch {
            cloudSyncService.verifyCloudSmokerPassword(item.userId, password)
                .fold(
                    onSuccess = { valid ->
                        withContext(Dispatchers.Main) {
                            if (valid) {
                                addCloudSmokerToLocal(item, password)
                                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                prefs.edit().putString(item.userId, password).apply()
                            }
                            else Toast.makeText(context, "Incorrect password", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onFailure = { err ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Verification failed: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
        }
    }

    private fun addCloudSmokerToLocal(item: CloudSmokerSearchResult, password: String?) {
        val hash = password?.let { PasswordUtils.hashPassword(it) }
        val smoker = Smoker(
            name = item.name,
            isCloudSmoker = true,
            cloudUserId = item.userId,
            shareCode = item.shareCode,
            lastSyncTime = 0L,
            needsSync = false,
            passwordHash = hash,
            isPasswordVerified = true,
            isOwner = false
        )
        onSmokerAdded(smoker)

        val shareCode = getCurrentShareCode()
        val message = if (shareCode != null && item.userId.isNotEmpty()) {
            "${item.name} has been invited to the session"
        } else if (item.hasPassword) {
            "Added password-protected ${item.name}"
        } else {
            "Added ${item.name}"
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun showSearchByNameDialog() {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

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

        // INVISIBLE SPACER
        val topSpacer = android.view.View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentWrapper.addView(topSpacer)

        // Main card at bottom - RAISED BY 180dp
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

        // Store card for animation reference
        rootContainer.tag = mainCard

        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Title
        val titleText = android.widget.TextView(context).apply {
            text = "Search by Name"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Input field
        val inputCard = androidx.cardview.widget.CardView(context).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }

        val input = EditText(context).apply {
            hint = "Enter name to search"
            setHintTextColor(android.graphics.Color.parseColor("#B0B0B0"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        inputCard.addView(input)
        contentLayout.addView(inputCard)

        // Button container
        val buttonContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Cancel button
        val cancelButton = createThemedDialogButton("Cancel", false) {
            animateCardSelection(dialog) {}
        }
        cancelButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            48.dpToPx(),
            1f
        ).apply {
            marginEnd = 8.dpToPx()
        }
        buttonContainer.addView(cancelButton)

        // Search button
        val searchButton = createThemedDialogButton("Search", true) {
            val query = input.text.toString().trim()
            if (query.isNotEmpty()) {
                animateCardSelection(dialog) {
                    searchSmokersByName(query)
                }
            } else {
                Toast.makeText(context, "Please enter a name", Toast.LENGTH_SHORT).show()
            }
        }
        searchButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            48.dpToPx(),
            1f
        ).apply {
            marginStart = 8.dpToPx()
        }
        buttonContainer.addView(searchButton)

        contentLayout.addView(buttonContainer)
        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        dialog.setContentView(rootContainer)

        dialog.window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#80000000"))
            )
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )

            // Set soft input mode to adjust resize
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )

            // Add keyboard adjustment listener
            decorView?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                private val originalBottomMargin = 180.dpToPx()

                override fun onGlobalLayout() {
                    val rect = Rect()
                    decorView.getWindowVisibleDisplayFrame(rect)
                    val screenHeight = decorView.height
                    val keypadHeight = screenHeight - rect.bottom

                    // Get the card's layout params
                    val cardParams = mainCard.layoutParams as? android.widget.LinearLayout.LayoutParams

                    if (keypadHeight > screenHeight * 0.15) {
                        // Keyboard is showing
                        // Calculate new bottom margin to place dialog above keyboard
                        // Add extra 20dp padding above keyboard for better visibility
                        val newBottomMargin = keypadHeight + 20.dpToPx()

                        cardParams?.bottomMargin = newBottomMargin
                        mainCard.layoutParams = cardParams

                        // Force layout update
                        mainCard.requestLayout()
                        contentWrapper.requestLayout()

                        Log.d(TAG, "Search by Name: Keyboard shown - Adjusted bottom margin to: $newBottomMargin")
                    } else {
                        // Keyboard hidden, reset to original position
                        cardParams?.bottomMargin = originalBottomMargin
                        mainCard.layoutParams = cardParams

                        // Force layout update
                        mainCard.requestLayout()
                        contentWrapper.requestLayout()

                        Log.d(TAG, "Search by Name: Keyboard hidden - Reset bottom margin to: $originalBottomMargin")
                    }
                }
            })
        }

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 2000L)

        // Request focus and show keyboard after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            input.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }

    private fun showSearchByCodeDialog() {
        val dialog = Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

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

        // INVISIBLE SPACER
        val topSpacer = android.view.View(context).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        contentWrapper.addView(topSpacer)

        // Main card at bottom - RAISED BY 180dp
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

        // Store card for animation reference
        rootContainer.tag = mainCard

        val contentLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }

        // Title
        val titleText = android.widget.TextView(context).apply {
            text = "Search by Code"
            textSize = 22f
            setTextColor(android.graphics.Color.parseColor("#98FB98"))
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            letterSpacing = 0.15f
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }
        contentLayout.addView(titleText)

        // Input field
        val inputCard = androidx.cardview.widget.CardView(context).apply {
            radius = 12.dpToPx().toFloat()
            cardElevation = 0f
            setCardBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))

            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24.dpToPx()
            }
        }

        val input = EditText(context).apply {
            hint = "Enter 6-character code"
            setHintTextColor(android.graphics.Color.parseColor("#B0B0B0"))
            setTextColor(android.graphics.Color.WHITE)
            textSize = 16f
            background = null
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            filters = arrayOf(InputFilter.LengthFilter(6))
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        inputCard.addView(input)
        contentLayout.addView(inputCard)

        // Button container
        val buttonContainer = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Cancel button
        val cancelButton = createThemedDialogButton("Cancel", false) {
            animateCardSelection(dialog) {}
        }
        cancelButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            48.dpToPx(),
            1f
        ).apply {
            marginEnd = 8.dpToPx()
        }
        buttonContainer.addView(cancelButton)

        // Search button
        val searchButton = createThemedDialogButton("Search", true) {
            val code = input.text.toString().trim().uppercase()
            if (code.length == 6) {
                animateCardSelection(dialog) {
                    searchSmokerByCode(code)
                }
            } else {
                Toast.makeText(context, "Please enter a 6-character code", Toast.LENGTH_SHORT).show()
            }
        }
        searchButton.layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            48.dpToPx(),
            1f
        ).apply {
            marginStart = 8.dpToPx()
        }
        buttonContainer.addView(searchButton)

        contentLayout.addView(buttonContainer)
        mainCard.addView(contentLayout)
        contentWrapper.addView(mainCard)
        rootContainer.addView(contentWrapper)

        // Add click to dismiss on background
        rootContainer.setOnClickListener {
            if (it == rootContainer) {
                animateCardSelection(dialog) {}
            }
        }

        dialog.setContentView(rootContainer)

        dialog.window?.apply {
            setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(android.graphics.Color.parseColor("#80000000"))
            )
            setFlags(
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            )

            // Set soft input mode to adjust resize
            setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE
            )

            // Add keyboard adjustment listener
            decorView?.viewTreeObserver?.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                private val originalBottomMargin = 180.dpToPx()

                override fun onGlobalLayout() {
                    val rect = Rect()
                    decorView.getWindowVisibleDisplayFrame(rect)
                    val screenHeight = decorView.height
                    val keypadHeight = screenHeight - rect.bottom

                    // Get the card's layout params
                    val cardParams = mainCard.layoutParams as? android.widget.LinearLayout.LayoutParams

                    if (keypadHeight > screenHeight * 0.15) {
                        // Keyboard is showing
                        // Calculate new bottom margin to place dialog above keyboard
                        // Add extra 20dp padding above keyboard for better visibility
                        val newBottomMargin = keypadHeight + 20.dpToPx()

                        cardParams?.bottomMargin = newBottomMargin
                        mainCard.layoutParams = cardParams

                        // Force layout update
                        mainCard.requestLayout()
                        contentWrapper.requestLayout()

                        Log.d(TAG, "Search by Code: Keyboard shown - Adjusted bottom margin to: $newBottomMargin")
                    } else {
                        // Keyboard hidden, reset to original position
                        cardParams?.bottomMargin = originalBottomMargin
                        mainCard.layoutParams = cardParams

                        // Force layout update
                        mainCard.requestLayout()
                        contentWrapper.requestLayout()

                        Log.d(TAG, "Search by Code: Keyboard hidden - Reset bottom margin to: $originalBottomMargin")
                    }
                }
            })
        }

        // Set initial alpha to 0 for fade-in
        rootContainer.alpha = 0f

        dialog.show()

        // Apply fade-in animation
        performManualFadeIn(rootContainer, 2000L)

        // Request focus and show keyboard after a short delay
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            input.requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.showSoftInput(input, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        }, 300)
    }
    
    private fun debugPasswordStorage() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val allEntries = prefs.all
        Log.d(TAG, "=== PASSWORD STORAGE DEBUG ===")
        allEntries.forEach { (key, value) ->
            Log.d(TAG, "Key: $key, Value: ${if (value is String && value.isNotEmpty()) "***hidden***" else value}")
        }
        Log.d(TAG, "==============================")
    }

    private fun showBrowseAllSmokersDialog() {
        lifecycleScope.launch {
            cloudSyncService.getAllCloudSmokers()
                .fold(
                    onSuccess = { cloudSmokersList ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            val activeRooms = sessionSyncService.getActiveRooms().getOrNull() ?: emptyList()
                            val onlineUserIds = mutableSetOf<String>()

                            activeRooms.forEach { room ->
                                onlineUserIds.addAll(room.activeParticipants)
                            }

                            val cloudSmokersWithStatus = cloudSmokersList.map { smoker ->
                                smoker.copy(isOnline = onlineUserIds.contains(smoker.userId))
                            }

                            val localSmokers = repository.getAllSmokersList()
                                .filter { !it.isCloudSmoker }
                                .map { smoker ->
                                    CloudSmokerSearchResult(
                                        userId = "",
                                        name = smoker.name,
                                        shareCode = "LOCAL",
                                        totalActivities = repository.getTotalActivitiesForSmoker(smoker.smokerId),
                                        lastActivity = repository.getLastActivityTimestamp(smoker.smokerId) ?: 0L,
                                        hasPassword = false,
                                        isOnline = false
                                    )
                                }

                            val allSmokers = cloudSmokersWithStatus + localSmokers

                            withContext(Dispatchers.Main) {
                                if (allSmokers.isEmpty()) {
                                    Toast.makeText(context, "No smokers found", Toast.LENGTH_SHORT).show()
                                } else {
                                    showSearchResults(allSmokers, "All Smokers")
                                }
                            }
                        }
                    },
                    onFailure = { err ->
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "Load failed: ${err.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
        }
    }
}
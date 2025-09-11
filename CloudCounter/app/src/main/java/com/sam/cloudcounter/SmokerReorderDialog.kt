package com.sam.cloudcounter

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections

class SmokerReorderDialog(
    private val context: Context,
    private val repository: ActivityRepository,
    private val smokerManager: SmokerManager,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onOrderChanged: () -> Unit
) {
    companion object {
        private const val TAG = "SmokerReorderDialog"
    }

    private var smokers = mutableListOf<Smoker>()
    private var draggedView: View? = null
    private var draggedIndex = -1

    fun show(smokersList: List<Smoker>) {
        Log.d(TAG, "📦 Showing reorder dialog with ${smokersList.size} smokers")
        
        smokers.clear()
        smokers.addAll(smokersList.sortedBy { it.displayOrder })
        
        // Create custom dialog with styled theme
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog)
            .create()
        
        // Create custom view for the dialog
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#CC000000")) // Black semi-transparent background
            setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
        }
        
        // Add title
        val titleView = TextView(context).apply {
            text = "REORDER SMOKERS"
            textSize = 20f
            setTextColor(Color.parseColor("#98FB98")) // Light neon green
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 8.dpToPx())
        }
        dialogView.addView(titleView)
        
        // Add instruction text
        val instructionView = TextView(context).apply {
            text = "Drag to reorder"
            textSize = 12f
            setTextColor(Color.parseColor("#98FB98"))
            alpha = 0.7f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 16.dpToPx())
        }
        dialogView.addView(instructionView)
        
        // Container for smoker items
        val itemsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        
        // Add smoker items
        smokers.forEachIndexed { index, smoker ->
            val itemView = createSmokerItemView(smoker, index, itemsContainer, dialog)
            itemsContainer.addView(itemView)
            
            // Add small spacing between items
            if (index < smokers.size - 1) {
                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        4.dpToPx()
                    )
                }
                itemsContainer.addView(spacer)
            }
        }
        
        dialogView.addView(itemsContainer)
        
        // Add save button
        val saveButton = TextView(context).apply {
            text = "SAVE ORDER"
            textSize = 16f
            setTextColor(Color.parseColor("#98FB98"))
            gravity = Gravity.CENTER
            setPadding(12.dpToPx(), 16.dpToPx(), 12.dpToPx(), 8.dpToPx())
            setOnClickListener {
                saveNewOrder()
                dialog.dismiss()
            }
        }
        dialogView.addView(saveButton)
        
        // Add cancel button
        val cancelButton = TextView(context).apply {
            text = "CANCEL"
            textSize = 14f
            setTextColor(Color.parseColor("#98FB98"))
            gravity = Gravity.CENTER
            setPadding(12.dpToPx(), 8.dpToPx(), 12.dpToPx(), 4.dpToPx())
            setOnClickListener {
                dialog.dismiss()
            }
        }
        dialogView.addView(cancelButton)
        
        // Apply neon green border to the entire dialog
        dialogView.background = GradientDrawable().apply {
            setColor(Color.parseColor("#CC000000")) // Black semi-transparent
            setStroke(2.dpToPx(), Color.parseColor("#98FB98")) // Thin neon green border
            cornerRadius = 12.dpToPx().toFloat()
        }
        
        dialog.setView(dialogView)
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            // Move dialog 2cm higher (same as GiantCounterActivity)
            setGravity(Gravity.CENTER)
            attributes = attributes?.apply {
                y = -80.dpToPx()  // Move up by ~2cm (80dp)
            }
        }
        dialog.show()
    }
    
    private fun createSmokerItemView(smoker: Smoker, index: Int, container: LinearLayout, dialog: AlertDialog): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            
            // Drag handle (always visible)
            val dragHandle = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(24.dpToPx(), 24.dpToPx()).apply {
                    marginEnd = 8.dpToPx()
                }
                setImageDrawable(createDragHandleDrawable())
                visibility = View.VISIBLE
            }
            addView(dragHandle)
            
            // Smoker name with preserved font and color
            val nameView = TextView(context).apply {
                text = smoker.name
                textSize = 18f
                // Use the same font and color from smokerManager
                setTextColor(smokerManager.getColorForSmoker(smoker.smokerId))
                typeface = smokerManager.getFontForSmoker(smoker.smokerId)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(nameView)
            
            // Store the smoker data for reordering
            tag = smoker
            
            // Setup touch handling for drag and drop
            setOnTouchListener { view, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // Start dragging immediately
                        draggedView = view
                        draggedIndex = findViewIndex(view, container)
                        // Visual feedback
                        view.elevation = 8.dpToPx().toFloat()
                        view.alpha = 0.9f
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (draggedView == view) {
                            // Calculate position in container
                            val location = IntArray(2)
                            container.getLocationOnScreen(location)
                            val containerY = location[1]
                            val touchY = event.rawY
                            
                            // Find target position
                            var targetIndex = -1
                            var totalHeight = 0f
                            
                            for (i in 0 until container.childCount) {
                                val child = container.getChildAt(i)
                                if (child is LinearLayout) {
                                    val childHeight = child.height
                                    if (touchY - containerY <= totalHeight + childHeight / 2) {
                                        targetIndex = i / 2  // Divide by 2 because of spacers
                                        break
                                    }
                                    totalHeight += childHeight
                                }
                                // Skip spacers
                                if (child !is LinearLayout) {
                                    totalHeight += child.height
                                }
                            }
                            
                            // If we didn't find a position, put it at the end
                            if (targetIndex == -1) {
                                targetIndex = smokers.size - 1
                            }
                            
                            // Perform the swap if needed
                            if (targetIndex != draggedIndex && targetIndex >= 0 && targetIndex < smokers.size) {
                                // Move item in list
                                val draggedSmoker = smokers.removeAt(draggedIndex)
                                smokers.add(targetIndex, draggedSmoker)
                                
                                // Update UI
                                refreshItemsContainer(container, dialog)
                                
                                // Find and highlight the dragged view again
                                for (i in 0 until container.childCount) {
                                    val child = container.getChildAt(i)
                                    if (child is LinearLayout && child.tag == draggedSmoker) {
                                        draggedView = child
                                        draggedView?.elevation = 8.dpToPx().toFloat()
                                        draggedView?.alpha = 0.9f
                                        break
                                    }
                                }
                                
                                // Update dragged index
                                draggedIndex = targetIndex
                            }
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        // Reset visual state
                        draggedView?.elevation = 0f
                        draggedView?.alpha = 1.0f
                        draggedView = null
                        draggedIndex = -1
                        
                        true
                    }
                    else -> false
                }
            }
        }
    }
    
    private fun findViewIndex(view: View, container: LinearLayout): Int {
        var index = 0
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child == view) {
                return index
            }
            if (child is LinearLayout) {
                index++
            }
        }
        return -1
    }
    
    private fun refreshItemsContainer(container: LinearLayout, dialog: AlertDialog) {
        container.removeAllViews()
        
        smokers.forEachIndexed { index, smoker ->
            val itemView = createSmokerItemView(smoker, index, container, dialog)
            container.addView(itemView)
            
            // Add spacing
            if (index < smokers.size - 1) {
                val spacer = View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        4.dpToPx()
                    )
                }
                container.addView(spacer)
            }
        }
    }
    
    private fun saveNewOrder() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Update display order for each smoker
                smokers.forEachIndexed { index, smoker ->
                    repository.updateSmokerDisplayOrder(smoker.smokerId, index)
                }
                
                withContext(Dispatchers.Main) {
                    Log.d(TAG, "✅ Saved new smoker order")
                    onOrderChanged()
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Error saving smoker order", e)
            }
        }
    }
    
    private fun createDragHandleDrawable(): android.graphics.drawable.Drawable {
        return object : android.graphics.drawable.Drawable() {
            override fun draw(canvas: android.graphics.Canvas) {
                val paint = android.graphics.Paint().apply {
                    color = Color.parseColor("#98FB98")
                    alpha = 180
                    strokeWidth = 2.dpToPx().toFloat()
                }
                
                val width = bounds.width().toFloat()
                val height = bounds.height().toFloat()
                val lineSpacing = height / 4
                
                // Draw three horizontal lines (hamburger menu style)
                for (i in 1..3) {
                    val y = lineSpacing * i
                    canvas.drawLine(0f, y, width, y, paint)
                }
            }
            
            override fun setAlpha(alpha: Int) {}
            override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
            override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
        }
    }
    
    private fun Int.dpToPx(): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
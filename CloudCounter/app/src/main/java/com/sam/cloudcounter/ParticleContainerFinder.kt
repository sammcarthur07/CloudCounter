package com.sam.cloudcounter

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout

object ParticleContainerFinder {
    private const val TAG = "ParticleContainer"

    /**
     * Finds the best container for particle effects
     */
    fun findBestContainer(view: View): ViewGroup? {
        Log.d(TAG, "Starting container search from view: ${view.javaClass.simpleName}")

        // Method 1: Try to get the activity and its content view
        val activity = getActivityFromContext(view.context)
        if (activity != null) {
            val contentView = activity.findViewById<ViewGroup>(android.R.id.content)
            if (contentView != null) {
                Log.d(TAG, "✓ Found activity content view")
                return contentView
            }

            // Try the decor view
            val decorView = activity.window.decorView as? ViewGroup
            if (decorView != null) {
                Log.d(TAG, "✓ Found activity decor view")
                return decorView
            }
        }

        // Method 2: Get the root view of the entire hierarchy
        val rootView = view.rootView
        if (rootView is ViewGroup && rootView.id != View.NO_ID) {
            Log.d(TAG, "✓ Found root ViewGroup: ${rootView.javaClass.simpleName}")
            return rootView
        }

        // Method 3: Walk up the parent hierarchy looking for suitable containers
        var parent = view.parent
        var lastValidContainer: ViewGroup? = null

        while (parent != null) {
            if (parent is ViewGroup) {
                Log.d(TAG, "Checking parent: ${parent.javaClass.simpleName}")

                // Check if this is a good container type
                when (parent) {
                    is FrameLayout -> {
                        lastValidContainer = parent
                        if (parent.id == android.R.id.content) {
                            Log.d(TAG, "✓ Found android.R.id.content FrameLayout")
                            return parent
                        }
                    }
                    is CoordinatorLayout -> {
                        Log.d(TAG, "✓ Found CoordinatorLayout")
                        return parent
                    }
                    is ConstraintLayout -> {
                        // Only use if it's a root-level constraint layout
                        if (parent.parent !is ViewGroup || parent.id == android.R.id.content) {
                            Log.d(TAG, "✓ Found root ConstraintLayout")
                            return parent
                        }
                        lastValidContainer = parent
                    }
                }
            }
            parent = (parent as? View)?.parent
        }

        // Return the last valid container we found
        if (lastValidContainer != null) {
            Log.d(TAG, "✓ Using last valid container: ${lastValidContainer.javaClass.simpleName}")
            return lastValidContainer
        }

        Log.e(TAG, "✗ No suitable container found!")
        return null
    }

    /**
     * Gets the Activity from a Context (handles ContextWrapper)
     */
    private fun getActivityFromContext(context: Context): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return ctx
            }
            ctx = ctx.baseContext
        }
        return null
    }
}
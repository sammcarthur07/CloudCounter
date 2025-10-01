package com.vibecode.cloudcounter

import android.content.SharedPreferences
import android.os.Handler
import android.util.Log

class OnboardingFlowController(
    private val activity: MainActivity,
    private val handler: Handler,
    private val onboardingPrefs: SharedPreferences
) {
    data class WelcomeSelections(
        val setupStash: Boolean,
        val setupRatios: Boolean,
        val setupGoals: Boolean
    )

    private enum class SetupDialog { STASH, RATIOS, GOALS }

    private val pendingDialogs = ArrayDeque<SetupDialog>()

    private var permissionsGuideDialog: SystemPermissionsGuideDialog? = null
    private var welcomeDialog: WelcomeScreenDialog? = null

    private var flowActive = false
    private var awaitingNotificationResult = false
    private var awaitingLocationResult = false
    private var awaitingCameraResult = false
    private var awaitingAudioResult = false

    fun shouldRunOnboarding(): Boolean =
        !onboardingPrefs.getBoolean(PREF_ONBOARDING_COMPLETE, false)

    fun start(shouldRun: Boolean, fromSplash: Boolean) {
        if (!shouldRun || !shouldRunOnboarding()) {
            Log.d(TAG, "⏭️ Skipping onboarding flow (shouldRun=$shouldRun)")
            activity.handlePostOnboardingLaunch()
            return
        }

        flowActive = true
        val delayMs = if (fromSplash) 500L else 2000L
        Log.d(TAG, "⏳ Scheduling permissions guide in ${delayMs}ms (fromSplash=$fromSplash)")
        handler.postDelayed({ showPermissionsGuide() }, delayMs)
    }

    fun onNotificationPermissionResult(granted: Boolean) {
        if (!flowActive || !awaitingNotificationResult) return

        awaitingNotificationResult = false
        Log.d(TAG, "🔔 Notification permission result handled in onboarding: $granted")

        handler.postDelayed({ requestLocationPermission() }, PERMISSION_CHAIN_DELAY_MS)
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (!flowActive || !awaitingLocationResult) return

        awaitingLocationResult = false
        Log.d(TAG, "📍 Location permission result handled in onboarding: $granted")

        handler.postDelayed({ requestCameraPermission() }, PERMISSION_CHAIN_DELAY_MS)
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (!flowActive || !awaitingCameraResult) return

        awaitingCameraResult = false
        Log.d(TAG, "📷 Camera permission result handled in onboarding: $granted")

        handler.postDelayed({ requestAudioPermission() }, PERMISSION_CHAIN_DELAY_MS)
    }

    fun onAudioPermissionResult(granted: Boolean) {
        if (!flowActive || !awaitingAudioResult) return

        awaitingAudioResult = false
        Log.d(TAG, "🎤 Audio permission result handled in onboarding: $granted")

        handler.postDelayed({ proceedToAddSmoker() }, POST_PERMISSIONS_DELAY_MS)
    }

    fun onAddSmokerStepCompleted(isFirstCloudSmoker: Boolean) {
        if (!flowActive) return

        Log.d(TAG, "📝 Add smoker step completed (firstCloud=$isFirstCloudSmoker)")
        if (isFirstCloudSmoker) {
            handler.postDelayed({ showWelcomeScreen() }, STEP_TRANSITION_DELAY_MS)
        } else {
            completeOnboarding()
        }
    }

    fun onAddSmokerCancelledOrSkipped() {
        if (!flowActive) return

        Log.d(TAG, "⚠️ Add smoker dialog dismissed without creating first cloud smoker")
        completeOnboarding()
    }

    fun onWelcomeSelections(selections: WelcomeSelections) {
        if (!flowActive) {
            Log.e(TAG, "⚠️ onWelcomeSelections called but flow not active!")
            return
        }

        Log.d(TAG, "🎯 Welcome selections received: $selections")
        Log.d(TAG, "📋 Clearing pending dialogs queue")
        pendingDialogs.clear()

        if (selections.setupStash) {
            Log.d(TAG, "✅ Adding STASH dialog to queue")
            pendingDialogs.addLast(SetupDialog.STASH)
        }
        if (selections.setupRatios) {
            Log.d(TAG, "✅ Adding RATIOS dialog to queue")
            pendingDialogs.addLast(SetupDialog.RATIOS)
        }
        if (selections.setupGoals) {
            Log.d(TAG, "✅ Adding GOALS dialog to queue")
            pendingDialogs.addLast(SetupDialog.GOALS)
        }

        Log.d(TAG, "📊 Queue size after adding: ${pendingDialogs.size}")
        
        if (pendingDialogs.isEmpty()) {
            Log.d(TAG, "✅ No setup dialogs selected, completing onboarding")
            completeOnboarding()
        } else {
            Log.d(TAG, "🚀 Running first dialog from queue")
            runNextQueuedDialog()
        }
    }

    fun onSetupDialogDismissed() {
        if (!flowActive) return

        Log.d(TAG, "🧩 Setup dialog finished, remaining=${pendingDialogs.size}")
        if (pendingDialogs.isEmpty()) {
            handler.postDelayed({ completeOnboarding() }, STEP_TRANSITION_DELAY_MS)
        } else {
            handler.postDelayed({ runNextQueuedDialog() }, STEP_TRANSITION_DELAY_MS)
        }
    }

    private fun showPermissionsGuide() {
        if (!flowActive) return
        if (permissionsGuideDialog != null) return

        Log.d(TAG, "📱 Showing system permissions guide dialog")
        permissionsGuideDialog = SystemPermissionsGuideDialog(activity) {
            permissionsGuideDialog = null
            activity.markLegacyFirstLaunchHandled()
            handler.postDelayed({ requestNotificationPermission() }, PERMISSION_CHAIN_DELAY_MS)
        }
        permissionsGuideDialog?.show()
    }

    private fun requestNotificationPermission() {
        if (!flowActive) return

        if (activity.shouldRequestNotificationPermission()) {
            Log.d(TAG, "🔔 Requesting notification permission (onboarding)")
            awaitingNotificationResult = true
            activity.launchNotificationPermissionRequest()
        } else {
            Log.d(TAG, "🔔 Notification permission already granted or not required")
            onNotificationPermissionResult(true)
        }
    }

    private fun requestLocationPermission() {
        if (!flowActive) return

        if (activity.shouldRequestLocationPermission()) {
            Log.d(TAG, "📍 Requesting location permission (onboarding)")
            awaitingLocationResult = true
            activity.launchLocationPermissionRequest()
        } else {
            Log.d(TAG, "📍 Location permission already granted")
            onLocationPermissionResult(true)
        }
    }

    private fun requestCameraPermission() {
        if (!flowActive) return

        if (activity.shouldRequestCameraPermission()) {
            Log.d(TAG, "📷 Requesting camera permission (onboarding)")
            awaitingCameraResult = true
            activity.launchCameraPermissionRequest()
        } else {
            Log.d(TAG, "📷 Camera permission already granted")
            onCameraPermissionResult(true)
        }
    }

    private fun requestAudioPermission() {
        if (!flowActive) return

        if (activity.shouldRequestAudioPermission()) {
            Log.d(TAG, "🎤 Requesting audio permission (onboarding)")
            awaitingAudioResult = true
            activity.launchAudioPermissionRequest()
        } else {
            Log.d(TAG, "🎤 Audio permission already granted")
            onAudioPermissionResult(true)
        }
    }

    private fun proceedToAddSmoker() {
        if (!flowActive) return

        if (activity.hasAnySmokers()) {
            Log.d(TAG, "👥 Smokers already exist, skipping add-smoker dialog")
            completeOnboarding()
        } else {
            Log.d(TAG, "📝 Showing add smoker dialog")
            activity.showAddSmokerDialogForOnboarding()
        }
    }

    private fun showWelcomeScreen() {
        if (!flowActive) {
            Log.e(TAG, "⚠️ showWelcomeScreen called but flow not active!")
            return
        }
        if (welcomeDialog?.isShowing == true) {
            Log.e(TAG, "⚠️ Welcome dialog is already showing! Preventing duplicate.")
            return
        }
        if (welcomeDialog != null) {
            Log.e(TAG, "⚠️ Welcome dialog instance exists but not showing. Cleaning up.")
            welcomeDialog = null
        }

        Log.d(TAG, "🌟 Showing welcome screen dialog")
        Log.d(TAG, "📊 Creating new WelcomeScreenDialog instance")
        welcomeDialog = WelcomeScreenDialog(activity) { selections ->
            Log.d(TAG, "📩 Welcome dialog callback triggered with selections: $selections")
            welcomeDialog = null
            onWelcomeSelections(selections)
        }
        Log.d(TAG, "🎭 Calling show() on welcome dialog")
        welcomeDialog?.show()
        Log.d(TAG, "✅ Welcome dialog show() completed")
    }

    private fun runNextQueuedDialog() {
        if (!flowActive) {
            Log.e(TAG, "⚠️ runNextQueuedDialog called but flow not active!")
            return
        }

        val next = pendingDialogs.removeFirstOrNull()
        if (next == null) {
            Log.d(TAG, "📭 No more dialogs in queue, completing onboarding")
            completeOnboarding()
            return
        }

        Log.d(TAG, "⏭️ Launching next setup dialog: $next (remaining=${pendingDialogs.size})")
        Log.d(TAG, "🎯 About to show dialog: $next")
        
        when (next) {
            SetupDialog.STASH -> {
                Log.d(TAG, "🗃️ Showing Add Stash Dialog")
                activity.showAddStashDialog { 
                    Log.d(TAG, "✅ Add Stash Dialog dismissed")
                    onSetupDialogDismissed() 
                }
            }
            SetupDialog.RATIOS -> {
                Log.d(TAG, "⚖️ Showing Set Ratio Dialog")
                activity.showSetRatioDialog { 
                    Log.d(TAG, "✅ Set Ratio Dialog dismissed")
                    onSetupDialogDismissed() 
                }
            }
            SetupDialog.GOALS -> {
                Log.d(TAG, "🎯 Showing Add Goal Dialog")
                activity.showAddGoalDialog { 
                    Log.d(TAG, "✅ Add Goal Dialog dismissed")
                    onSetupDialogDismissed() 
                }
            }
        }
    }

    private fun completeOnboarding() {
        if (!flowActive) {
            activity.handlePostOnboardingLaunch()
            return
        }

        Log.d(TAG, "🎉 Onboarding flow complete")
        flowActive = false
        onboardingPrefs.edit().putBoolean(PREF_ONBOARDING_COMPLETE, true).apply()
        activity.markLegacyFirstLaunchHandled()
        activity.handlePostOnboardingLaunch()
    }

    companion object {
        private const val TAG = "OnboardingFlow"
        private const val PREF_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val PERMISSION_CHAIN_DELAY_MS = 500L
        private const val POST_PERMISSIONS_DELAY_MS = 500L
        private const val STEP_TRANSITION_DELAY_MS = 1000L
    }
}

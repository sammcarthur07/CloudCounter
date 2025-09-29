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
        if (!flowActive) return

        Log.d(TAG, "🎯 Welcome selections received: $selections")
        pendingDialogs.clear()

        if (selections.setupStash) pendingDialogs.addLast(SetupDialog.STASH)
        if (selections.setupRatios) pendingDialogs.addLast(SetupDialog.RATIOS)
        if (selections.setupGoals) pendingDialogs.addLast(SetupDialog.GOALS)

        if (pendingDialogs.isEmpty()) {
            Log.d(TAG, "✅ No setup dialogs selected, completing onboarding")
            completeOnboarding()
        } else {
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
        if (!flowActive) return
        if (welcomeDialog?.isShowing == true) return

        Log.d(TAG, "🌟 Showing welcome screen dialog")
        welcomeDialog = WelcomeScreenDialog(activity) { selections ->
            welcomeDialog = null
            onWelcomeSelections(selections)
        }
        welcomeDialog?.show()
    }

    private fun runNextQueuedDialog() {
        if (!flowActive) return

        val next = pendingDialogs.removeFirstOrNull()
        if (next == null) {
            completeOnboarding()
            return
        }

        Log.d(TAG, "⏭️ Launching next setup dialog: $next (remaining=${pendingDialogs.size})")
        when (next) {
            SetupDialog.STASH -> activity.showAddStashDialog { onSetupDialogDismissed() }
            SetupDialog.RATIOS -> activity.showSetRatioDialog { onSetupDialogDismissed() }
            SetupDialog.GOALS -> activity.showAddGoalDialog { onSetupDialogDismissed() }
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

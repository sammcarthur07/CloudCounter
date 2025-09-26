package com.vibecode.cloudcounter

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_FROM_SPLASH = "from_splash"
        const val EXTRA_SHOULD_RUN_ONBOARDING = "should_run_onboarding"

        private const val PREFS_ONBOARDING = "onboarding_prefs"
        private const val PREF_ONBOARDING_COMPLETE = "onboarding_complete"
        private const val LEGACY_PREFS = "sesh"
        private const val LEGACY_FIRST_LAUNCH_KEY = "is_first_launch"
        private const val SPLASH_DELAY_MS = 3000L
    }

    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The splash theme is applied via manifest, no need for setContentView
        
        Log.d("FIRST_LAUNCH_FLOW", "🚀 SplashActivity started - Loading app resources...")

        val onboardingPrefs = getSharedPreferences(PREFS_ONBOARDING, MODE_PRIVATE)
        val legacyPrefs = getSharedPreferences(LEGACY_PREFS, MODE_PRIVATE)

        // Legacy installs only had is_first_launch flag in "sesh" prefs. Treat false as completed onboarding.
        val legacyFirstLaunch = legacyPrefs.getBoolean(LEGACY_FIRST_LAUNCH_KEY, true)
        val onboardingComplete = onboardingPrefs.getBoolean(PREF_ONBOARDING_COMPLETE, false) || !legacyFirstLaunch

        if (onboardingComplete) {
            Log.d("FIRST_LAUNCH_FLOW", "⏭️ Onboarding already complete - skipping splash delay")
            startMainActivity(shouldRunOnboarding = false)
        } else {
            pendingStart = true
            handler.postDelayed({
                pendingStart = false
                Log.d("FIRST_LAUNCH_FLOW", "✅ Splash complete - Starting MainActivity")
                startMainActivity(shouldRunOnboarding = true)
            }, SPLASH_DELAY_MS)
        }
    }

    private fun startMainActivity(shouldRunOnboarding: Boolean) {
        val intent = Intent(this, MainActivity::class.java).apply {
            putExtra(EXTRA_FROM_SPLASH, true)
            putExtra(EXTRA_SHOULD_RUN_ONBOARDING, shouldRunOnboarding)
        }
        startActivity(intent)

        // Smooth transition
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        if (pendingStart) {
            handler.removeCallbacksAndMessages(null)
        }
        super.onDestroy()
    }
}

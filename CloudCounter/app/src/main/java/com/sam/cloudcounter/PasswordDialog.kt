// app/src/main/java/com.sam.cloudcounter/PasswordDialog.kt
package com.sam.cloudcounter

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat

class PasswordDialog(private val context: Context) {

    /**
     * Shows dialog to setup password for new cloud smoker
     */
    fun showSetupPasswordDialog(
        smokerName: String,
        onPasswordSet: (String) -> Unit,
        onSkip: () -> Unit
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val infoText = TextView(context).apply {
            text = "Set a password to protect \"$smokerName\" when shared with other devices. This is optional but recommended for security."
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 20)
        }

        val passwordInput = EditText(context).apply {
            hint = "Enter password (optional)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val confirmInput = EditText(context).apply {
            hint = "Confirm password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val strengthText = TextView(context).apply {
            textSize = 12f
            setPadding(0, 10, 0, 0)
        }

        layout.addView(infoText)
        layout.addView(passwordInput)
        layout.addView(confirmInput)
        layout.addView(strengthText)

        // Real-time password validation
        passwordInput.setOnTextChangedListener { text ->
            if (text.isNullOrEmpty()) {
                strengthText.text = ""
            } else {
                // Just show that a password is entered, no strength requirements
                strengthText.text = "✓ Password entered"
                strengthText.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Secure Your Cloud Smoker")
            .setView(layout)
            .setPositiveButton("Set Password") { _, _ ->
                val password = passwordInput.text.toString()
                val confirm = confirmInput.text.toString()

                when {
                    password != confirm -> {
                        Toast.makeText(context, "Passwords don't match", Toast.LENGTH_SHORT).show()
                        showSetupPasswordDialog(smokerName, onPasswordSet, onSkip)
                    }
                    else -> {
                        // NO VALIDATION - just accept any password
                        onPasswordSet(password)
                    }
                }
            }
            .setNeutralButton("Skip") { _, _ -> onSkip() }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    /**
     * Shows dialog to verify password for existing cloud smoker
     */
    fun showVerifyPasswordDialog(
        smokerName: String,
        onPasswordEntered: (String) -> Unit
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val infoText = TextView(context).apply {
            text = "\"$smokerName\" is password protected. Enter the password to use this smoker."
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 20)
        }

        val passwordInput = EditText(context).apply {
            hint = "Enter password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            requestFocus()
        }

        layout.addView(infoText)
        layout.addView(passwordInput)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Password Required")
            .setView(layout)
            .setPositiveButton("Unlock") { _, _ ->
                val password = passwordInput.text.toString()
                if (password.isNotEmpty()) {
                    onPasswordEntered(password)
                } else {
                    Toast.makeText(context, "Please enter the password", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .create()

        dialog.show()

        // Focus on password input and show keyboard
        passwordInput.requestFocus()
    }

    /**
     * Shows dialog to change password for existing cloud smoker
     */
    fun showChangePasswordDialog(
        smokerName: String,
        onPasswordChanged: (String?) -> Unit
    ) {
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 40, 50, 20)
        }

        val infoText = TextView(context).apply {
            text = "Change password for \"$smokerName\". Leave empty to remove password protection."
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, android.R.color.black))
            setPadding(0, 0, 0, 20)
        }

        val passwordInput = EditText(context).apply {
            hint = "New password (leave empty to remove)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val confirmInput = EditText(context).apply {
            hint = "Confirm new password"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }

        val strengthText = TextView(context).apply {
            textSize = 12f
            setPadding(0, 10, 0, 0)
        }

        layout.addView(infoText)
        layout.addView(passwordInput)
        layout.addView(confirmInput)
        layout.addView(strengthText)

        // Real-time password validation
        passwordInput.setOnTextChangedListener { text ->
            if (text.isNullOrEmpty()) {
                strengthText.text = "Password protection will be removed"
                strengthText.setTextColor(ContextCompat.getColor(context, android.R.color.darker_gray))
            } else {
                strengthText.text = "✓ Password entered"
                strengthText.setTextColor(ContextCompat.getColor(context, android.R.color.holo_green_dark))
            }
        }

        AlertDialog.Builder(context)
            .setTitle("Change Password")
            .setView(layout)
            .setPositiveButton("Update") { _, _ ->
                val password = passwordInput.text.toString()
                val confirm = confirmInput.text.toString()

                when {
                    password.isEmpty() -> {
                        // Remove password protection
                        onPasswordChanged(null)
                    }
                    password != confirm -> {
                        Toast.makeText(context, "Passwords don't match", Toast.LENGTH_SHORT).show()
                        showChangePasswordDialog(smokerName, onPasswordChanged)
                    }
                    else -> {
                        // NO VALIDATION - just accept any password
                        onPasswordChanged(password)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

// Extension function for real-time text change listening
private fun EditText.setOnTextChangedListener(action: (CharSequence?) -> Unit) {
    this.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            action(s)
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}
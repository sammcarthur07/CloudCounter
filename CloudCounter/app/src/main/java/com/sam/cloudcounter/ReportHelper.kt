package com.sam.cloudcounter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class ReportHelper(private val context: Context) {

    companion object {
        private const val TAG = "ReportHelper"
        private const val REPORT_EMAIL = "mcarthur.sp@gmail.com"

        // Report reasons
        val MESSAGE_REPORT_REASONS = listOf(
            "Harassment or bullying",
            "Spam or scam",
            "Hate speech",
            "Inappropriate content",
            "Threatening behavior",
            "Misinformation",
            "Privacy violation",
            "Other"
        )

        val VIDEO_REPORT_REASONS = listOf(
            "Inappropriate behavior",
            "Harassment",
            "Nudity or sexual content",
            "Violence",
            "Hate speech",
            "Privacy violation",
            "Underage user",
            "Other"
        )
    }

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    /**
     * Send message report via email intent
     */
    suspend fun sendMessageReport(
        report: MessageReport,
        customReason: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val subject = "CloudCounter Message Report - ${dateFormatter.format(Date(report.timestamp))}"

            val body = buildString {
                appendLine("MESSAGE REPORT")
                appendLine("==============")
                appendLine()
                appendLine("Report Details:")
                appendLine("Date/Time: ${dateFormatter.format(Date(report.timestamp))}")
                appendLine("Reporter: ${report.reporterUserName} (ID: ${report.reporterUserId})")
                appendLine("Reported User: ${report.reportedUserName} (ID: ${report.reportedUserId})")
                appendLine()
                appendLine("Reason: ${if (report.reason == "Other" && customReason != null) customReason else report.reason}")
                appendLine()
                appendLine("Message Content:")
                appendLine("\"${report.messageContent ?: "Content not available"}\"")
                appendLine()
                appendLine("Message ID: ${report.messageId}")
                appendLine()
                appendLine("---")
                appendLine("This report was automatically generated from CloudCounter app")
            }

            sendEmail(subject, body)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send message report", e)
            false
        }
    }

    /**
     * Send video report via email intent
     */
    suspend fun sendVideoReport(
        report: VideoReport,
        customReason: String? = null
    ) = withContext(Dispatchers.IO) {
        try {
            val subject = "CloudCounter Video Call Report - ${dateFormatter.format(Date(report.timestamp))}"

            val body = buildString {
                appendLine("VIDEO CALL REPORT")
                appendLine("=================")
                appendLine()
                appendLine("Report Details:")
                appendLine("Date/Time: ${dateFormatter.format(Date(report.timestamp))}")
                appendLine("Reporter: ${report.reporterUserName} (ID: ${report.reporterUserId})")
                appendLine("Reported User: ${report.reportedUserName} (ID: ${report.reportedUserId})")
                appendLine()
                appendLine("Room ID: ${report.roomId}")
                appendLine()
                appendLine("Reason: ${if (report.reason == "Other" && customReason != null) customReason else report.reason}")
                appendLine()
                appendLine("---")
                appendLine("This report was automatically generated from CloudCounter app")
            }

            sendEmail(subject, body)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send video report", e)
            false
        }
    }

    /**
     * Create and launch email intent
     */
    private fun sendEmail(subject: String, body: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            // If no email app is available, try with ACTION_SEND
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "message/rfc822"
                putExtra(Intent.EXTRA_EMAIL, arrayOf(REPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(Intent.createChooser(sendIntent, "Send Report").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
    }

    /**
     * Show report dialog and handle submission
     */
    fun showReportDialog(
        context: Context,
        isVideo: Boolean,
        reportedUserId: String,
        reportedUserName: String,
        reporterUserId: String,
        reporterUserName: String,
        roomId: String,
        messageId: String? = null,
        messageContent: String? = null,
        onReportSubmitted: suspend (String, String?) -> Unit
    ) {
        val reasons = if (isVideo) VIDEO_REPORT_REASONS else MESSAGE_REPORT_REASONS

        val builder = android.app.AlertDialog.Builder(context)
        builder.setTitle("Report ${if (isVideo) "Video" else "Message"}")

        var selectedReason: String? = null
        var customReason: String? = null

        builder.setSingleChoiceItems(reasons.toTypedArray(), -1) { _, which ->
            selectedReason = reasons[which]

            // If "Other" is selected, show input dialog
            if (selectedReason == "Other") {
                showCustomReasonDialog(context) { reason ->
                    customReason = reason
                }
            }
        }

        builder.setPositiveButton("Submit Report") { _, _ ->
            selectedReason?.let { reason ->
                CoroutineScope(Dispatchers.Main).launch {
                    onReportSubmitted(reason, customReason)
                }
            }
        }

        builder.setNegativeButton("Cancel", null)
        builder.show()
    }

    private fun showCustomReasonDialog(context: Context, onReasonProvided: (String) -> Unit) {
        val input = android.widget.EditText(context).apply {
            hint = "Please describe the issue"
            setLines(3)
            maxLines = 5
        }

        android.app.AlertDialog.Builder(context)
            .setTitle("Other Reason")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                onReasonProvided(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
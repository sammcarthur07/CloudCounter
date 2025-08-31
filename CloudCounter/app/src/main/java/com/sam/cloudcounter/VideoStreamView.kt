package com.sam.cloudcounter

import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoTrack

class VideoStreamView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val videoRenderer: SurfaceViewRenderer
    private val userNameText: TextView
    private val muteAudioButton: ImageButton
    private val hideVideoButton: ImageButton
    private val reportButton: ImageButton
    private val mutedIndicator: LinearLayout
    private val mutedIcon: ImageView
    private val mutedText: TextView
    private val controlButtons: LinearLayout

    private var userId: String = ""
    private var userName: String = ""
    private var isLocalUser: Boolean = false
    private var isAudioMuted = false
    private var isVideoHidden = false
    private var videoTrack: VideoTrack? = null

    var onMuteAudioClick: ((String, Boolean) -> Unit)? = null
    var onHideVideoClick: ((String, Boolean) -> Unit)? = null
    var onReportClick: ((String, String) -> Unit)? = null
    var onSelfMuteAudioClick: ((Boolean) -> Unit)? = null  // For self muting
    var onSelfHideVideoClick: ((Boolean) -> Unit)? = null  // For self video

    init {
        Log.d("VideoStreamView", "🎬 Inflating video_stream_overlay layout")
        LayoutInflater.from(context).inflate(R.layout.video_stream_overlay, this, true)

        Log.d("VideoStreamView", "🎬 Finding views...")
        videoRenderer = findViewById(R.id.videoRenderer)
        userNameText = findViewById(R.id.userName)
        muteAudioButton = findViewById(R.id.muteAudioButton)
        hideVideoButton = findViewById(R.id.hideVideoButton)
        reportButton = findViewById(R.id.reportButton)
        mutedIndicator = findViewById(R.id.mutedIndicator)
        mutedIcon = findViewById(R.id.mutedIcon)
        mutedText = findViewById(R.id.mutedText)
        controlButtons = findViewById(R.id.controlButtons)

        Log.d("VideoStreamView", "🎬 Views found:")
        Log.d("VideoStreamView", "🎬   - videoRenderer: ${videoRenderer != null}")
        Log.d("VideoStreamView", "🎬   - userNameText: ${userNameText != null}")
        Log.d("VideoStreamView", "🎬   - controlButtons: ${controlButtons != null}")
        Log.d("VideoStreamView", "🎬   - muteAudioButton: ${muteAudioButton != null}")
        Log.d("VideoStreamView", "🎬   - hideVideoButton: ${hideVideoButton != null}")
        Log.d("VideoStreamView", "🎬   - reportButton: ${reportButton != null}")

        setupClickListeners()
    }

    private fun setupClickListeners() {
        Log.d("VideoStreamView", "🎬 Setting up click listeners")

        muteAudioButton.setOnClickListener {
            Log.d("VideoStreamView", "🎬 Mute audio button clicked for ${if (isLocalUser) "self" else userId}")
            if (isLocalUser) {
                // For local user, toggle their own audio for everyone
                toggleSelfAudio()
            } else {
                // For remote user, toggle audio locally only
                toggleAudioMute()
            }
        }

        hideVideoButton.setOnClickListener {
            Log.d("VideoStreamView", "🎬 Hide video button clicked for ${if (isLocalUser) "self" else userId}")
            if (isLocalUser) {
                // For local user, toggle their own video for everyone
                toggleSelfVideo()
            } else {
                // For remote user, hide video locally only
                toggleVideoHide()
            }
        }

        reportButton.setOnClickListener {
            Log.d("VideoStreamView", "🎬 Report button clicked")
            onReportClick?.invoke(userId, userName)
        }
    }

    fun setUserInfo(userId: String, userName: String, isLocalUser: Boolean = false) {
        Log.d("VideoStreamView", "🎬 setUserInfo called:")
        Log.d("VideoStreamView", "🎬   - userId: $userId")
        Log.d("VideoStreamView", "🎬   - userName: $userName")
        Log.d("VideoStreamView", "🎬   - isLocalUser: $isLocalUser")

        this.userId = userId
        this.userName = userName
        this.isLocalUser = isLocalUser
        userNameText.text = userName

        // Store userId as tag for identification during removal
        this.tag = userId

        // Always show control buttons, but hide report button for self
        controlButtons.visibility = View.VISIBLE

        // Hide report button for local user (can't report yourself)
        reportButton.visibility = if (isLocalUser) View.GONE else View.VISIBLE

        Log.d("VideoStreamView", "🎬 Control buttons visibility: VISIBLE")
        Log.d("VideoStreamView", "🎬 Report button visibility: ${if (isLocalUser) "GONE" else "VISIBLE"}")
    }

    // Toggle functions for SELF (affects everyone)
    private fun toggleSelfAudio() {
        isAudioMuted = !isAudioMuted
        updateAudioMuteState()
        onSelfMuteAudioClick?.invoke(!isAudioMuted) // Pass enabled state

        // Don't show the large overlay, just update button color
        Log.d("VideoStreamView", "🎬 Self audio muted: $isAudioMuted")
    }

    private fun toggleSelfVideo() {
        isVideoHidden = !isVideoHidden
        updateVideoHideState()
        onSelfHideVideoClick?.invoke(!isVideoHidden) // Pass enabled state

        // For self video off, just make the video black but don't show overlay
        if (isVideoHidden) {
            videoRenderer.visibility = View.INVISIBLE
            // Optionally, you could show a small text indicator instead of the large overlay
            // For now, just the button color change indicates the state
        } else {
            videoRenderer.visibility = View.VISIBLE
        }
    }

    // Toggle functions for REMOTE users (local only)
    private fun toggleAudioMute() {
        isAudioMuted = !isAudioMuted
        updateAudioMuteState()
        onMuteAudioClick?.invoke(userId, !isAudioMuted) // Pass enabled state

        // For remote users, you might want to keep a subtle indicator
        // but for now, just the button color change is enough
    }

    private fun toggleVideoHide() {
        isVideoHidden = !isVideoHidden
        updateVideoHideState()
        onHideVideoClick?.invoke(userId, !isVideoHidden) // Pass enabled state

        // For remote video hidden, just hide the video without overlay
        videoRenderer.visibility = if (isVideoHidden) View.INVISIBLE else View.VISIBLE
    }

    private fun updateAudioMuteState() {
        muteAudioButton.setImageResource(
            if (isAudioMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on
        )
        muteAudioButton.setColorFilter(
            ContextCompat.getColor(
                context,
                if (isAudioMuted) android.R.color.holo_red_light else android.R.color.white
            )
        )
    }

    private fun updateVideoHideState() {
        hideVideoButton.setImageResource(
            if (isVideoHidden) R.drawable.ic_videocam_off else R.drawable.ic_videocam_on
        )
        hideVideoButton.setColorFilter(
            ContextCompat.getColor(
                context,
                if (isVideoHidden) android.R.color.holo_red_light else android.R.color.white
            )
        )
    }

    private fun showMutedIndicator(text: String, iconRes: Int) {
        mutedIndicator.visibility = View.VISIBLE
        mutedIcon.setImageResource(iconRes)
        mutedText.text = text
    }

    private fun hideMutedIndicator() {
        mutedIndicator.visibility = View.GONE
    }

    fun setVideoTrack(track: VideoTrack?) {
        videoTrack?.removeSink(videoRenderer) // Remove old track if exists
        videoTrack = track
        track?.addSink(videoRenderer)
    }

    fun initializeSurfaceView(eglBase: org.webrtc.EglBase) {
        videoRenderer.init(eglBase.eglBaseContext, null)
        videoRenderer.setEnableHardwareScaler(true)
        videoRenderer.setMirror(false)
        videoRenderer.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FIT)
    }

    fun setMuteStates(audioMuted: Boolean, videoHidden: Boolean) {
        isAudioMuted = audioMuted
        isVideoHidden = videoHidden
        updateAudioMuteState()
        updateVideoHideState()
    }

    fun release() {
        videoTrack?.removeSink(videoRenderer)
        videoRenderer.release()
    }
}
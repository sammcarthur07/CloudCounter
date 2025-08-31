package com.sam.cloudcounter

import android.content.Context
import android.util.Log
import org.webrtc.*
import java.util.concurrent.ConcurrentHashMap

class WebRTCManager(
    private val context: Context,
    private val roomId: String,
    private val userId: String,
    private val userName: String
) {
    companion object {
        private const val TAG = "WebRTCManager"
        private const val STUN_SERVER = "stun:stun.l.google.com:19302"
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
        private const val VIDEO_FPS = 30
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private val peerConnections = ConcurrentHashMap<String, PeerConnection>()
    private val remoteVideoTracks = ConcurrentHashMap<String, VideoTrack>()

    // Local mute states for remote users (local-only, not synced)
    private val remoteMuteStates = ConcurrentHashMap<String, RemoteMuteState>()

    var localVideoTrack: VideoTrack? = null
        private set
    var localAudioTrack: AudioTrack? = null
        private set
    private var videoCapturer: CameraVideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    val eglBase: EglBase = EglBase.create()

    private var onRemoteVideoTrack: ((String, VideoTrack) -> Unit)? = null
    private var onRemoteVideoRemoved: ((String) -> Unit)? = null
    private var isInitialized = false

    data class RemoteMuteState(
        var isAudioMuted: Boolean = false,
        var isVideoMuted: Boolean = false
    )

    fun initialize() {
        if (isInitialized) return

        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase.eglBaseContext,
            true,
            true
        )
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableEncryption = false
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()

        isInitialized = true
        Log.d(TAG, "WebRTC initialized")
    }

    fun startLocalVideo(): VideoTrack? {
        val factory = peerConnectionFactory ?: run {
            Log.e(TAG, "PeerConnectionFactory is null")
            return null
        }

        Log.d(TAG, "Creating video source and track")

        // Create video source
        val videoSource = factory.createVideoSource(false)
        localVideoTrack = factory.createVideoTrack("local_video_$userId", videoSource)
        localVideoTrack?.setEnabled(true)

        // Setup camera capturer
        surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
        videoCapturer = createCameraCapturer()

        if (videoCapturer == null) {
            Log.e(TAG, "Failed to create camera capturer")
            return null
        }

        videoCapturer?.initialize(
            surfaceTextureHelper,
            context,
            videoSource.capturerObserver
        )

        videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
        Log.d(TAG, "Camera capture started at ${VIDEO_WIDTH}x${VIDEO_HEIGHT}@${VIDEO_FPS}fps")

        // Create audio track
        val audioConstraints = MediaConstraints()
        val audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("local_audio_$userId", audioSource)
        localAudioTrack?.setEnabled(true)

        Log.d(TAG, "Local video and audio started successfully")
        return localVideoTrack
    }

    fun createPeerConnection(
        remoteUserId: String,
        isOffer: Boolean,
        onIceCandidate: (IceCandidate) -> Unit,
        onNegotiationNeeded: () -> Unit
    ): PeerConnection? {
        val factory = peerConnectionFactory ?: return null

        val iceServers = listOf(
            PeerConnection.IceServer.builder(STUN_SERVER).createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        val observer = createPeerConnectionObserver(remoteUserId, onIceCandidate, onNegotiationNeeded)

        val peerConnection = factory.createPeerConnection(rtcConfig, observer)

        peerConnection?.let { pc ->
            // Add local tracks
            localVideoTrack?.let {
                pc.addTrack(it, listOf("stream_$userId"))
            }
            localAudioTrack?.let {
                pc.addTrack(it, listOf("stream_$userId"))
            }

            peerConnections[remoteUserId] = pc
            // Initialize mute state for this remote user
            remoteMuteStates[remoteUserId] = RemoteMuteState()
            Log.d(TAG, "Created peer connection for $remoteUserId")
        }

        return peerConnection
    }

    fun isAudioEnabled(): Boolean {
        return localAudioTrack?.enabled() ?: false
    }

    fun isVideoEnabled(): Boolean {
        return localVideoTrack?.enabled() ?: false
    }

    // Local-only muting functions for remote users
    fun toggleRemoteAudio(remoteUserId: String, enabled: Boolean) {
        remoteMuteStates[remoteUserId]?.isAudioMuted = !enabled
        // Apply mute state to the remote audio track if it exists
        peerConnections[remoteUserId]?.receivers?.forEach { receiver ->
            val track = receiver.track()
            if (track is AudioTrack) {
                track.setEnabled(enabled)
            }
        }
        Log.d(TAG, "Remote audio for $remoteUserId: ${if (enabled) "unmuted" else "muted"} (local-only)")
    }

    fun toggleRemoteVideo(remoteUserId: String, enabled: Boolean) {
        remoteMuteStates[remoteUserId]?.isVideoMuted = !enabled
        // Apply mute state to the remote video track
        remoteVideoTracks[remoteUserId]?.setEnabled(enabled)
        Log.d(TAG, "Remote video for $remoteUserId: ${if (enabled) "shown" else "hidden"} (local-only)")
    }

    fun isRemoteAudioMuted(remoteUserId: String): Boolean {
        return remoteMuteStates[remoteUserId]?.isAudioMuted ?: false
    }

    fun isRemoteVideoMuted(remoteUserId: String): Boolean {
        return remoteMuteStates[remoteUserId]?.isVideoMuted ?: false
    }

    private fun createPeerConnectionObserver(
        remoteUserId: String,
        onIceCandidate: (IceCandidate) -> Unit,
        onNegotiationNeeded: () -> Unit
    ) = object : PeerConnection.Observer {

        override fun onIceCandidate(candidate: IceCandidate?) {
            candidate?.let {
                Log.d(TAG, "ICE candidate for $remoteUserId: ${it.sdp}")
                onIceCandidate(it)
            }
        }

        override fun onTrack(transceiver: RtpTransceiver?) {
            val track = transceiver?.receiver?.track()
            if (track is VideoTrack) {
                Log.d(TAG, "Received video track from $remoteUserId")
                // Apply local mute state if exists
                val muteState = remoteMuteStates[remoteUserId]
                if (muteState?.isVideoMuted == true) {
                    track.setEnabled(false)
                }
                remoteVideoTracks[remoteUserId] = track
                onRemoteVideoTrack?.invoke(remoteUserId, track)
            } else if (track is AudioTrack) {
                // Apply local audio mute state if exists
                val muteState = remoteMuteStates[remoteUserId]
                if (muteState?.isAudioMuted == true) {
                    track.setEnabled(false)
                }
            }
        }

        override fun onSignalingChange(state: PeerConnection.SignalingState?) {
            Log.d(TAG, "Signaling state changed for $remoteUserId: $state")
        }

        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
            Log.d(TAG, "ICE connection state for $remoteUserId: $state")
            if (state == PeerConnection.IceConnectionState.DISCONNECTED ||
                state == PeerConnection.IceConnectionState.FAILED) {
                onRemoteVideoRemoved?.invoke(remoteUserId)
                remoteMuteStates.remove(remoteUserId)
            }
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) {}
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
        override fun onAddStream(stream: MediaStream?) {}
        override fun onRemoveStream(stream: MediaStream?) {}
        override fun onDataChannel(channel: DataChannel?) {}
        override fun onRenegotiationNeeded() {
            Log.d(TAG, "Renegotiation needed for $remoteUserId")
            onNegotiationNeeded()
        }
    }

    fun createOffer(remoteUserId: String, callback: (SessionDescription) -> Unit) {
        val pc = peerConnections[remoteUserId] ?: return

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
        }

        pc.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    pc.setLocalDescription(SimpleSdpObserver(), it)
                    callback(it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create offer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun createAnswer(remoteUserId: String, callback: (SessionDescription) -> Unit) {
        val pc = peerConnections[remoteUserId] ?: return

        val constraints = MediaConstraints()

        pc.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                sdp?.let {
                    pc.setLocalDescription(SimpleSdpObserver(), it)
                    callback(it)
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) {
                Log.e(TAG, "Failed to create answer: $error")
            }
            override fun onSetFailure(error: String?) {}
        }, constraints)
    }

    fun setRemoteDescription(remoteUserId: String, sdp: SessionDescription) {
        val pc = peerConnections[remoteUserId] ?: return
        pc.setRemoteDescription(SimpleSdpObserver(), sdp)
    }

    fun addIceCandidate(remoteUserId: String, candidate: IceCandidate) {
        val pc = peerConnections[remoteUserId] ?: return
        pc.addIceCandidate(candidate)
    }

    private fun createCameraCapturer(): CameraVideoCapturer? {
        val enumerator = Camera2Enumerator(context)

        // Try front camera first
        for (deviceName in enumerator.deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        // Fallback to back camera
        for (deviceName in enumerator.deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                val capturer = enumerator.createCapturer(deviceName, null)
                if (capturer != null) return capturer
            }
        }

        return null
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(null)
    }

    fun toggleAudio(enabled: Boolean) {
        localAudioTrack?.setEnabled(enabled)
    }

    fun toggleVideo(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
    }

    fun setOnRemoteVideoTrack(callback: (String, VideoTrack) -> Unit) {
        onRemoteVideoTrack = callback
    }

    fun setOnRemoteVideoRemoved(callback: (String) -> Unit) {
        onRemoteVideoRemoved = callback
    }

    fun removePeerConnection(remoteUserId: String) {
        peerConnections[remoteUserId]?.close()
        peerConnections.remove(remoteUserId)
        remoteVideoTracks.remove(remoteUserId)
        remoteMuteStates.remove(remoteUserId)
        onRemoteVideoRemoved?.invoke(remoteUserId)
    }

    fun dispose() {
        Log.d(TAG, "Disposing WebRTC resources")

        videoCapturer?.stopCapture()
        videoCapturer?.dispose()

        surfaceTextureHelper?.dispose()

        localVideoTrack?.dispose()
        localAudioTrack?.dispose()

        peerConnections.values.forEach { it.close() }
        peerConnections.clear()

        remoteVideoTracks.clear()
        remoteMuteStates.clear()

        peerConnectionFactory?.dispose()

        eglBase.release()

        isInitialized = false
    }

    private class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(sdp: SessionDescription?) {}
        override fun onSetSuccess() {}
        override fun onCreateFailure(error: String?) {}
        override fun onSetFailure(error: String?) {}
    }
}
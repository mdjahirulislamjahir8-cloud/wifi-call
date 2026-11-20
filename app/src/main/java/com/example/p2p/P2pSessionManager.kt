package com.example.p2p

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import com.example.data.VideoQualityPreset
import com.example.data.WaveLinkRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets

class P2pSessionManager(
    context: Context,
    private val repository: WaveLinkRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioEngine = AudioCallEngine(context)

    private val _selectedTransport = MutableStateFlow(TransportType.BLUETOOTH)
    val selectedTransport: StateFlow<TransportType> = _selectedTransport.asStateFlow()

    private val _linkStatus = MutableStateFlow(LinkStatus.IDLE)
    val linkStatus: StateFlow<LinkStatus> = _linkStatus.asStateFlow()

    private val _statusError = MutableStateFlow<String?>(null)
    val statusError: StateFlow<String?> = _statusError.asStateFlow()

    private val _peers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val peers: StateFlow<List<DiscoveredPeer>> = _peers.asStateFlow()

    private val _connectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    val connectedPeer: StateFlow<DiscoveredPeer?> = _connectedPeer.asStateFlow()

    private val _callSession = MutableStateFlow(CallSessionState())
    val callSession: StateFlow<CallSessionState> = _callSession.asStateFlow()

    private val _remoteVideoBitmap = MutableStateFlow<Bitmap?>(null)
    val remoteVideoBitmap: StateFlow<Bitmap?> = _remoteVideoBitmap.asStateFlow()

    private var localAlias: String = "WaveLink Device"
    private var videoQuality: VideoQualityPreset = VideoQualityPreset.BALANCED
    private var lastVideoFrameSentAt = 0L
    private var isDecodingVideo = false
    private var callTimerJob: Job? = null

    private val bluetoothTransport = BluetoothTransport(
        context = context,
        scope = scope,
        onPeersUpdated = { btPeers ->
            if (_selectedTransport.value == TransportType.BLUETOOTH) {
                _peers.value = btPeers
            }
        },
        onStatusChanged = { status, error ->
            if (_selectedTransport.value == TransportType.BLUETOOTH) {
                _linkStatus.value = status
                _statusError.value = error
            }
        },
        onSocketConnected = { peerId, peerName, _, _ ->
            handleConnected(peerId, peerName, TransportType.BLUETOOTH)
        },
        onPacketReceived = { type, payload ->
            handleIncomingPacket(type, payload)
        },
        onDisconnected = { reason ->
            handleDisconnected(reason)
        }
    )

    private val wifiDirectTransport = WifiDirectTransport(
        context = context,
        scope = scope,
        onPeersUpdated = { wfdPeers ->
            if (_selectedTransport.value == TransportType.WIFI_DIRECT) {
                _peers.value = wfdPeers
            }
        },
        onStatusChanged = { status, error ->
            if (_selectedTransport.value == TransportType.WIFI_DIRECT) {
                _linkStatus.value = status
                _statusError.value = error
            }
        },
        onSocketConnected = { peerId, peerName, _, _ ->
            handleConnected(peerId, peerName, TransportType.WIFI_DIRECT)
        },
        onPacketReceived = { type, payload ->
            handleIncomingPacket(type, payload)
        },
        onDisconnected = { reason ->
            handleDisconnected(reason)
        }
    )

    fun updateSettings(alias: String, quality: VideoQualityPreset) {
        localAlias = alias
        videoQuality = quality
    }

    fun selectTransport(transport: TransportType) {
        if (_linkStatus.value == LinkStatus.CONNECTED) return
        _selectedTransport.value = transport
        _statusError.value = null
        _peers.value = emptyList()
        if (transport == TransportType.BLUETOOTH) {
            bluetoothTransport.loadBondedPeers()
        }
    }

    fun refreshPairedBluetoothPeers() {
        if (_selectedTransport.value == TransportType.BLUETOOTH) {
            bluetoothTransport.loadBondedPeers()
        }
    }

    fun startScan() {
        _statusError.value = null
        when (_selectedTransport.value) {
            TransportType.BLUETOOTH -> bluetoothTransport.startScan()
            TransportType.WIFI_DIRECT -> wifiDirectTransport.startScan()
        }
    }

    fun startListening() {
        _statusError.value = null
        when (_selectedTransport.value) {
            TransportType.BLUETOOTH -> bluetoothTransport.startListening()
            TransportType.WIFI_DIRECT -> wifiDirectTransport.startListening()
        }
    }

    fun connectToPeer(peer: DiscoveredPeer) {
        _statusError.value = null
        _selectedTransport.value = peer.transport
        when (peer.transport) {
            TransportType.BLUETOOTH -> bluetoothTransport.connectToPeer(peer)
            TransportType.WIFI_DIRECT -> wifiDirectTransport.connectToPeer(peer)
        }
    }

    fun disconnect() {
        endCallInternal(sendHangup = true, logStatus = "Ended")
        when (_connectedPeer.value?.transport ?: _selectedTransport.value) {
            TransportType.BLUETOOTH -> bluetoothTransport.disconnect()
            TransportType.WIFI_DIRECT -> wifiDirectTransport.disconnect()
        }
        _connectedPeer.value = null
        _linkStatus.value = LinkStatus.IDLE
    }

    fun sendTextMessage(text: String) {
        val peer = _connectedPeer.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        val bytes = trimmed.toByteArray(StandardCharsets.UTF_8)
        scope.launch(Dispatchers.IO) {
            val sent = sendRawPacket(PacketType.TEXT, bytes)
            if (sent) {
                repository.saveMessage(
                    peerId = peer.id,
                    peerName = peer.name,
                    transport = peer.transport.name,
                    content = trimmed,
                    isOutgoing = true
                )
            }
        }
    }

    fun startCall(isVideo: Boolean) {
        val peer = _connectedPeer.value ?: return
        if (_callSession.value.phase != CallPhase.IDLE) return

        _callSession.value = CallSessionState(
            phase = CallPhase.OUTGOING_RINGING,
            isVideo = isVideo,
            isOutgoing = true,
            peerId = peer.id,
            peerName = peer.name,
            isCameraEnabled = isVideo
        )
        val cmd = if (isVideo) CallSignalCommand.INVITE_VIDEO else CallSignalCommand.INVITE_AUDIO
        sendSignal(cmd)
    }

    fun acceptIncomingCall() {
        val current = _callSession.value
        if (current.phase != CallPhase.INCOMING_RINGING) return

        sendSignal(CallSignalCommand.ACCEPT)
        activateInCallMedia(current.isVideo)
    }

    fun declineIncomingCall() {
        val current = _callSession.value
        if (current.phase != CallPhase.INCOMING_RINGING) return
        sendSignal(CallSignalCommand.REJECT)
        logCallAndReset(status = "Declined")
    }

    fun endCall() {
        endCallInternal(sendHangup = true, logStatus = "Completed")
    }

    fun toggleMute() {
        val next = !_callSession.value.isMuted
        audioEngine.isMuted = next
        _callSession.update { it.copy(isMuted = next) }
    }

    fun toggleSpeaker() {
        val next = !_callSession.value.isSpeakerOn
        audioEngine.setSpeakerphone(next)
        _callSession.update { it.copy(isSpeakerOn = next) }
    }

    fun toggleCamera() {
        val next = !_callSession.value.isCameraEnabled
        _callSession.update { it.copy(isCameraEnabled = next) }
    }

    fun flipCamera() {
        _callSession.update { it.copy(isFrontCamera = !it.isFrontCamera) }
    }

    fun toggleVideoUpgrade() {
        val current = _callSession.value
        if (current.phase != CallPhase.IN_CALL) return
        val nextVideo = !current.isVideo
        _callSession.update { it.copy(isVideo = nextVideo, isCameraEnabled = nextVideo) }
        if (!nextVideo) {
            _remoteVideoBitmap.value = null
        }
        sendSignal(if (nextVideo) CallSignalCommand.UPGRADE_VIDEO else CallSignalCommand.DOWNGRADE_AUDIO)
    }

    fun sendCameraFrame(image: ImageProxy) {
        val state = _callSession.value
        if (state.phase != CallPhase.IN_CALL || !state.isVideo || !state.isCameraEnabled) {
            image.close()
            return
        }
        val now = System.currentTimeMillis()
        val minInterval = if (_connectedPeer.value?.transport == TransportType.BLUETOOTH) {
            maxOf(videoQuality.frameIntervalMs, 180L)
        } else {
            videoQuality.frameIntervalMs
        }
        if (now - lastVideoFrameSentAt < minInterval) {
            image.close()
            return
        }
        lastVideoFrameSentAt = now

        val preset = if (_connectedPeer.value?.transport == TransportType.BLUETOOTH) {
            VideoQualityPreset.LOW
        } else {
            videoQuality
        }

        val jpeg = VideoFrameProcessor.imageProxyToJpeg(
            image = image,
            maxWidth = preset.maxWidth,
            jpegQuality = preset.jpegQuality,
            mirrorHorizontally = state.isFrontCamera
        )
        image.close()

        if (jpeg != null && jpeg.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                sendRawPacket(PacketType.VIDEO_JPEG, jpeg)
            }
        }
    }

    private fun handleConnected(peerId: String, peerName: String, transport: TransportType) {
        _connectedPeer.value = DiscoveredPeer(
            id = peerId,
            name = peerName,
            address = peerId.substringAfter("_"),
            transport = transport
        )
        _linkStatus.value = LinkStatus.CONNECTED
        _statusError.value = null

        val handshake = JSONObject().apply {
            put("alias", localAlias)
        }.toString().toByteArray(StandardCharsets.UTF_8)

        scope.launch(Dispatchers.IO) {
            sendRawPacket(PacketType.HANDSHAKE, handshake)
        }
    }

    private suspend fun handleIncomingPacket(type: Byte, payload: ByteArray) {
        val peer = _connectedPeer.value
        when (type) {
            PacketType.HANDSHAKE -> {
                val json = runCatching {
                    JSONObject(String(payload, StandardCharsets.UTF_8))
                }.getOrNull()
                val remoteAlias = json?.optString("alias")?.takeIf { it.isNotBlank() }
                if (remoteAlias != null) {
                    _connectedPeer.update { it?.copy(name = remoteAlias) }
                }
            }

            PacketType.TEXT -> {
                val msg = String(payload, StandardCharsets.UTF_8)
                if (peer != null && msg.isNotBlank()) {
                    repository.saveMessage(
                        peerId = peer.id,
                        peerName = peer.name,
                        transport = peer.transport.name,
                        content = msg,
                        isOutgoing = false
                    )
                }
            }

            PacketType.CALL_SIGNAL -> {
                val cmd = String(payload, StandardCharsets.UTF_8)
                handleCallSignal(cmd)
            }

            PacketType.AUDIO_PCM -> {
                if (_callSession.value.phase == CallPhase.IN_CALL) {
                    audioEngine.playIncomingFrame(payload)
                }
            }

            PacketType.VIDEO_JPEG -> {
                if (_callSession.value.phase == CallPhase.IN_CALL && !isDecodingVideo) {
                    isDecodingVideo = true
                    scope.launch(Dispatchers.Default) {
                        val bmp = VideoFrameProcessor.decodeJpegToBitmap(payload)
                        if (bmp != null) {
                            _remoteVideoBitmap.value = bmp
                        }
                        isDecodingVideo = false
                    }
                }
            }
        }
    }

    private fun handleCallSignal(cmd: String) {
        val peer = _connectedPeer.value ?: return
        when (cmd) {
            CallSignalCommand.INVITE_AUDIO, CallSignalCommand.INVITE_VIDEO -> {
                if (_callSession.value.phase != CallPhase.IDLE) {
                    sendSignal(CallSignalCommand.REJECT)
                    return
                }
                val isVideo = (cmd == CallSignalCommand.INVITE_VIDEO)
                _callSession.value = CallSessionState(
                    phase = CallPhase.INCOMING_RINGING,
                    isVideo = isVideo,
                    isOutgoing = false,
                    peerId = peer.id,
                    peerName = peer.name,
                    isCameraEnabled = isVideo
                )
            }

            CallSignalCommand.ACCEPT -> {
                val current = _callSession.value
                if (current.phase == CallPhase.OUTGOING_RINGING) {
                    activateInCallMedia(current.isVideo)
                }
            }

            CallSignalCommand.REJECT -> {
                if (_callSession.value.phase == CallPhase.OUTGOING_RINGING) {
                    logCallAndReset("Declined")
                }
            }

            CallSignalCommand.HANGUP -> {
                endCallInternal(sendHangup = false, logStatus = "Completed")
            }

            CallSignalCommand.UPGRADE_VIDEO -> {
                _callSession.update { it.copy(isVideo = true, isCameraEnabled = true) }
            }

            CallSignalCommand.DOWNGRADE_AUDIO -> {
                _callSession.update { it.copy(isVideo = false, isCameraEnabled = false) }
                _remoteVideoBitmap.value = null
            }
        }
    }

    private fun activateInCallMedia(isVideo: Boolean) {
        _callSession.update {
            it.copy(
                phase = CallPhase.IN_CALL,
                isVideo = isVideo,
                isCameraEnabled = isVideo,
                durationSeconds = 0L,
                isMuted = false,
                isSpeakerOn = isVideo
            )
        }
        audioEngine.isMuted = false
        audioEngine.start(
            scope = scope,
            speakerOn = isVideo,
            onAudioFrame = { pcm ->
                sendRawPacket(PacketType.AUDIO_PCM, pcm)
            }
        )
        callTimerJob?.cancel()
        callTimerJob = scope.launch {
            while (isActive && _callSession.value.phase == CallPhase.IN_CALL) {
                delay(1000L)
                _callSession.update { it.copy(durationSeconds = it.durationSeconds + 1L) }
            }
        }
    }

    private fun endCallInternal(sendHangup: Boolean, logStatus: String) {
        val current = _callSession.value
        if (current.phase == CallPhase.IDLE) return
        if (sendHangup) {
            sendSignal(CallSignalCommand.HANGUP)
        }
        logCallAndReset(logStatus)
    }

    private fun logCallAndReset(status: String) {
        val current = _callSession.value
        val transportName = _connectedPeer.value?.transport?.name ?: _selectedTransport.value.name
        callTimerJob?.cancel()
        callTimerJob = null
        audioEngine.stop()
        _remoteVideoBitmap.value = null
        _callSession.value = CallSessionState()

        if (current.peerId.isNotBlank()) {
            scope.launch(Dispatchers.IO) {
                repository.saveCallLog(
                    peerId = current.peerId,
                    peerName = current.peerName,
                    transport = transportName,
                    isVideo = current.isVideo,
                    isOutgoing = current.isOutgoing,
                    durationSeconds = current.durationSeconds,
                    status = status
                )
            }
        }
    }

    private fun handleDisconnected(reason: String?) {
        endCallInternal(sendHangup = false, logStatus = "Disconnected")
        _connectedPeer.value = null
        _linkStatus.value = if (reason != null) LinkStatus.ERROR else LinkStatus.IDLE
        _statusError.value = reason
    }

    private fun sendSignal(cmd: String) {
        scope.launch(Dispatchers.IO) {
            sendRawPacket(PacketType.CALL_SIGNAL, cmd.toByteArray(StandardCharsets.UTF_8))
        }
    }

    private fun sendRawPacket(type: Byte, payload: ByteArray): Boolean {
        val transport = _connectedPeer.value?.transport ?: _selectedTransport.value
        return when (transport) {
            TransportType.BLUETOOTH -> bluetoothTransport.sendPacket(type, payload)
            TransportType.WIFI_DIRECT -> wifiDirectTransport.sendPacket(type, payload)
        }
    }

    fun release() {
        audioEngine.stop()
        callTimerJob?.cancel()
        bluetoothTransport.release()
        wifiDirectTransport.release()
    }
}

package com.example.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CallLogEntity
import com.example.data.ChatThreadSummary
import com.example.data.PeerMessageEntity
import com.example.data.UserPreferencesRepository
import com.example.data.UserSettings
import com.example.data.VideoQualityPreset
import com.example.data.WaveLinkDatabase
import com.example.data.WaveLinkRepository
import com.example.p2p.CallSessionState
import com.example.p2p.DiscoveredPeer
import com.example.p2p.LinkStatus
import com.example.p2p.P2pSessionManager
import com.example.p2p.TransportType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class WaveLinkViewModel(application: Application) : AndroidViewModel(application) {
    private val database = WaveLinkDatabase.getInstance(application)
    private val repository = WaveLinkRepository(database.waveLinkDao())
    private val preferences = UserPreferencesRepository(application)
    private val sessionManager = P2pSessionManager(application, repository)

    val selectedTransport: StateFlow<TransportType> = sessionManager.selectedTransport
    val linkStatus: StateFlow<LinkStatus> = sessionManager.linkStatus
    val statusError: StateFlow<String?> = sessionManager.statusError
    val peers: StateFlow<List<DiscoveredPeer>> = sessionManager.peers
    val connectedPeer: StateFlow<DiscoveredPeer?> = sessionManager.connectedPeer
    val callSession: StateFlow<CallSessionState> = sessionManager.callSession
    val remoteVideoBitmap: StateFlow<Bitmap?> = sessionManager.remoteVideoBitmap

    val userSettings: StateFlow<UserSettings> = preferences.settingsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = UserSettings()
        )

    val recentThreads: StateFlow<List<ChatThreadSummary>> = repository.recentChatThreads
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val callLogs: StateFlow<List<CallLogEntity>> = repository.allCallLogs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _activeChatPeerId = MutableStateFlow<String?>(null)
    val activeChatPeerId: StateFlow<String?> = _activeChatPeerId.asStateFlow()

    private val _activeChatPeerName = MutableStateFlow<String>("")
    val activeChatPeerName: StateFlow<String> = _activeChatPeerName.asStateFlow()

    val activeChatMessages: StateFlow<List<PeerMessageEntity>> = _activeChatPeerId
        .flatMapLatest { peerId ->
            if (peerId.isNullOrBlank()) flowOf(emptyList())
            else repository.getMessagesForPeer(peerId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            userSettings.collect { settings ->
                sessionManager.updateSettings(settings.deviceAlias, settings.videoQuality)
            }
        }
        viewModelScope.launch {
            connectedPeer.collect { peer ->
                if (peer != null) {
                    _activeChatPeerId.value = peer.id
                    _activeChatPeerName.value = peer.name
                }
            }
        }
    }

    fun selectTransport(transport: TransportType) = sessionManager.selectTransport(transport)

    fun refreshPairedBluetoothPeers() = sessionManager.refreshPairedBluetoothPeers()

    fun startScan() = sessionManager.startScan()

    fun startListening() = sessionManager.startListening()

    fun connectToPeer(peer: DiscoveredPeer) = sessionManager.connectToPeer(peer)

    fun disconnect() = sessionManager.disconnect()

    fun openChatThread(peerId: String, peerName: String) {
        _activeChatPeerId.value = peerId
        _activeChatPeerName.value = peerName
    }

    fun sendMessage(text: String) = sessionManager.sendTextMessage(text)

    fun startAudioCall() = sessionManager.startCall(isVideo = false)

    fun startVideoCall() = sessionManager.startCall(isVideo = true)

    fun acceptIncomingCall() = sessionManager.acceptIncomingCall()

    fun declineIncomingCall() = sessionManager.declineIncomingCall()

    fun endCall() = sessionManager.endCall()

    fun toggleMute() = sessionManager.toggleMute()

    fun toggleSpeaker() = sessionManager.toggleSpeaker()

    fun toggleCamera() = sessionManager.toggleCamera()

    fun flipCamera() = sessionManager.flipCamera()

    fun toggleVideoUpgrade() = sessionManager.toggleVideoUpgrade()

    fun sendCameraFrame(image: ImageProxy) = sessionManager.sendCameraFrame(image)

    fun updateDeviceAlias(alias: String) {
        viewModelScope.launch { preferences.updateDeviceAlias(alias) }
    }

    fun updateVideoQuality(quality: VideoQualityPreset) {
        viewModelScope.launch { preferences.updateVideoQuality(quality) }
    }

    fun updateLanguage(language: AppLanguage) {
        viewModelScope.launch { preferences.updateLanguage(language) }
    }

    fun toggleLanguage() {
        val next = if (userSettings.value.language == AppLanguage.BN) AppLanguage.EN else AppLanguage.BN
        updateLanguage(next)
    }

    override fun onCleared() {
        super.onCleared()
        sessionManager.release()
    }
}

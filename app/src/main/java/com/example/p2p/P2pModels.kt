package com.example.p2p

enum class TransportType {
    BLUETOOTH,
    WIFI_DIRECT
}

enum class LinkStatus {
    IDLE,
    LISTENING,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

enum class CallPhase {
    IDLE,
    OUTGOING_RINGING,
    INCOMING_RINGING,
    IN_CALL
}

data class DiscoveredPeer(
    val id: String,
    val name: String,
    val address: String,
    val transport: TransportType,
    val isPaired: Boolean = false,
    val signalHint: String = ""
)

data class CallSessionState(
    val phase: CallPhase = CallPhase.IDLE,
    val isVideo: Boolean = false,
    val isOutgoing: Boolean = false,
    val peerId: String = "",
    val peerName: String = "",
    val durationSeconds: Long = 0L,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isCameraEnabled: Boolean = true,
    val isFrontCamera: Boolean = true
)

object PacketType {
    const val HANDSHAKE: Byte = 1
    const val TEXT: Byte = 2
    const val CALL_SIGNAL: Byte = 3
    const val AUDIO_PCM: Byte = 4
    const val VIDEO_JPEG: Byte = 5
}

object CallSignalCommand {
    const val INVITE_AUDIO = "INVITE_AUDIO"
    const val INVITE_VIDEO = "INVITE_VIDEO"
    const val ACCEPT = "ACCEPT"
    const val REJECT = "REJECT"
    const val HANGUP = "HANGUP"
    const val UPGRADE_VIDEO = "UPGRADE_VIDEO"
    const val DOWNGRADE_AUDIO = "DOWNGRADE_AUDIO"
}

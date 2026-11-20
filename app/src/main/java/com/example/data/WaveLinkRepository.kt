package com.example.data

import kotlinx.coroutines.flow.Flow

class WaveLinkRepository(private val dao: WaveLinkDao) {
    val recentChatThreads: Flow<List<ChatThreadSummary>> = dao.getRecentChatThreads()
    val allCallLogs: Flow<List<CallLogEntity>> = dao.getAllCallLogs()

    fun getMessagesForPeer(peerId: String): Flow<List<PeerMessageEntity>> =
        dao.getMessagesForPeer(peerId)

    suspend fun saveMessage(
        peerId: String,
        peerName: String,
        transport: String,
        content: String,
        isOutgoing: Boolean
    ) {
        dao.insertMessage(
            PeerMessageEntity(
                peerId = peerId,
                peerName = peerName,
                transport = transport,
                content = content,
                isOutgoing = isOutgoing
            )
        )
    }

    suspend fun clearChat(peerId: String) = dao.clearChat(peerId)

    suspend fun saveCallLog(
        peerId: String,
        peerName: String,
        transport: String,
        isVideo: Boolean,
        isOutgoing: Boolean,
        durationSeconds: Long,
        status: String
    ) {
        dao.insertCallLog(
            CallLogEntity(
                peerId = peerId,
                peerName = peerName,
                transport = transport,
                isVideo = isVideo,
                isOutgoing = isOutgoing,
                durationSeconds = durationSeconds,
                status = status
            )
        )
    }
}

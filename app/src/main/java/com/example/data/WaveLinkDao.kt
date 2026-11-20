package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class ChatThreadSummary(
    val peerId: String,
    val peerName: String,
    val transport: String,
    val content: String,
    val timestamp: Long
)

@Dao
interface WaveLinkDao {
    @Query("SELECT * FROM peer_messages WHERE peerId = :peerId ORDER BY timestamp ASC")
    fun getMessagesForPeer(peerId: String): Flow<List<PeerMessageEntity>>

    @Query(
        """
        SELECT peerId, peerName, transport, content, MAX(timestamp) as timestamp
        FROM peer_messages
        GROUP BY peerId
        ORDER BY timestamp DESC
        """
    )
    fun getRecentChatThreads(): Flow<List<ChatThreadSummary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: PeerMessageEntity)

    @Query("DELETE FROM peer_messages WHERE peerId = :peerId")
    suspend fun clearChat(peerId: String)

    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity)
}

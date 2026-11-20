package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "peer_messages")
data class PeerMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerId: String,
    val peerName: String,
    val transport: String,
    val content: String,
    val isOutgoing: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

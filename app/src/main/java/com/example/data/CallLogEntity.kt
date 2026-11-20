package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val peerId: String,
    val peerName: String,
    val transport: String,
    val isVideo: Boolean,
    val isOutgoing: Boolean,
    val durationSeconds: Long,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)

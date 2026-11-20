package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PeerMessageEntity::class, CallLogEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WaveLinkDatabase : RoomDatabase() {
    abstract fun waveLinkDao(): WaveLinkDao

    companion object {
        @Volatile
        private var INSTANCE: WaveLinkDatabase? = null

        fun getInstance(context: Context): WaveLinkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WaveLinkDatabase::class.java,
                    "wavelink_offline.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

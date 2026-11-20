package com.example.data

import android.content.Context
import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "wavelink_prefs")

enum class VideoQualityPreset(val maxWidth: Int, val jpegQuality: Int, val frameIntervalMs: Long) {
    LOW(192, 42, 160L),
    BALANCED(288, 55, 100L),
    HIGH(420, 68, 66L)
}

data class UserSettings(
    val deviceAlias: String = Build.MODEL ?: "WaveLink Device",
    val videoQuality: VideoQualityPreset = VideoQualityPreset.BALANCED,
    val language: AppLanguage = AppLanguage.BN
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val DEVICE_ALIAS = stringPreferencesKey("device_alias")
        val VIDEO_QUALITY = stringPreferencesKey("video_quality")
        val LANGUAGE = stringPreferencesKey("language")
    }

    val settingsFlow: Flow<UserSettings> = context.dataStore.data.map { prefs ->
        val defaultAlias = Build.MODEL?.takeIf { it.isNotBlank() } ?: "WaveLink Phone"
        val alias = prefs[Keys.DEVICE_ALIAS]?.takeIf { it.isNotBlank() } ?: defaultAlias
        val quality = runCatching {
            VideoQualityPreset.valueOf(prefs[Keys.VIDEO_QUALITY] ?: VideoQualityPreset.BALANCED.name)
        }.getOrDefault(VideoQualityPreset.BALANCED)
        val language = runCatching {
            AppLanguage.valueOf(prefs[Keys.LANGUAGE] ?: AppLanguage.BN.name)
        }.getOrDefault(AppLanguage.BN)

        UserSettings(
            deviceAlias = alias,
            videoQuality = quality,
            language = language
        )
    }

    suspend fun updateDeviceAlias(alias: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DEVICE_ALIAS] = alias.trim().ifEmpty { Build.MODEL ?: "WaveLink Phone" }
        }
    }

    suspend fun updateVideoQuality(quality: VideoQualityPreset) {
        context.dataStore.edit { prefs ->
            prefs[Keys.VIDEO_QUALITY] = quality.name
        }
    }

    suspend fun updateLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.name
        }
    }
}

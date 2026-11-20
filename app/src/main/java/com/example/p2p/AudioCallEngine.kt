package com.example.p2p

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.max

class AudioCallEngine(private val context: Context) {
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val FRAME_BYTES = 640 // 20ms at 16kHz 16-bit mono
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var captureJob: Job? = null
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var echoCanceler: AcousticEchoCanceler? = null

    @Volatile
    var isMuted: Boolean = false

    @Volatile
    private var isRunning: Boolean = false

    @SuppressLint("MissingPermission")
    fun start(
        scope: CoroutineScope,
        speakerOn: Boolean,
        onAudioFrame: (ByteArray) -> Unit
    ) {
        if (isRunning) return
        isRunning = true

        runCatching {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = speakerOn
        }

        val minRecBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_BYTES * 4)

        val minPlayBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(FRAME_BYTES * 4)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(max(minPlayBuf, FRAME_BYTES * 6))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack = track
        runCatching { track.play() }

        captureJob = scope.launch(Dispatchers.IO) {
            var recorder: AudioRecord? = null
            try {
                recorder = AudioRecord(
                    MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minRecBuf
                )
                if (recorder.state != AudioRecord.STATE_INITIALIZED) {
                    recorder.release()
                    return@launch
                }
                audioRecord = recorder

                if (AcousticEchoCanceler.isAvailable()) {
                    echoCanceler = runCatching {
                        AcousticEchoCanceler.create(recorder.audioSessionId)?.apply {
                            enabled = true
                        }
                    }.getOrNull()
                }

                recorder.startRecording()
                val buffer = ByteArray(FRAME_BYTES)
                while (isActive && isRunning) {
                    val read = recorder.read(buffer, 0, buffer.size)
                    if (read > 0 && !isMuted) {
                        onAudioFrame(buffer.copyOf(read))
                    }
                }
            } catch (_: SecurityException) {
                // Microphone permission missing
            } catch (_: Exception) {
                // Recorder stopped or unavailable
            } finally {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                audioRecord = null
            }
        }
    }

    fun playIncomingFrame(pcmBytes: ByteArray) {
        if (!isRunning) return
        val track = audioTrack ?: return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
            runCatching { track.write(pcmBytes, 0, pcmBytes.size) }
        }
    }

    fun setSpeakerphone(enabled: Boolean) {
        runCatching {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = enabled
        }
    }

    fun stop() {
        isRunning = false
        captureJob?.cancel()
        captureJob = null

        runCatching { echoCanceler?.release() }
        echoCanceler = null

        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null

        runCatching { audioTrack?.pause() }
        runCatching { audioTrack?.flush() }
        runCatching { audioTrack?.release() }
        audioTrack = null

        runCatching {
            @Suppress("DEPRECATION")
            audioManager.isSpeakerphoneOn = false
            audioManager.mode = AudioManager.MODE_NORMAL
        }
    }
}

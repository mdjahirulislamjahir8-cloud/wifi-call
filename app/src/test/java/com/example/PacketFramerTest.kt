package com.example

import com.example.p2p.PacketFramer
import com.example.p2p.PacketType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class PacketFramerTest {
    @Test
    fun writeAndReadPackets_roundTripPreservesTypeAndPayload() = runBlocking {
        val out = ByteArrayOutputStream()
        val lock = Any()

        val textPayload = "WaveLink বাংলা অফলাইন মেসেজ".toByteArray(StandardCharsets.UTF_8)
        val audioPayload = byteArrayOf(1, 2, 3, 4, 127, -128)

        PacketFramer.writePacket(out, lock, PacketType.TEXT, textPayload)
        PacketFramer.writePacket(out, lock, PacketType.AUDIO_PCM, audioPayload)

        val received = mutableListOf<Pair<Byte, ByteArray>>()
        val input = ByteArrayInputStream(out.toByteArray())

        PacketFramer.readLoop(
            inputStream = input,
            isActive = { true },
            onPacket = { type, payload ->
                received.add(type to payload)
            },
            onDisconnected = {}
        )

        assertEquals(2, received.size)
        assertEquals(PacketType.TEXT, received[0].first)
        assertEquals("WaveLink বাংলা অফলাইন মেসেজ", String(received[0].second, StandardCharsets.UTF_8))
        assertEquals(PacketType.AUDIO_PCM, received[1].first)
        assertArrayEquals(audioPayload, received[1].second)
    }
}

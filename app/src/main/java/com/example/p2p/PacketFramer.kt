package com.example.p2p

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object PacketFramer {
    private const val MAX_PACKET_BYTES = 2 * 1024 * 1024 // 2 MB safety bound

    fun writePacket(
        outputStream: OutputStream,
        writeLock: Any,
        type: Byte,
        payload: ByteArray
    ) {
        synchronized(writeLock) {
            val dataOut = DataOutputStream(outputStream)
            dataOut.writeByte(type.toInt())
            dataOut.writeInt(payload.size)
            if (payload.isNotEmpty()) {
                dataOut.write(payload)
            }
            dataOut.flush()
        }
    }

    suspend fun readLoop(
        inputStream: InputStream,
        isActive: () -> Boolean,
        onPacket: suspend (Byte, ByteArray) -> Unit,
        onDisconnected: (String?) -> Unit
    ) {
        val dataIn = DataInputStream(inputStream)
        try {
            while (isActive()) {
                val type = dataIn.readByte()
                val length = dataIn.readInt()
                if (length < 0 || length > MAX_PACKET_BYTES) {
                    throw IOException("Invalid packet length: $length")
                }
                val payload = ByteArray(length)
                if (length > 0) {
                    dataIn.readFully(payload)
                }
                onPacket(type, payload)
            }
            onDisconnected(null)
        } catch (_: EOFException) {
            onDisconnected(null)
        } catch (e: IOException) {
            onDisconnected(e.message)
        } catch (e: Exception) {
            onDisconnected(e.message)
        }
    }
}

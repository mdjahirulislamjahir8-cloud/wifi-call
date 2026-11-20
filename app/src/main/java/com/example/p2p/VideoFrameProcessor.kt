package com.example.p2p

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream

object VideoFrameProcessor {
    fun imageProxyToJpeg(
        image: ImageProxy,
        maxWidth: Int,
        jpegQuality: Int,
        mirrorHorizontally: Boolean
    ): ByteArray? {
        return try {
            val nv21 = yuv420888ToNv21(image)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val rawOut = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, image.width, image.height), 75, rawOut)
            val rawBytes = rawOut.toByteArray()
            val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size) ?: return null

            val rotation = image.imageInfo.rotationDegrees.toFloat()
            val scale = (maxWidth.toFloat() / decoded.width.toFloat()).coerceAtMost(1f)
            val matrix = Matrix().apply {
                postScale(
                    if (mirrorHorizontally) -scale else scale,
                    scale
                )
                if (rotation != 0f) {
                    postRotate(rotation)
                }
            }
            val transformed = Bitmap.createBitmap(
                decoded,
                0,
                0,
                decoded.width,
                decoded.height,
                matrix,
                true
            )
            if (transformed !== decoded) {
                decoded.recycle()
            }
            val finalOut = ByteArrayOutputStream()
            transformed.compress(Bitmap.CompressFormat.JPEG, jpegQuality, finalOut)
            transformed.recycle()
            finalOut.toByteArray()
        } catch (_: Exception) {
            null
        }
    }

    fun decodeJpegToBitmap(bytes: ByteArray): Bitmap? {
        return try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    private fun yuv420888ToNv21(image: ImageProxy): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        var pos = 0
        if (yPixelStride == 1 && yRowStride == width) {
            yBuffer.get(nv21, 0, ySize)
            pos = ySize
        } else {
            val row = ByteArray(yRowStride)
            for (r in 0 until height) {
                val len = minOf(yRowStride, yBuffer.remaining())
                yBuffer.get(row, 0, len)
                for (c in 0 until width) {
                    nv21[pos++] = row[c * yPixelStride]
                }
            }
        }

        val chromaHeight = height / 2
        val chromaWidth = width / 2
        val vRowStride = vPlane.rowStride
        val uRowStride = uPlane.rowStride
        val vPixelStride = vPlane.pixelStride
        val uPixelStride = uPlane.pixelStride

        val vRow = ByteArray(vRowStride)
        val uRow = ByteArray(uRowStride)
        for (r in 0 until chromaHeight) {
            val vLen = minOf(vRowStride, vBuffer.remaining())
            val uLen = minOf(uRowStride, uBuffer.remaining())
            vBuffer.get(vRow, 0, vLen)
            uBuffer.get(uRow, 0, uLen)
            for (c in 0 until chromaWidth) {
                nv21[pos++] = vRow[c * vPixelStride]
                nv21[pos++] = uRow[c * uPixelStride]
            }
        }

        return nv21
    }
}

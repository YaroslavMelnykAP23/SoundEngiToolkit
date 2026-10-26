package com.example.soundengitoolkit

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Minimal WAV codec, 16-bit PCM. Samples in memory are mono floats in -1..1.
 * Files are written as stereo (the mono signal goes to both channels).
 * Reading accepts mono and stereo files (stereo is mixed down to mono).
 */
object Wav {
    private const val CHANNELS_OUT = 2

    fun write(file: File, data: FloatArray, sampleRate: Int = SR.toInt()) {
        val ch = CHANNELS_OUT
        val bytes = data.size * 2 * ch
        file.outputStream().buffered().use { out ->
            val h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            h.put("RIFF".toByteArray()).putInt(36 + bytes).put("WAVE".toByteArray())
            h.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(ch.toShort())   // PCM, 2 channels
            h.putInt(sampleRate).putInt(sampleRate * 2 * ch).putShort((2 * ch).toShort()).putShort(16)
            h.put("data".toByteArray()).putInt(bytes)
            out.write(h.array())
            val buf = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)   // 2048 stereo frames
            var i = 0
            while (i < data.size) {
                buf.clear()
                while (buf.remaining() >= 2 * ch && i < data.size) {
                    val v = (data[i].coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                    for (c in 0 until ch) buf.putShort(v)      // L = R
                    i++
                }
                out.write(buf.array(), 0, buf.position())
            }
        }
    }

    fun read(file: File): FloatArray {
        val b = ByteBuffer.wrap(file.readBytes()).order(ByteOrder.LITTLE_ENDIAN)
        require(b.limit() >= 12 && tag(b, 0) == "RIFF" && tag(b, 8) == "WAVE") { "Not a WAV file" }
        var pos = 12
        var ch = 0
        while (pos + 8 <= b.limit()) {
            val id = tag(b, pos); val size = b.getInt(pos + 4)
            if (id == "fmt ") {
                ch = b.getShort(pos + 10).toInt()
                require(b.getShort(pos + 8).toInt() == 1 && (ch == 1 || ch == 2) && b.getShort(pos + 22).toInt() == 16) {
                    "Only 16-bit PCM WAV (mono or stereo) is supported"
                }
                require(b.getInt(pos + 12) == SR.toInt()) { "Unsupported sample rate (expected 44100 Hz)" }
            } else if (id == "data") {
                require(ch != 0) { "Corrupted WAV: no fmt chunk" }
                val frames = min(size, b.limit() - pos - 8) / (2 * ch)
                return FloatArray(frames) { f ->
                    var sum = 0f
                    for (c in 0 until ch) sum += b.getShort(pos + 8 + (f * ch + c) * 2) / 32768f
                    sum / ch                                    // stereo -> mono
                }
            }
            pos += 8 + size + (size and 1)
        }
        error("Corrupted WAV: no data chunk")
    }

    private fun tag(b: ByteBuffer, at: Int) = String(ByteArray(4) { b.get(at + it) })
}
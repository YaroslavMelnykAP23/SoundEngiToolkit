package com.example.soundengitoolkit

import android.annotation.SuppressLint
import android.media.*
import kotlin.concurrent.thread
import kotlin.math.*

class Engine {
    val synth = Synth(); val sampler = Sampler()
    val eq = Eq(); val comp = Comp(); val delay = Delay(); val reverb = Reverb()
    private val chain = listOf<Fx>(eq, comp, delay, reverb)

    @Volatile var bpm = 120f
        set(v) { field = v; delay.sync(v) }
    /** Master output volume in dB (-60 = mute). Audible, applied after the effect chain. */
    @Volatile var masterDb = 0f
    private var gainCur = 1f
    @Volatile private var running = false
    @Volatile private var recording = false

    private val scopeBuf = FloatArray(8192)
    private val mask = 8191
    @Volatile private var scopeW = 0

    init { delay.sync(bpm) }

    /**
     * Fills [out] with the latest output signal covering [span] samples (max 4096),
     * aligned to a rising zero crossing. Returns how many points of [out] are valid.
     */
    fun scope(out: FloatArray, span: Int): Int {
        val sp = span.coerceIn(64, 4096)
        val n = min(out.size, sp)
        val start = (scopeW - sp * 2 + scopeBuf.size) and mask
        var off = 0
        for (i in 0 until sp) {
            if (scopeBuf[(start + i) and mask] <= 0f && scopeBuf[(start + i + 1) and mask] > 0f) { off = i; break }
        }
        for (i in 0 until n) out[i] = scopeBuf[(start + off + (i.toLong() * sp / n).toInt()) and mask]
        return n
    }

    /** out[0] = peak dBFS, out[1] = RMS dBFS over the last ~46 ms. */
    fun levels(out: FloatArray) {
        val n = 2048
        val start = (scopeW - n + scopeBuf.size) and mask
        var pk = 0f; var sum = 0f
        for (i in 0 until n) { val v = scopeBuf[(start + i) and mask]; pk = max(pk, abs(v)); sum += v * v }
        out[0] = 20f * log10(max(pk, 1e-6f)); out[1] = 20f * log10(max(sqrt(sum / n), 1e-6f))
    }

    fun start() {
        if (running) return
        running = true
        thread(name = "audio-out", priority = Thread.MAX_PRIORITY) {
            val sr = SR.toInt()
            val minBuf = AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_FLOAT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(maxOf(minBuf, 4096))
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            val buf = FloatArray(256)
            track.play()
            while (running) {
                buf.fill(0f)
                synth.run(buf, buf.size); sampler.run(buf, buf.size)
                for (fx in chain) if (fx.on) fx.run(buf, buf.size)
                val target = if (masterDb <= -60f) 0f else 10f.pow(masterDb / 20f)
                for (i in buf.indices) { gainCur += (target - gainCur) * 0.002f; buf[i] *= gainCur }
                for (i in buf.indices) buf[i] = buf[i].coerceIn(-1f, 1f)
                for (i in buf.indices) scopeBuf[(scopeW + i) and mask] = buf[i]
                scopeW = (scopeW + buf.size) and mask
                track.write(buf, 0, buf.size, AudioTrack.WRITE_BLOCKING)
            }
            track.stop(); track.release()
        }
    }

    fun stop() { running = false }

    /** Requires RECORD_AUDIO permission. Returns false if the mic could not be opened. */
    @SuppressLint("MissingPermission")
    fun startRecording(onDone: (Int) -> Unit): Boolean {
        if (recording) return true
        val sr = SR.toInt()
        val size = maxOf(
            AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT), 4096
        )
        val rec = runCatching {
            AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT, size)
        }.getOrNull() ?: return false
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return false }
        recording = true
        thread(name = "audio-in") {
            val chunks = ArrayList<FloatArray>(); val tmp = FloatArray(1024)
            rec.startRecording()
            while (recording) {
                val n = rec.read(tmp, 0, tmp.size, AudioRecord.READ_BLOCKING)
                if (n > 0) chunks.add(tmp.copyOf(n))
            }
            rec.stop(); rec.release()
            val out = FloatArray(chunks.sumOf { it.size }); var p = 0
            for (c in chunks) { c.copyInto(out, p); p += c.size }
            val peak = out.maxOfOrNull { abs(it) } ?: 0f
            if (peak > 0.001f) { val g = 0.9f / peak; for (i in out.indices) out[i] *= g } // normalize
            sampler.load(out)
            onDone(out.size)
        }
        return true
    }

    fun stopRecording() { recording = false }
}
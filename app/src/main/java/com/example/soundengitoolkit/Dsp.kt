package com.example.soundengitoolkit

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.math.*

const val SR = 44100f
private const val TWO_PI = (2.0 * PI).toFloat()

abstract class Fx {
    @Volatile var on = false
    abstract fun run(b: FloatArray, n: Int)
}

/** Biquad filter (RBJ cookbook) */
class Bq {
    private var b0 = 1f; private var b1 = 0f; private var b2 = 0f
    private var a1 = 0f; private var a2 = 0f; private var z1 = 0f; private var z2 = 0f

    private fun coef(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = (nb0 / a0).toFloat(); b1 = (nb1 / a0).toFloat(); b2 = (nb2 / a0).toFloat()
        a1 = (na1 / a0).toFloat(); a2 = (na2 / a0).toFloat()
    }

    fun peak(f: Float, db: Float, q: Float) {
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f / SR; val al = sin(w) / (2 * q); val c = cos(w)
        coef(1 + al * a, -2 * c, 1 - al * a, 1 + al / a, -2 * c, 1 - al / a)
    }

    fun shelf(f: Float, db: Float, high: Boolean) {
        val a = 10.0.pow(db / 40.0); val w = 2 * PI * f / SR; val c = cos(w)
        val t = 2 * sqrt(a) * (sin(w) / 2 * sqrt(2.0))
        if (!high) coef(
            a * ((a + 1) - (a - 1) * c + t), 2 * a * ((a - 1) - (a + 1) * c), a * ((a + 1) - (a - 1) * c - t),
            (a + 1) + (a - 1) * c + t, -2 * ((a - 1) + (a + 1) * c), (a + 1) + (a - 1) * c - t
        ) else coef(
            a * ((a + 1) + (a - 1) * c + t), -2 * a * ((a - 1) + (a + 1) * c), a * ((a + 1) + (a - 1) * c - t),
            (a + 1) - (a - 1) * c + t, 2 * ((a - 1) - (a + 1) * c), (a + 1) - (a - 1) * c - t
        )
    }

    fun run(b: FloatArray, n: Int) {
        for (i in 0 until n) {
            val x = b[i]; val y = b0 * x + z1
            z1 = b1 * x - a1 * y + z2; z2 = b2 * x - a2 * y; b[i] = y
        }
    }
}

class Eq : Fx() {
    private val lo = Bq(); private val mid = Bq(); private val hi = Bq()
    init { set(0f, 0f, 0f) }
    fun set(low: Float, m: Float, high: Float) {
        lo.shelf(100f, low, false); mid.peak(1000f, m, 1f); hi.shelf(8000f, high, true)
    }
    override fun run(b: FloatArray, n: Int) { lo.run(b, n); mid.run(b, n); hi.run(b, n) }
}

class Delay : Fx() {
    private val buf = FloatArray(SR.toInt() * 3); private var w = 0
    private var samples = 11025
    var denom = 8; var fb = 0.4f; var mix = 0.35f

    /** delay time = note division synced to BPM (whole note = 4 beats) */
    fun sync(bpm: Float) { samples = (SR * 240f / bpm / denom).toInt().coerceIn(1, buf.size - 1) }

    override fun run(b: FloatArray, n: Int) {
        for (i in 0 until n) {
            val d = buf[(w - samples + buf.size) % buf.size]
            buf[w] = b[i] + d * fb; w = (w + 1) % buf.size
            b[i] += d * mix
        }
    }
}

private class Comb(n: Int) {
    val b = FloatArray(n); var i = 0; var s = 0f
    fun p(x: Float, fb: Float, damp: Float): Float {
        val o = b[i]; s = o * (1 - damp) + s * damp; b[i] = x + s * fb; i = (i + 1) % b.size; return o
    }
}

private class Ap(n: Int) {
    val b = FloatArray(n); var i = 0
    fun p(x: Float): Float { val o = b[i]; b[i] = x + o * 0.5f; i = (i + 1) % b.size; return o - x }
}

/** Simple Schroeder/Freeverb-style reverb */
class Reverb : Fx() {
    private val combs = listOf(1116, 1188, 1277, 1356).map { Comb(it) }
    private val aps = listOf(556, 441).map { Ap(it) }
    var mix = 0.25f; var size = 0.84f

    override fun run(b: FloatArray, n: Int) {
        for (i in 0 until n) {
            val x = b[i] * 0.15f
            var s = 0f
            for (c in combs) s += c.p(x, size, 0.2f)
            for (a in aps) s = a.p(s)
            b[i] = b[i] * (1 - mix) + s * mix * 3f
        }
    }
}

class Comp : Fx() {
    private var thr = -18f; private var ratio = 4f; private var makeup = 1f; private var env = 0f
    private val att = exp(-1f / (0.01f * SR)); private val rel = exp(-1f / (0.15f * SR))
    init { set(-18f, 4f) }

    fun set(thrDb: Float, r: Float) {
        thr = thrDb; ratio = r
        makeup = 10f.pow(-thrDb * (1 - 1 / r) * 0.5f / 20f) // auto makeup gain
    }

    override fun run(b: FloatArray, n: Int) {
        for (i in 0 until n) {
            val x = abs(b[i])
            env = if (x > env) att * env + (1 - att) * x else rel * env + (1 - rel) * x
            val over = 20f * log10(max(env, 1e-6f)) - thr
            val g = if (over > 0) 10f.pow(-(over * (1 - 1 / ratio)) / 20f) else 1f
            b[i] *= g * makeup
        }
    }
}

class Synth {
    private class V(val f: Float) { var ph = 0f; var lv = 0f; var down = true }
    private val voices = ConcurrentHashMap<Int, V>()

    fun on(note: Int) { voices[note] = V(440f * 2f.pow((note - 69) / 12f)) }
    fun off(note: Int) { voices[note]?.down = false }

    fun run(b: FloatArray, n: Int) {
        for ((k, v) in voices) {
            for (i in 0 until n) {
                v.lv = if (v.down) min(1f, v.lv + 0.005f) else v.lv - 0.0002f
                if (v.lv <= 0f) { voices.remove(k); break }
                v.ph += v.f / SR; if (v.ph >= 1f) v.ph -= 1f
                b[i] += ((2 * v.ph - 1) * 0.15f + sin(TWO_PI * v.ph) * 0.15f) * v.lv
            }
        }
    }
}

class Sampler {
    @Volatile var data = FloatArray(0); private set
    @Volatile var playing = false
    @Volatile var pos = 0            // playhead, in samples
    @Volatile var loopOn = false
    @Volatile var loopStart = 0
    @Volatile var loopEnd = 0        // exclusive

    fun load(d: FloatArray) { playing = false; pos = 0; loopStart = 0; loopEnd = d.size; loopOn = false; data = d }
    fun play(from: Int) { if (data.isEmpty()) return; pos = from.coerceIn(0, data.size - 1); playing = true }
    fun stop() { playing = false }

    fun run(b: FloatArray, n: Int) {
        if (!playing) return
        val d = data
        val looping = loopOn && loopEnd - loopStart > 1
        val end = if (looping) min(loopEnd, d.size) else d.size
        var p = pos
        for (i in 0 until n) {
            if (p >= end) {
                if (looping) p = loopStart.coerceIn(0, d.size - 1) else { playing = false; break }
            }
            b[i] += d[p] * 0.8f; p++
        }
        pos = p
    }
}
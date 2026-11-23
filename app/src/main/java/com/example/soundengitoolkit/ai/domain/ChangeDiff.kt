package com.example.soundengitoolkit.ai.domain

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** One parameter that really changed. [fx] groups it with the model's "why" for that effect. */
data class Change(val fx: String, val param: String, val from: String, val to: String)

/**
 * "What changed" is computed on the device from two snapshots, not taken from the model's text (REQ-7):
 * the message can never claim a change that did not happen, or hide one that did (AC-3).
 */
object ChangeDiff {
    fun diff(b: FxState, a: FxState): List<Change> {
        val out = mutableListOf<Change>()
        fun num(fx: String, name: String, x: Float, y: Float, fmt: (Float) -> String) {
            if (abs(x - y) > 1e-3f) out += Change(fx, name, fmt(x), fmt(y))
        }
        fun flag(fx: String, name: String, x: Boolean, y: Boolean) {
            if (x != y) out += Change(fx, name, x.onOff(), y.onOff())
        }
        num("bpm", "BPM", b.bpm, a.bpm) { it.roundToInt().toString() }
        flag("delay", "Delay", b.delayOn, a.delayOn)
        if (b.delayDivision != a.delayDivision) out += Change("delay", "Delay time", "1/${b.delayDivision}", "1/${a.delayDivision}")
        num("delay", "Delay feedback", b.delayFeedback, a.delayFeedback) { it.pct() }
        num("delay", "Delay mix", b.delayMix, a.delayMix) { it.pct() }
        flag("reverb", "Reverb", b.reverbOn, a.reverbOn)
        num("reverb", "Reverb mix", b.reverbMix, a.reverbMix) { it.pct() }
        num("reverb", "Reverb size", b.reverbSize, a.reverbSize) { it.pct() }
        flag("compressor", "Compressor", b.compOn, a.compOn)
        num("compressor", "Compressor threshold", b.compThreshold, a.compThreshold) { it.db() }
        num("compressor", "Compressor ratio", b.compRatio, a.compRatio) { it.one() + ":1" }
        flag("eq", "EQ", b.eqOn, a.eqOn)
        num("eq", "EQ low", b.eqLow, a.eqLow) { it.db() }
        num("eq", "EQ mid", b.eqMid, a.eqMid) { it.db() }
        num("eq", "EQ high", b.eqHigh, a.eqHigh) { it.db() }
        return out
    }
}

private fun Boolean.onOff() = if (this) "on" else "off"
private fun Float.pct() = "${(this * 100).roundToInt()}%"
private fun Float.one() = String.format(Locale.US, "%.1f", this).removeSuffix(".0")
private fun Float.db() = (if (this > 0) "+" else "") + one() + " dB"

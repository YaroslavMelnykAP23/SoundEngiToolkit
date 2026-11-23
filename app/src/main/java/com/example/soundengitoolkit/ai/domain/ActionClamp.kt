package com.example.soundengitoolkit.ai.domain

import java.util.Locale

/** Actions after range checks, plus human-readable warnings about every value that was changed. */
data class Clamped(val actions: List<PlannedAction>, val warnings: List<String>)

/**
 * Hard limits from [Ai.SYSTEM] (R-28, REQ-6). The model is told the ranges, but the device does not trust it:
 * e.g. delay feedback >= 1 makes the echo grow without end (L-03).
 */
object ActionClamp {
    val BPM = 40f..240f
    val FEEDBACK = 0f..0.9f
    val MIX = 0f..1f
    val SIZE = 0.5f..0.95f
    val THRESHOLD = -40f..0f
    val RATIO = 1f..20f
    val BAND = -12f..12f
    val DIVISIONS = listOf(4, 8, 16)

    fun clamp(actions: List<PlannedAction>): Clamped {
        val w = mutableListOf<String>()
        fun c(name: String, v: Float?, r: ClosedFloatingPointRange<Float>): Float? {
            if (v == null) return null
            val x = v.coerceIn(r.start, r.endInclusive)
            if (x != v) w += "$name ${v.f()} → ${x.f()} (allowed ${r.start.f()}…${r.endInclusive.f()})"
            return x
        }
        fun div(v: Int?): Int? {
            if (v == null) return null
            val x = DIVISIONS.minBy { kotlin.math.abs(it - v) }
            if (x != v) w += "delay division 1/$v → 1/$x (allowed 1/4, 1/8, 1/16)"
            return x
        }
        val out = actions.map { p ->
            val a = when (val a = p.action) {
                is AiAction.Bpm -> AiAction.Bpm(c("BPM", a.value, BPM))
                is AiAction.DelayFx -> AiAction.DelayFx(a.on, div(a.division), c("delay feedback", a.feedback, FEEDBACK), c("delay mix", a.mix, MIX))
                is AiAction.ReverbFx -> AiAction.ReverbFx(a.on, c("reverb mix", a.mix, MIX), c("reverb size", a.size, SIZE))
                is AiAction.CompressorFx -> AiAction.CompressorFx(a.on, c("compressor threshold", a.threshold, THRESHOLD), c("compressor ratio", a.ratio, RATIO))
                is AiAction.EqFx -> AiAction.EqFx(a.on, c("EQ low", a.low, BAND), c("EQ mid", a.mid, BAND), c("EQ high", a.high, BAND))
                is AiAction.Unknown -> a
            }
            p.copy(action = a)
        }
        return Clamped(out, w)
    }

    /** Old scenario: same limits, no explanations. */
    fun clampActions(actions: List<AiAction>): List<AiAction> =
        clamp(actions.map { PlannedAction(it) }).actions.map { it.action }
}

private fun Float.f(): String = String.format(Locale.US, "%.2f", this).trimEnd('0').trimEnd('.')

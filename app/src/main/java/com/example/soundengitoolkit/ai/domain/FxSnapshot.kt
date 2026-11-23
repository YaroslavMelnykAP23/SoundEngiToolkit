package com.example.soundengitoolkit.ai.domain

import org.json.JSONObject
import com.example.soundengitoolkit.audio.Engine

/**
 * Snapshot of all parameters the AI may change. Pure data: used to give the model a reference point
 * for relative commands ("longer", "less") and, in step 2b, to compute what actually changed.
 */
data class FxSnapshot(
    val bpm: Float,
    val delayOn: Boolean, val delayDivision: Int, val delayFeedback: Float, val delayMix: Float,
    val reverbOn: Boolean, val reverbMix: Float, val reverbSize: Float,
    val compOn: Boolean, val compThreshold: Float, val compRatio: Float,
    val eqOn: Boolean, val eqLow: Float, val eqMid: Float, val eqHigh: Float
) {
    /** The "state" object sent to the model. Rounded: the model does not need 7 decimal places. */
    fun toJson(): JSONObject = JSONObject()
        .put("bpm", bpm.r())
        .put("delay", JSONObject().put("on", delayOn).put("division", delayDivision)
            .put("feedback", delayFeedback.r()).put("mix", delayMix.r()))
        .put("reverb", JSONObject().put("on", reverbOn).put("mix", reverbMix.r()).put("size", reverbSize.r()))
        .put("compressor", JSONObject().put("on", compOn).put("threshold", compThreshold.r()).put("ratio", compRatio.r()))
        .put("eq", JSONObject().put("on", eqOn).put("low", eqLow.r()).put("mid", eqMid.r()).put("high", eqHigh.r()))

    companion object {
        fun from(e: Engine) = FxSnapshot(
            bpm = e.bpm,
            delayOn = e.delay.on, delayDivision = e.delay.denom, delayFeedback = e.delay.fb, delayMix = e.delay.mix,
            reverbOn = e.reverb.on, reverbMix = e.reverb.mix, reverbSize = e.reverb.size,
            compOn = e.comp.on, compThreshold = e.comp.thr, compRatio = e.comp.ratio,
            eqOn = e.eq.on, eqLow = e.eq.lowDb, eqMid = e.eq.midDb, eqHigh = e.eq.highDb
        )
    }
}

private fun Float.r(): Double = Math.round(this * 100.0) / 100.0

package com.example.soundengitoolkit.ai.domain

/**
 * Typed AI action. Null fields mean "not specified by the source".
 * Defaults for missing values stay in the executor, so current behaviour (L-04) is preserved.
 */
sealed interface AiAction {
    data class Bpm(val value: Float?) : AiAction
    data class DelayFx(val on: Boolean?, val division: Int?, val feedback: Float?, val mix: Float?) : AiAction
    data class ReverbFx(val on: Boolean?, val mix: Float?, val size: Float?) : AiAction
    data class CompressorFx(val on: Boolean?, val threshold: Float?, val ratio: Float?) : AiAction
    data class EqFx(val on: Boolean?, val low: Float?, val mid: Float?, val high: Float?) : AiAction
    /** Unrecognized `fx`; kept so the log can still show `? name`. */
    data class Unknown(val fx: String) : AiAction
}
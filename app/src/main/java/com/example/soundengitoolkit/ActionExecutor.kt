package com.example.soundengitoolkit

/** Applies typed actions to the Engine through the same setters the UI uses. Returns a human-readable log. */
object ActionExecutor {
    fun apply(e: Engine, actions: List<AiAction>): String {
        val log = StringBuilder()
        for (a in actions) {
            when (a) {
                is AiAction.Bpm -> e.bpm = a.value ?: e.bpm
                is AiAction.DelayFx -> e.delay.apply {
                    on = a.on ?: on; denom = a.division ?: denom
                    fb = a.feedback ?: fb; mix = a.mix ?: mix; sync(e.bpm)
                }
                is AiAction.ReverbFx -> e.reverb.apply {
                    on = a.on ?: on; mix = a.mix ?: mix; size = a.size ?: size
                }
                is AiAction.CompressorFx -> e.comp.apply {
                    on = a.on ?: on; set(a.threshold ?: -18f, a.ratio ?: 4f)   // L-04
                }
                is AiAction.EqFx -> e.eq.apply {
                    on = a.on ?: true; set(a.low ?: 0f, a.mid ?: 0f, a.high ?: 0f)   // L-04
                }
                is AiAction.Unknown -> { log.append("? ${a.fx}\n"); continue }
            }
            log.append("✓ ${describe(a)}\n")
        }
        return log.toString()
    }

    private fun fields(vararg p: Pair<String, Any?>) =
        p.filter { it.second != null }.joinToString(" ") { "${it.first}=${it.second}" }

    private fun describe(a: AiAction): String = when (a) {
        is AiAction.Bpm -> "bpm " + fields("value" to a.value)
        is AiAction.DelayFx -> "delay " + fields("on" to a.on, "division" to a.division, "feedback" to a.feedback, "mix" to a.mix)
        is AiAction.ReverbFx -> "reverb " + fields("on" to a.on, "mix" to a.mix, "size" to a.size)
        is AiAction.CompressorFx -> "compressor " + fields("on" to a.on, "threshold" to a.threshold, "ratio" to a.ratio)
        is AiAction.EqFx -> "eq " + fields("on" to a.on, "low" to a.low, "mid" to a.mid, "high" to a.high)
        is AiAction.Unknown -> a.fx
    }
}
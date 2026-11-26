package com.example.soundengitoolkit.ai.domain

/** What the chat shows after a run. */
sealed interface ChatAnswer {
    /** Something audible changed: list of changes, why per effect, warnings, model summary. */
    data class Changed(
        val changes: List<Change>,
        val why: Map<String, String>,
        val warnings: List<String>,
        val summary: String?
    ) : ChatAnswer

    /** "No usable result": nothing (audible) changed. [message] says why and what to try. */
    data class NothingChanged(val message: String, val warnings: List<String> = emptyList()) : ChatAnswer
}

/**
 * Builds the answer from the REAL difference of states plus the model's "why" (REQ-7).
 * Rules give no "why": a short default explanation per effect is used instead, so every change is explained (AC-2).
 */
object ExplanationBuilder {
    const val HELP = "I can change BPM, delay, reverb, compressor and EQ. Try: \"reverb off\" or \"make it brighter\"."

    /**
     * [explicitCommand] = the actions come from the rules, i.e. the user literally asked for them ("eq on").
     * Then "EQ off -> on" with flat bands is what was asked and is listed as a change (Lab 7, BUG-01);
     * from the model the same action means "warmer" was not done, so it is still reported as nothing changed.
     */
    fun build(
        actions: List<PlannedAction>, reply: String?, warnings: List<String>, before: FxSnapshot, after: FxSnapshot,
        explicitCommand: Boolean = false
    ): ChatAnswer {
        val changes = ChangeDiff.diff(before, after)
        val flatEq = after.eqOn && !before.eqOn && after.eqLow == 0f && after.eqMid == 0f && after.eqHigh == 0f
        val audible = changes.filterNot { flatEq && !explicitCommand && it.param == "EQ" }

        if (audible.isEmpty()) {
            val msg = when {
                actions.none { it.action !is AiAction.Unknown } -> reply ?: HELP
                flatEq -> "EQ is on, but all bands are 0 dB, so the sound did not change. Try again or say e.g. \"more bass\"."
                else -> "Nothing changed: the settings already match the request." + (reply?.let { " $it" } ?: "")
            }
            return ChatAnswer.NothingChanged(msg, warnings)
        }

        val why = LinkedHashMap<String, String>()
        for (fx in audible.map { it.fx }.distinct()) {
            val fromModel = actions.firstOrNull { fxOf(it.action) == fx }?.why
            why[fx] = fromModel ?: defaultWhy(fx, after)
        }
        return ChatAnswer.Changed(audible, why, warnings, reply)
    }

    /** Plain text for history, logs and tests. The chat screen renders the same data with styling. */
    fun text(a: ChatAnswer): String = when (a) {
        is ChatAnswer.NothingChanged -> (listOf(a.message) + a.warnings.map { "⚠ $it" }).joinToString("\n")
        is ChatAnswer.Changed -> buildString {
            a.summary?.let { appendLine(it) }
            a.changes.forEach { appendLine("• ${it.param}: ${it.from} → ${it.to}") }
            a.why.forEach { (fx, w) -> appendLine("Why (${label(fx)}): $w") }
            a.warnings.forEach { appendLine("⚠ $it") }
        }.trimEnd()
    }

    /** Short summary of the changes, used as "result" in the chat history sent back to the model. */
    fun summary(a: ChatAnswer): String = when (a) {
        is ChatAnswer.Changed -> a.changes.joinToString("; ") { "${it.param} ${it.from}→${it.to}" }
        is ChatAnswer.NothingChanged -> "nothing changed"
    }

    private fun fxOf(a: AiAction): String? = when (a) {
        is AiAction.Bpm -> "bpm"
        is AiAction.DelayFx -> "delay"
        is AiAction.ReverbFx -> "reverb"
        is AiAction.CompressorFx -> "compressor"
        is AiAction.EqFx -> "eq"
        is AiAction.Unknown -> null
    }

    private fun label(fx: String) = when (fx) {
        "bpm" -> "BPM"; "eq" -> "EQ"; else -> fx.replaceFirstChar { it.uppercase() }
    }

    private fun defaultWhy(fx: String, s: FxSnapshot): String = when (fx) {
        "bpm" -> "Tempo sets the beat grid and the delay time."
        "delay" -> if (s.delayOn) "Delay repeats the sound in time with the tempo (1/${s.delayDivision} of a bar)." else "Without delay there are no echoes."
        "reverb" -> if (s.reverbOn) "Reverb adds a sense of space around the sound." else "Without reverb the sound is drier and closer."
        "compressor" -> if (s.compOn) "The compressor evens out loud and quiet parts." else "Without compression the dynamics stay natural."
        "eq" -> when {
            !s.eqOn -> "EQ off: the tone is not shaped."
            s.eqLow == 0f && s.eqMid == 0f && s.eqHigh == 0f ->
                "EQ is on, all bands are 0 dB: the tone changes once you adjust them, e.g. \"more bass\"."
            else -> "The EQ shapes the tone by boosting or cutting frequency bands."
        }
        else -> "Done as requested."
    }
}

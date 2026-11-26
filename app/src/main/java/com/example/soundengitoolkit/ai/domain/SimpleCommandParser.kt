package com.example.soundengitoolkit.ai.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Rules for SIMPLE, unambiguous commands only (docs/ai-chat.md, section 2).
 * A command is simple when every word is understood: tempo ("120 bpm"), delay division ("delay 1/8"),
 * switching an effect on/off ("reverb off", "вимкни компресор") and filler words.
 * Anything else ("зроби delay довшим", "make it warmer") returns null and goes to the cloud model.
 * The command may have several parts separated by "and", "і", "та" or a comma.
 */
object SimpleCommandParser {
    private val SEPARATORS = setOf(",", "and", "і", "та", "й")
    private val FILLER = setOf("add", "set", "turn", "at", "the", "a", "to", "please", "with", "на", "з", "із", "до")
    private val ON = setOf("on", "enable", "увімкни", "ввімкни", "включи", "увімкнути", "додай")
    private val OFF = setOf("off", "disable", "вимкни", "виключи", "вимкнути", "прибери")
    private val BPM_WORDS = setOf("bpm", "темп", "tempo")
    private val DIVISIONS = setOf(4, 8, 16)
    private val DIV = Regex("^1/(\\d+)$")
    private val NUM = Regex("^\\d+$")
    private val NUM_BPM = Regex("^(\\d+)bpm$")

    /** Typed actions, or null if the command is not simple (then it is not the rules' job). */
    fun parse(prompt: String): List<AiAction>? {
        val tokens = prompt.lowercase().replace(",", " , ").replace(";", " , ")
            .split(Regex("\\s+")).map { it.trim('.', '!', '?', '"', '\'', '«', '»') }.filter { it.isNotEmpty() }
        val clauses = mutableListOf(mutableListOf<String>())
        for (t in tokens) if (t in SEPARATORS) clauses.add(mutableListOf()) else clauses.last().add(t)
        val actions = mutableListOf<AiAction>()
        for (c in clauses) if (c.isNotEmpty()) actions += clause(c) ?: return null
        return actions.ifEmpty { null }
    }

    /** {"actions":[...]} in the same format the cloud model uses. Only non-null fields are written. */
    fun toJson(actions: List<AiAction>): String {
        val arr = JSONArray()
        for (a in actions) arr.put(
            when (a) {
                is AiAction.Bpm -> JSONObject().put("fx", "bpm").putOpt("value", a.value?.toInt())
                is AiAction.DelayFx -> JSONObject().put("fx", "delay").putOpt("on", a.on).putOpt("division", a.division)
                is AiAction.ReverbFx -> JSONObject().put("fx", "reverb").putOpt("on", a.on)
                is AiAction.CompressorFx -> JSONObject().put("fx", "compressor").putOpt("on", a.on)
                is AiAction.EqFx -> JSONObject().put("fx", "eq").putOpt("on", a.on)
                is AiAction.Unknown -> JSONObject().put("fx", a.fx)
            }
        )
        return JSONObject().put("actions", arr).toString()
    }

    private fun fxOf(t: String): String? = when {
        t == "delay" || t.startsWith("ділей") || t.startsWith("дилей") || t.startsWith("делей") -> "delay"
        t.startsWith("reverb") || t.startsWith("ревер") -> "reverb"
        t.startsWith("compress") || t.startsWith("компрес") -> "compressor"
        t == "eq" || t.startsWith("еквал") -> "eq"
        else -> null
    }

    /** One part of the command: at most one effect, at most one tempo. Any unknown word -> null. */
    private fun clause(c: List<String>): List<AiAction>? {
        var fx: String? = null
        var on: Boolean? = null
        var division: Int? = null
        var number: Int? = null
        var bpmWord = false
        for (t in c) {
            val name = fxOf(t)
            when {
                name != null -> { if (fx != null && fx != name) return null; fx = name }
                t in ON -> { if (on == false) return null; on = true }
                t in OFF -> { if (on == true) return null; on = false }
                t in BPM_WORDS -> bpmWord = true
                DIV.matches(t) -> {
                    val d = DIV.find(t)!!.groupValues[1].toInt()
                    if (division != null || d !in DIVISIONS) return null
                    division = d
                }
                NUM_BPM.matches(t) -> { if (number != null) return null; number = NUM_BPM.find(t)!!.groupValues[1].toInt(); bpmWord = true }
                NUM.matches(t) -> { if (number != null) return null; number = t.toInt() }
                t in FILLER -> {}
                else -> return null                                  // e.g. "довшим", "warmer", "наполовину"
            }
        }
        // "reverb 50" -> cloud. Lab 7 (BUG-04): a tempo outside 40..240 ("300 bpm") is still a simple command:
        // the rules take it and ActionClamp limits it to the nearest bound with a warning (REQ-6), no model needed.
        if (number != null && (!bpmWord || number !in 1..999)) return null
        if (number == null && bpmWord) return null                             // "bpm" without a value
        if (division != null && (fx != "delay" || on == false)) return null
        if (fx == null && (number == null || on != null)) return null         // "turn off" without an effect

        val out = mutableListOf<AiAction>()
        number?.let { out += AiAction.Bpm(it.toFloat()) }
        when (fx) {
            "delay" -> out += AiAction.DelayFx(on ?: true, division, null, null)
            "reverb" -> out += AiAction.ReverbFx(on ?: true, null, null)
            "compressor" -> out += AiAction.CompressorFx(on ?: true, null, null)
            "eq" -> out += AiAction.EqFx(on ?: true, null, null, null)
        }
        return out
    }
}

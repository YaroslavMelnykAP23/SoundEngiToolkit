package com.example.soundengitoolkit

import org.json.JSONObject

/**
 * AI never touches audio directly: a CommandSource returns a JSON list of actions,
 * and the app executes them through the same setters the UI uses.
 */
object Ai {
    // Swap the source here: RulesSource(), FirebaseSource(), or the hybrid below.
    var source: CommandSource = HybridSource(RulesSource(), FirebaseSource())

    suspend fun ask(prompt: String): String = source.ask(prompt)

    const val SYSTEM = """You control an audio app. Reply with ONLY a JSON object, no prose, no markdown:
{"actions":[...]}. Allowed actions:
{"fx":"bpm","value":40-240}
{"fx":"delay","on":bool,"division":4|8|16,"feedback":0-0.9,"mix":0-1}
{"fx":"reverb","on":bool,"mix":0-1,"size":0.5-0.95}
{"fx":"compressor","on":bool,"threshold":-40..0,"ratio":1-20}
{"fx":"eq","on":bool,"low":-12..12,"mid":-12..12,"high":-12..12}
Omit fields the user did not ask to change."""

    /** Runs on any thread; returns human-readable log. */
    fun apply(e: Engine, json: String): String {
        val log = StringBuilder()
        val arr = JSONObject(json).getJSONArray("actions")
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            fun f(k: String, d: Float) = if (o.has(k)) o.getDouble(k).toFloat() else d
            when (val fx = o.getString("fx")) {
                "bpm" -> e.bpm = f("value", e.bpm)
                "delay" -> e.delay.apply {
                    on = o.optBoolean("on", on); denom = o.optInt("division", denom)
                    fb = f("feedback", fb); mix = f("mix", mix); sync(e.bpm)
                }
                "reverb" -> e.reverb.apply { on = o.optBoolean("on", on); mix = f("mix", mix); size = f("size", size) }
                "compressor" -> e.comp.apply {
                    on = o.optBoolean("on", on); set(f("threshold", -18f), f("ratio", 4f))
                }
                "eq" -> e.eq.apply {
                    on = o.optBoolean("on", true); set(f("low", 0f), f("mid", 0f), f("high", 0f))
                }
                else -> { log.append("? $fx\n"); continue }
            }
            log.append("✓ $o\n")
        }
        return log.toString()
    }
}
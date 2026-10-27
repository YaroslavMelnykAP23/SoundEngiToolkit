package com.example.soundengitoolkit

/**
 * AI never touches audio directly: a CommandSource returns a JSON list of actions,
 * and the app executes them through the same setters the UI uses.
 * Wiring of sources lives in AppContainer (composition root).
 */
object Ai {
    const val SYSTEM = """You control an audio app. Reply with ONLY a JSON object, no prose, no markdown:
{"actions":[...]}. Allowed actions:
{"fx":"bpm","value":40-240}
{"fx":"delay","on":bool,"division":4|8|16,"feedback":0-0.9,"mix":0-1}
{"fx":"reverb","on":bool,"mix":0-1,"size":0.5-0.95}
{"fx":"compressor","on":bool,"threshold":-40..0,"ratio":1-20}
{"fx":"eq","on":bool,"low":-12..12,"mid":-12..12,"high":-12..12}
Omit fields the user did not ask to change."""

    /** Runs on any thread; returns human-readable log. Parses everything first, then applies. */
    fun apply(e: Engine, json: String): String =
        ActionExecutor.apply(e, AiActionMapper.parse(json))
}
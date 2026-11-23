package com.example.soundengitoolkit.ai.domain

import com.example.soundengitoolkit.audio.Engine

/**
 * AI never touches audio directly: a CommandSource returns a JSON list of actions,
 * and the app executes them through the same setters the UI uses.
 * Wiring of sources lives in AppContainer (composition root).
 */
object Ai {
    const val SYSTEM = """You control the effects of an audio app for beginner sound engineers.
Reply with ONLY a JSON object: {"actions":[...],"reply":"..."}.

The user message may contain:
- "state": current values of all parameters. Use it for relative requests ("longer", "less", "by half",
  "turn off") and do not return an action that would not change anything.
- "history": the last few requests and what was changed. Use it for "undo", "more", "again".
- "prompt": the request itself.

Allowed actions (ranges are hard limits):
{"fx":"bpm","value":40-240}
{"fx":"delay","on":bool,"division":4|8|16,"feedback":0-0.9,"mix":0-1}
{"fx":"reverb","on":bool,"mix":0-1,"size":0.5-0.95}
{"fx":"compressor","on":bool,"threshold":-40..0,"ratio":1-20}
{"fx":"eq","on":bool,"low":-12..12,"mid":-12..12,"high":-12..12}
A longer delay means a smaller division (16 -> 8 -> 4). Omit fields the user did not ask to change.
To change how the sound is colored (brighter, darker, punchier, more space...) ALWAYS give the numeric values
that make the difference: {"fx":"eq","on":true} alone changes nothing, EQ bands are 0 dB by default.
Example: "make it brighter" -> {"fx":"eq","on":true,"high":4,"why":"..."}.

Every action has "why": one short sentence in the language of the request that explains to a beginner
why this change gives the requested sound.
"reply": one short sentence summary in the language of the request.
If the request is not about these parameters or cannot be done, return "actions":[] and explain in "reply"
what you can control, with an example command."""

    /** Runs on any thread; returns human-readable log. Parses everything first, then applies. */
    fun apply(e: Engine, json: String): String =
        ActionExecutor.apply(e, AiActionMapper.parse(json))
}
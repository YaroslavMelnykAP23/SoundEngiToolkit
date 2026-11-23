package com.example.soundengitoolkit.ai.domain

import org.json.JSONObject

/** One action plus the model's explanation of why it gives the requested sound (null for rules). */
data class PlannedAction(val action: AiAction, val why: String? = null)

/** Parsed reply: actions in order and the model's one-line summary (null for rules). */
data class ParsedReply(val actions: List<PlannedAction>, val reply: String?)

/**
 * JSON {"actions":[{fx, ..., "why"}], "reply"} -> [ParsedReply]. Typed mapping is reused from [AiActionMapper],
 * so both scenarios understand exactly the same actions. Throws JSONException on malformed JSON.
 */
object AiReplyParser {
    fun parse(json: String): ParsedReply {
        val o = JSONObject(json)
        val arr = o.getJSONArray("actions")
        val actions = AiActionMapper.parse(json)
        val whys = List(arr.length()) { i -> arr.getJSONObject(i).optString("why").trim().ifEmpty { null } }
        return ParsedReply(
            actions.zip(whys) { a, w -> PlannedAction(a, w) },
            o.optString("reply").trim().ifEmpty { null }
        )
    }
}

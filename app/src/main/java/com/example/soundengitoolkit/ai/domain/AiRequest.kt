package com.example.soundengitoolkit.ai.domain

import org.json.JSONArray
import org.json.JSONObject

/** One previous exchange of the chat: what the user asked and a short summary of what was changed. */
data class ChatTurn(val prompt: String, val result: String)

/**
 * Everything a [CommandSource] may use. [state] is null in the old "AI command" block (no context there).
 * Only what [toUserMessage] writes leaves the device: no audio, no project names, no full history.
 */
data class AiRequest(
    val prompt: String,
    val state: FxState? = null,
    val history: List<ChatTurn> = emptyList()
) {
    /** The user message sent to the cloud model: {"state":{...},"history":[...],"prompt":"..."}. */
    fun toUserMessage(): String {
        val o = JSONObject()
        state?.let { o.put("state", it.toJson()) }
        if (history.isNotEmpty()) {
            val h = JSONArray()
            history.forEach { h.put(JSONObject().put("prompt", it.prompt).put("result", it.result)) }
            o.put("history", h)
        }
        return o.put("prompt", prompt).toString()
    }
}

/** Data preparation: validation and trimming happen here, before any source is called. */
object AiRequestBuilder {
    const val MAX_PROMPT = 500
    const val MAX_HISTORY = 3

    /** Throws [AiException] with [AiError.InvalidInput] for a blank or too long prompt. */
    fun build(prompt: String, state: FxState? = null, history: List<ChatTurn> = emptyList()): AiRequest {
        val p = prompt.trim()
        if (p.isEmpty() || p.length > MAX_PROMPT) throw AiException(AiError.InvalidInput)
        return AiRequest(p, state, history.takeLast(MAX_HISTORY))
    }
}

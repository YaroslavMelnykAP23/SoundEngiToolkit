package com.example.soundengitoolkit.ai.domain

/** JSON {"actions":[...]} plus the name of the source that produced it (rules, cloud, ...). */
data class CommandReply(val json: String, val source: String)

/** Turns a request (prompt + optional state and history) into a reply with JSON {"actions":[...]}. */
interface CommandSource {
    suspend fun ask(request: AiRequest): CommandReply
}

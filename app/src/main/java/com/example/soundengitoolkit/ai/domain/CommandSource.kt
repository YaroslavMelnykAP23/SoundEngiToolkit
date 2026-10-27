package com.example.soundengitoolkit.ai.domain

/** JSON {"actions":[...]} plus the name of the source that produced it (rules, cloud, ...). */
data class CommandReply(val json: String, val source: String)

/** Turns a user prompt into a reply with JSON {"actions":[...]} for the action mapper. */
interface CommandSource {
    suspend fun ask(prompt: String): CommandReply
}

package com.example.soundengitoolkit.ai.domain

import kotlinx.coroutines.CancellationException

/** Successful result: typed actions, the raw JSON (for history) and which source answered. */
data class AiReply(val actions: List<AiAction>, val json: String, val source: String)

/** Scenario "AI command": validate -> ask source -> parse. No Android, no Engine, no UI. */
class RunAiCommandUseCase(private val source: CommandSource) {
    suspend operator fun invoke(prompt: String): Result<AiReply> {
        if (prompt.isBlank()) return Result.failure(AiException(AiError.InvalidInput))
        return try {
            val reply = source.ask(prompt.trim())
            Result.success(AiReply(AiActionMapper.parse(reply.json), reply.json, reply.source))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(AiException(AiErrorMapper.map(e), e))
        }
    }
}
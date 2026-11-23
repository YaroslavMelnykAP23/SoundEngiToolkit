package com.example.soundengitoolkit.ai.domain

import kotlinx.coroutines.CancellationException

/** Successful result: typed actions, the raw JSON (for history) and which source answered. */
data class AiReply(val actions: List<AiAction>, val json: String, val source: String)

/**
 * Old scenario "AI command" (block on the Studio tab): validate -> ask source with timeout -> parse.
 * Sends no state and no history (state = null): this block has no context, the AI chat does.
 */
class RunAiCommandUseCase(
    private val source: CommandSource,
    private val timeoutMs: Long = AiCall.TIMEOUT_MS
) {
    suspend operator fun invoke(prompt: String): Result<AiReply> {
        val request = try {
            AiRequestBuilder.build(prompt)
        } catch (e: AiException) {
            return Result.failure(e)
        }
        return try {
            val reply = AiCall.ask(source, request, timeoutMs)
            // Same range limits as the chat (R-28): the old block must not set feedback 1.5 either.
            Result.success(AiReply(ActionClamp.clampActions(AiActionMapper.parse(reply.json)), reply.json, reply.source))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(AiException(AiErrorMapper.map(e), e))
        }
    }
}

package com.example.soundengitoolkit.ai.domain

import kotlinx.coroutines.CancellationException

/** Ready-to-apply result of one chat request. Applying it to the Engine happens in the ViewModel (main thread). */
data class AiPlan(
    val actions: List<PlannedAction>,
    val reply: String?,
    val warnings: List<String>,
    val json: String,
    val source: String
)

/**
 * Scenario "AI chat": [1] data preparation -> [2] source with timeout -> [3] parse + clamp.
 * No Android, no Engine, no UI: testable on the JVM with a fake source.
 */
class RunAiChatUseCase(
    private val source: CommandSource,
    private val timeoutMs: Long = AiCall.TIMEOUT_MS
) {
    suspend operator fun invoke(prompt: String, state: FxSnapshot, history: List<ChatTurn>): Result<AiPlan> {
        val request = try {
            AiRequestBuilder.build(prompt, state, history)                 // [1]
        } catch (e: AiException) {
            return Result.failure(e)
        }
        return try {
            val reply = AiCall.ask(source, request, timeoutMs)            // [2]
            val parsed = AiReplyParser.parse(reply.json)                  // [3]
            val clamped = ActionClamp.clamp(parsed.actions)
            Result.success(AiPlan(clamped.actions, parsed.reply, clamped.warnings, reply.json, reply.source))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            Result.failure(AiException(AiErrorMapper.map(e), e))
        }
    }
}

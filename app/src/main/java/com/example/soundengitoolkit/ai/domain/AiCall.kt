package com.example.soundengitoolkit.ai.domain

import kotlinx.coroutines.withTimeoutOrNull

/** Calls a source with a hard time limit, so the user never waits for the SDK's own (minutes long) timeout. */
object AiCall {
    /** See docs/ai-chat.md (REQ-10) and evaluation.md 1.3: without it a request waited up to 3 minutes. */
    const val TIMEOUT_MS = 20_000L

    suspend fun ask(source: CommandSource, request: AiRequest, timeoutMs: Long = TIMEOUT_MS): CommandReply =
        withTimeoutOrNull(timeoutMs) { source.ask(request) } ?: throw AiException(AiError.Timeout)
}

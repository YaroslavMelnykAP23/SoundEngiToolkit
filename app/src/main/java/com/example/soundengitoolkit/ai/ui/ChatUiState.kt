package com.example.soundengitoolkit.ai.ui

import com.example.soundengitoolkit.ai.domain.AiError
import com.example.soundengitoolkit.ai.domain.ChatAnswer

/** One bubble in the AI chat. */
sealed interface ChatMessage {
    val id: Long

    data class User(override val id: Long, val text: String) : ChatMessage

    /** Success (something changed) or "no usable result" (nothing changed); see [ChatAnswer]. */
    data class Answer(override val id: Long, val answer: ChatAnswer, val source: String) : ChatMessage

    /** Controlled error: category, text for the user, whether Retry makes sense. */
    data class Failure(override val id: Long, val error: AiError, val message: String, val retryable: Boolean) : ChatMessage
}

/**
 * Whole chat screen state. States from docs/ai-chat.md 7.5:
 * Idle = !loading; Loading = loading; Success / NoResult / Error = type of the last message.
 */
data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val loading: Boolean = false
) {
    /** Retry is offered only for the last message, and only if it is a retryable failure. */
    val canRetry: Boolean get() = !loading && (messages.lastOrNull() as? ChatMessage.Failure)?.retryable == true
}

/** Category -> text for the user. No exception classes, no stack traces. */
fun AiError.userMessage(): String = when (this) {
    AiError.InvalidInput -> "Enter a command first"
    AiError.Network -> "No connection. Check the network and try again"
    AiError.Timeout -> "AI did not answer in time. Try again"
    AiError.Busy -> "AI is busy right now. Try again in a minute"
    AiError.AiUnavailable -> "Cloud AI is unavailable. Offline commands still work"
    AiError.InvalidResponse -> "The AI reply could not be understood"
    AiError.Unknown -> "Something went wrong"
}

/** Chat texts: like [userMessage], but offline it also says what still works without network. */
fun AiError.chatMessage(): String = when (this) {
    AiError.Network -> "No connection. Simple commands still work offline: \"120 bpm\", \"reverb off\", \"delay 1/8\"."
    AiError.AiUnavailable -> "Cloud AI is unavailable (setup or quota). Simple commands still work."
    else -> userMessage()
}

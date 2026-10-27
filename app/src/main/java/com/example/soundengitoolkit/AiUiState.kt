package com.example.soundengitoolkit

/** What the AI command block of the screen shows. */
sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class Success(val log: String, val source: String) : AiUiState
    data class Error(val error: AiError, val message: String = error.userMessage()) : AiUiState
}

/** Category -> text for the user. No exception classes, no stack traces. */
fun AiError.userMessage(): String = when (this) {
    AiError.InvalidInput -> "Enter a command first"
    AiError.Network -> "No connection. Check the network and try again"
    AiError.AiUnavailable -> "Cloud AI is unavailable. Offline commands still work"
    AiError.InvalidResponse -> "The AI reply could not be understood"
    AiError.Unknown -> "Something went wrong"
}
package com.example.soundengitoolkit

/** Controlled error categories for the AI command scenario (UI never sees raw exceptions). */
sealed interface AiError {
    /** The user input is not acceptable (e.g. blank prompt). */
    data object InvalidInput : AiError
    /** No connection or timeout while reaching the cloud source. */
    data object Network : AiError
    /** The cloud model is not configured or refused the request. */
    data object AiUnavailable : AiError
    /** The source answered, but the reply is not valid action JSON. */
    data object InvalidResponse : AiError
    data object Unknown : AiError
}

/** Exception carrying an [AiError] category through Result/runCatching. */
class AiException(val error: AiError, cause: Throwable? = null) : Exception(error.toString(), cause)
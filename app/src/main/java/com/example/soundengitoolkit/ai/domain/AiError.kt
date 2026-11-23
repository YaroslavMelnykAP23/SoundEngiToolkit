package com.example.soundengitoolkit.ai.domain

/** Controlled error categories for the AI command scenario (UI never sees raw exceptions). */
sealed interface AiError {
    /** The user input is not acceptable (e.g. blank prompt). */
    data object InvalidInput : AiError
    /** No connection while reaching the cloud source. */
    data object Network : AiError
    /** The source did not answer within [AiCall.TIMEOUT_MS] (or the SDK timed out). */
    data object Timeout : AiError
    /** The model is temporarily overloaded ("high demand"): worth retrying later. */
    data object Busy : AiError
    /** The cloud model is not configured or refused the request (App Check, quota, model name). Retrying does not help. */
    data object AiUnavailable : AiError
    /** The source answered, but the reply is not valid action JSON. */
    data object InvalidResponse : AiError
    data object Unknown : AiError
}

/** Exception carrying an [AiError] category through Result/runCatching. */
class AiException(val error: AiError, cause: Throwable? = null) : Exception(error.toString(), cause)
package com.example.soundengitoolkit

import kotlinx.coroutines.CancellationException
import org.json.JSONException
import java.io.IOException

/** Technical exception -> controlled category. Never returns raw messages or stack traces. */
object AiErrorMapper {
    fun map(t: Throwable): AiError = when (t) {
        is AiException -> t.error
        is JSONException -> AiError.InvalidResponse
        is IOException -> AiError.Network            // includes UnknownHostException, SocketTimeoutException
        is CancellationException -> throw t          // cancellation is not an error
        is IllegalStateException -> AiError.AiUnavailable   // e.g. Firebase not configured, empty reply
        else -> AiError.Unknown
    }
}
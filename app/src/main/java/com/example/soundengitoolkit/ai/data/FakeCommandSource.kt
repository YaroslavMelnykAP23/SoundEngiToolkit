package com.example.soundengitoolkit.ai.data

import kotlinx.coroutines.delay
import java.io.IOException
import com.example.soundengitoolkit.ai.domain.AiError
import com.example.soundengitoolkit.ai.domain.AiException
import com.example.soundengitoolkit.ai.domain.AiRequest
import com.example.soundengitoolkit.ai.domain.CommandReply
import com.example.soundengitoolkit.ai.domain.CommandSource

/**
 * Second implementation of [CommandSource]: no network, no keys. For tests and the debug switch.
 * Use [delayMs] above the timeout to simulate a slow model.
 */
class FakeCommandSource(
    @Volatile var mode: Mode = Mode.SUCCESS,
    @Volatile var delayMs: Long = 0
) : CommandSource {
    enum class Mode { SUCCESS, NETWORK_ERROR, UNAVAILABLE, BUSY, INVALID_JSON, NO_ACTIONS, FAIL_ONCE }

    @Volatile var calls = 0
        private set

    /** The last request, so tests can check what would be sent to the model. */
    @Volatile var lastRequest: AiRequest? = null
        private set

    override suspend fun ask(request: AiRequest): CommandReply {
        calls++
        lastRequest = request
        if (delayMs > 0) delay(delayMs)
        return when (mode) {
            Mode.SUCCESS -> success()
            Mode.NETWORK_ERROR -> throw IOException("fake: offline")
            Mode.UNAVAILABLE -> throw AiException(AiError.AiUnavailable)
            Mode.BUSY -> throw AiException(AiError.Busy)
            Mode.INVALID_JSON -> CommandReply("not json", "fake")
            Mode.NO_ACTIONS -> CommandReply(
                """{"actions":[],"reply":"I can change BPM, delay, reverb, compressor and EQ. Try: reverb off"}""", "fake"
            )
            Mode.FAIL_ONCE -> if (calls == 1) throw IOException("fake: offline") else success()
        }
    }

    private fun success() = CommandReply(
        """{"actions":[{"fx":"reverb","on":true,"why":"Reverb adds a sense of space."}],"reply":"Turned reverb on."}""",
        "fake"
    )
}

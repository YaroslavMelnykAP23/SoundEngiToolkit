package com.example.soundengitoolkit.ai.data

import kotlinx.coroutines.delay
import java.io.IOException
import com.example.soundengitoolkit.ai.domain.AiError
import com.example.soundengitoolkit.ai.domain.AiException

/** Second implementation of [CommandSource]: no network, no keys. For tests and the debug switch. */
class FakeCommandSource(
    @Volatile var mode: Mode = Mode.SUCCESS,
    @Volatile var delayMs: Long = 0
) : CommandSource {
    enum class Mode { SUCCESS, NETWORK_ERROR, UNAVAILABLE, INVALID_JSON, FAIL_ONCE }

    @Volatile var calls = 0
        private set

    override suspend fun ask(prompt: String): CommandReply {
        calls++
        if (delayMs > 0) delay(delayMs)
        return when (mode) {
            Mode.SUCCESS -> success()
            Mode.NETWORK_ERROR -> throw IOException("fake: offline")
            Mode.UNAVAILABLE -> throw AiException(AiError.AiUnavailable)
            Mode.INVALID_JSON -> CommandReply("not json", "fake")
            Mode.FAIL_ONCE -> if (calls == 1) throw IOException("fake: offline") else success()
        }
    }

    private fun success() = CommandReply("""{"actions":[{"fx":"reverb","on":true}]}""", "fake")
}

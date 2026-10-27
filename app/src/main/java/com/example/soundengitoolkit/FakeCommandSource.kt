package com.example.soundengitoolkit

import java.io.IOException

/** Second implementation of [CommandSource]: no network, no keys. For tests and the debug switch. */
class FakeCommandSource(@Volatile var mode: Mode = Mode.SUCCESS) : CommandSource {
    enum class Mode { SUCCESS, NETWORK_ERROR, UNAVAILABLE, INVALID_JSON }

    @Volatile var calls = 0
        private set

    override suspend fun ask(prompt: String): CommandReply {
        calls++
        return when (mode) {
            Mode.SUCCESS -> CommandReply("""{"actions":[{"fx":"reverb","on":true}]}""", "fake")
            Mode.NETWORK_ERROR -> throw IOException("fake: offline")
            Mode.UNAVAILABLE -> throw AiException(AiError.AiUnavailable)
            Mode.INVALID_JSON -> CommandReply("not json", "fake")
        }
    }
}
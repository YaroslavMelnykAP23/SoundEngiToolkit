package com.example.soundengitoolkit

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeCommandSourceTest {
    private fun run(mode: FakeCommandSource.Mode, prompt: String = "x") =
        runBlocking { RunAiCommandUseCase(FakeCommandSource(mode))(prompt) }

    private fun errorOf(r: Result<AiReply>) = (r.exceptionOrNull() as AiException).error

    @Test fun success_returnsReverbAction() {
        val r = run(FakeCommandSource.Mode.SUCCESS).getOrThrow()
        assertEquals(listOf<AiAction>(AiAction.ReverbFx(true, null, null)), r.actions)
        assertEquals("fake", r.source)
    }

    @Test fun networkError_isNetwork() = assertEquals(AiError.Network, errorOf(run(FakeCommandSource.Mode.NETWORK_ERROR)))
    @Test fun unavailable_isAiUnavailable() = assertEquals(AiError.AiUnavailable, errorOf(run(FakeCommandSource.Mode.UNAVAILABLE)))
    @Test fun invalidJson_isInvalidResponse() = assertEquals(AiError.InvalidResponse, errorOf(run(FakeCommandSource.Mode.INVALID_JSON)))

    @Test fun container_switchesImplementationWithoutTouchingCallers() = runBlocking {
        val c = AppContainer(initialUseFake = true)
        assertEquals("fake", c.runAiCommand("x").getOrThrow().source)
        c.useFake = false
        assertEquals("rules", c.runAiCommand("reverb").getOrThrow().source)
    }
}
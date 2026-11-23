package com.example.soundengitoolkit.ai.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import com.example.soundengitoolkit.ai.domain.AiAction
import com.example.soundengitoolkit.ai.domain.AiError
import com.example.soundengitoolkit.ai.domain.AiException
import com.example.soundengitoolkit.ai.domain.AiPlan
import com.example.soundengitoolkit.ai.domain.FxSnapshot
import com.example.soundengitoolkit.ai.domain.RunAiChatUseCase
import com.example.soundengitoolkit.audio.Engine
import com.example.soundengitoolkit.di.AppContainer

class FakeCommandSourceTest {
    private val state = FxSnapshot.from(Engine())

    private fun run(mode: FakeCommandSource.Mode, prompt: String = "x") =
        runBlocking { RunAiChatUseCase(FakeCommandSource(mode))(prompt, state, emptyList()) }

    private fun errorOf(r: Result<AiPlan>) = (r.exceptionOrNull() as AiException).error

    @Test fun success_returnsReverbAction() {
        val r = run(FakeCommandSource.Mode.SUCCESS).getOrThrow()
        assertEquals(AiAction.ReverbFx(true, null, null), r.actions[0].action)
        assertEquals("fake", r.source)
    }

    @Test fun networkError_isNetwork() = assertEquals(AiError.Network, errorOf(run(FakeCommandSource.Mode.NETWORK_ERROR)))
    @Test fun unavailable_isAiUnavailable() = assertEquals(AiError.AiUnavailable, errorOf(run(FakeCommandSource.Mode.UNAVAILABLE)))
    @Test fun busy_isBusy() = assertEquals(AiError.Busy, errorOf(run(FakeCommandSource.Mode.BUSY)))
    @Test fun noActions_isSuccessWithEmptyList() = assertEquals(0, run(FakeCommandSource.Mode.NO_ACTIONS).getOrThrow().actions.size)
    @Test fun invalidJson_isInvalidResponse() = assertEquals(AiError.InvalidResponse, errorOf(run(FakeCommandSource.Mode.INVALID_JSON)))

    @Test fun container_switchesImplementationWithoutTouchingCallers() = runBlocking {
        val c = AppContainer(initialUseFake = true)
        assertEquals("fake", c.runAiChat("x", state, emptyList()).getOrThrow().source)
        c.useFake = false
        assertEquals("rules", c.runAiChat("reverb", state, emptyList()).getOrThrow().source)
    }
}

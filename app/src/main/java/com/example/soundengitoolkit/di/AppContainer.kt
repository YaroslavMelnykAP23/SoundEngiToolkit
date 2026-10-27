package com.example.soundengitoolkit.di

import com.example.soundengitoolkit.ai.data.CommandReply
import com.example.soundengitoolkit.ai.data.CommandSource
import com.example.soundengitoolkit.ai.data.FakeCommandSource
import com.example.soundengitoolkit.ai.data.FirebaseSource
import com.example.soundengitoolkit.ai.data.HybridSource
import com.example.soundengitoolkit.ai.data.RulesSource
import com.example.soundengitoolkit.ai.domain.Ai
import com.example.soundengitoolkit.ai.domain.RunAiCommandUseCase

/** The only place where concrete implementations are chosen and wired. */
class AppContainer(initialUseFake: Boolean = false) {
    val fakeSource = FakeCommandSource()

    private val realSource: CommandSource = HybridSource(RulesSource(), FirebaseSource(Ai.SYSTEM))

    /** Debug switch: real <-> fake changes only this class. */
    @Volatile var useFake: Boolean = initialUseFake

    /** Delegates to the currently selected implementation; callers never know which one it is. */
    val commandSource: CommandSource = object : CommandSource {
        override suspend fun ask(prompt: String): CommandReply =
            (if (this@AppContainer.useFake) fakeSource else realSource).ask(prompt)
    }

    val runAiCommand = RunAiCommandUseCase(commandSource)
}
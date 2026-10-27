package com.example.soundengitoolkit

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
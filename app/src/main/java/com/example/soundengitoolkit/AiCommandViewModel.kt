package com.example.soundengitoolkit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AiCommandViewModel(
    private val runAiCommand: RunAiCommandUseCase,
    private val logHistory: suspend (prompt: String, json: String, log: String, source: String, ok: Boolean) -> Unit =
        { _, _, _, _, _ -> }
) : ViewModel() {
    private val _state = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val state: StateFlow<AiUiState> = _state.asStateFlow()

    /** The prompt of the last failed run; kept here, so retry does not depend on the text field. */
    private var lastFailedPrompt: String? = null

    /** [apply] turns the parsed actions into effect changes and returns the log. Called on the main thread. */
    fun run(prompt: String, apply: (List<AiAction>) -> String) {
        if (_state.value is AiUiState.Loading) return          // ignore repeated taps
        _state.value = AiUiState.Loading
        viewModelScope.launch {
            val result = runAiCommand(prompt)
            val reply = result.getOrNull()
            if (reply == null) {
                val error = (result.exceptionOrNull() as? AiException)?.error ?: AiError.Unknown
                fail(error, prompt, "", "error")
                return@launch
            }
            val log = try {
                apply(reply.actions)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(AiError.Unknown, prompt, reply.json, reply.source)
                return@launch
            }
            lastFailedPrompt = null
            finish(
                AiUiState.Success(log.ifBlank { "No actions recognized" }, reply.source),
                prompt, reply.json, log, reply.source, log.isNotBlank()
            )
        }
    }

    /** Runs the last failed prompt again. Does nothing if there is no failure to retry. */
    fun retry(apply: (List<AiAction>) -> String) {
        val prompt = lastFailedPrompt ?: return
        run(prompt, apply)
    }

    private suspend fun fail(error: AiError, prompt: String, json: String, source: String) {
        val state = AiUiState.Error(error)
        lastFailedPrompt = if (state.retryable) prompt else null
        finish(state, prompt, json, "Error: $error", source, false)
    }

    private suspend fun finish(
        state: AiUiState, prompt: String, json: String, log: String, source: String, ok: Boolean
    ) {
        _state.value = state                                    // UI first, history is best-effort
        try {
            logHistory(prompt, json, log, source, ok)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // history failure must not break the scenario
        }
    }
}

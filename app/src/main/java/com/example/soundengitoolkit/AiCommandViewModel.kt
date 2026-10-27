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

    /** [apply] turns the parsed actions into effect changes and returns the log. Called on the main thread. */
    fun run(prompt: String, apply: (List<AiAction>) -> String) {
        if (_state.value is AiUiState.Loading) return          // ignore repeated taps
        _state.value = AiUiState.Loading
        viewModelScope.launch {
            val result = runAiCommand(prompt)
            val reply = result.getOrNull()
            if (reply == null) {
                val error = (result.exceptionOrNull() as? AiException)?.error ?: AiError.Unknown
                finish(AiUiState.Error(error), prompt, "", "Error: $error", "error", false)
                return@launch
            }
            val log = try {
                apply(reply.actions)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                finish(AiUiState.Error(AiError.Unknown), prompt, reply.json, "Error: ${AiError.Unknown}", reply.source, false)
                return@launch
            }
            finish(
                AiUiState.Success(log.ifBlank { "No actions recognized" }, reply.source),
                prompt, reply.json, log, reply.source, log.isNotBlank()
            )
        }
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
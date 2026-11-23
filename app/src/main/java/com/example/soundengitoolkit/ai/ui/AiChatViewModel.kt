package com.example.soundengitoolkit.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.example.soundengitoolkit.ai.domain.AiAction
import com.example.soundengitoolkit.ai.domain.AiError
import com.example.soundengitoolkit.ai.domain.AiException
import com.example.soundengitoolkit.ai.domain.ChatAnswer
import com.example.soundengitoolkit.ai.domain.ChatTurn
import com.example.soundengitoolkit.ai.domain.ExplanationBuilder
import com.example.soundengitoolkit.ai.domain.FxSnapshot
import com.example.soundengitoolkit.ai.domain.RunAiChatUseCase

/**
 * AI chat. Holds messages and the short history sent back to the model.
 * Does not hold the Engine (it lives in the Activity, L-13): [readState] and [apply] are passed with each call.
 */
class AiChatViewModel(
    private val runAiChat: RunAiChatUseCase,
    private val logHistory: suspend (prompt: String, json: String, log: String, source: String, ok: Boolean) -> Unit =
        { _, _, _, _, _ -> }
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private val turns = mutableListOf<ChatTurn>()
    private var lastFailedPrompt: String? = null
    private var nextId = 0L

    /** Sends a new request. Ignored while a request is running (no parallel requests). */
    fun send(prompt: String, readState: () -> FxSnapshot, apply: (List<AiAction>) -> Unit) {
        if (_state.value.loading) return
        add(ChatMessage.User(nextId++, prompt.trim()))
        run(prompt, readState, apply)
    }

    /** Repeats the last failed request: the failure bubble is replaced, the user bubble is not duplicated. */
    fun retry(readState: () -> FxSnapshot, apply: (List<AiAction>) -> Unit) {
        if (!_state.value.canRetry) return
        val prompt = lastFailedPrompt ?: return
        _state.update { it.copy(messages = it.messages.dropLast(1)) }
        run(prompt, readState, apply)
    }

    private fun run(prompt: String, readState: () -> FxSnapshot, apply: (List<AiAction>) -> Unit) {
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val result = runAiChat(prompt, readState(), turns.toList())
            val plan = result.getOrNull()
            if (plan == null) {
                val error = (result.exceptionOrNull() as? AiException)?.error ?: AiError.Unknown
                fail(prompt, error, "")
                return@launch
            }
            val answer = try {
                val before = readState()                     // fresh: the user may have moved a switch while waiting
                apply(plan.actions.map { it.action })
                ExplanationBuilder.build(plan.actions, plan.reply, plan.warnings, before, readState())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(prompt, AiError.Unknown, plan.json)
                return@launch
            }
            lastFailedPrompt = null
            turns += ChatTurn(prompt.trim(), ExplanationBuilder.summary(answer))
            add(ChatMessage.Answer(nextId++, answer, plan.source))
            _state.update { it.copy(loading = false) }
            log(prompt, plan.json, ExplanationBuilder.text(answer), "chat:${plan.source}", answer is ChatAnswer.Changed)
        }
    }

    private suspend fun fail(prompt: String, error: AiError, json: String) {
        val retryable = error != AiError.InvalidInput
        lastFailedPrompt = if (retryable) prompt else null
        add(ChatMessage.Failure(nextId++, error, error.chatMessage(), retryable))
        _state.update { it.copy(loading = false) }                 // never an endless "thinking"
        log(prompt, json, "Error: $error", "chat:error", false)
    }

    private fun add(m: ChatMessage) = _state.update { it.copy(messages = it.messages + m) }

    private suspend fun log(prompt: String, json: String, text: String, source: String, ok: Boolean) {
        try {
            logHistory(prompt, json, text, source, ok)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // history is best-effort: a DB failure must not break the chat
        }
    }
}

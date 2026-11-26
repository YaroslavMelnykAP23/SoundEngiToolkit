package com.example.soundengitoolkit.ai.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.soundengitoolkit.ai.domain.AiRequestBuilder
import com.example.soundengitoolkit.ai.domain.ChatAnswer

private val UserBubble = Color(0xFF3A2F5C)
private val AiBubble = Color(0xFF1E1E24)
private val Muted = Color(0xFFB0B0B8)
private val Warn = Color(0xFFFFCC80)
private val ErrorText = Color(0xFFFF8A80)

/**
 * The "AI" tab: messages, AI history, input, Send, Retry. Shows the state only; the scenario lives in [AiChatViewModel].
 * [history] is a slot for the saved AI history (data layer); tapping an entry puts its prompt into the input.
 */
@Composable
fun AiChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    history: @Composable (onPick: (String) -> Unit) -> Unit = {}
) {
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(state.messages.size, state.loading) {
        val last = state.messages.size + (if (state.loading) 1 else 0) - 1
        if (last >= 0) list.animateScrollToItem(last)
    }

    Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.messages.isEmpty()) item { EmptyHint { text = it } }
            items(state.messages, key = { it.id }) { m ->
                when (m) {
                    is ChatMessage.User -> Bubble(m.text, mine = true)
                    is ChatMessage.Answer -> AnswerBubble(m.answer, m.source)
                    is ChatMessage.Failure -> FailureBubble(m, showRetry = state.canRetry && m == state.messages.last(), onRetry)
                }
            }
            if (state.loading) item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text("AI is thinking…", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Box(Modifier.fillMaxWidth().heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
            history { text = it }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                text, { text = it.take(AiRequestBuilder.MAX_PROMPT) }, Modifier.weight(1f),   // W-2: never > 500
                enabled = !state.loading, placeholder = { Text("Ask AI, e.g. make it warmer") }, maxLines = 3,
                supportingText = if (text.length > AiRequestBuilder.MAX_PROMPT - 100) {
                    { Text("${text.length}/${AiRequestBuilder.MAX_PROMPT}") }
                } else null
            )
            Button(
                enabled = text.isNotBlank() && !state.loading,
                onClick = { onSend(text); text = "" }
            ) { Text("Send") }
        }
    }
}

@Composable
private fun EmptyHint(onPick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Describe the sound you want. I change BPM, delay, reverb, compressor and EQ and explain what I did and why.",
            color = Muted, style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("make it brighter", "reverb off", "more space").forEach { s ->
                SuggestionChip(onClick = { onPick(s) }, label = { Text(s) })
            }
        }
    }
}

@Composable
private fun Bubble(text: String, mine: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(text, Modifier.widthIn(max = 300.dp)
            .background(if (mine) UserBubble else AiBubble, RoundedCornerShape(12.dp)).padding(10.dp))
    }
}

@Composable
private fun AnswerBubble(a: ChatAnswer, source: String) {
    Column(Modifier.fillMaxWidth().background(AiBubble, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        when (a) {
            is ChatAnswer.Changed -> {
                a.summary?.let { Text(it, fontWeight = FontWeight.SemiBold) }
                Text("Changed", color = Muted, style = MaterialTheme.typography.labelMedium)
                a.changes.forEach { Text("• ${it.param}: ${it.from} → ${it.to}") }
                Text("Why", color = Muted, style = MaterialTheme.typography.labelMedium)
                a.why.values.forEach { Text("• $it") }
                a.warnings.forEach { Text("⚠ $it", color = Warn, style = MaterialTheme.typography.bodySmall) }
            }
            is ChatAnswer.NothingChanged -> {
                Text(a.message)
                a.warnings.forEach { Text("⚠ $it", color = Warn, style = MaterialTheme.typography.bodySmall) }
            }
        }
        Text(source, color = Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun FailureBubble(m: ChatMessage.Failure, showRetry: Boolean, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(AiBubble, RoundedCornerShape(12.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(m.message, color = ErrorText)
        if (showRetry) OutlinedButton(onClick = onRetry) { Text("Retry") }
    }
}

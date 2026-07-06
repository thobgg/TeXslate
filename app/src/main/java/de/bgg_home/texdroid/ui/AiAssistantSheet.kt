package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.ai.AiClient
import de.bgg_home.texdroid.ai.AiMessage
import de.bgg_home.texdroid.ai.AiPrompt
import de.bgg_home.texdroid.ai.AiResult
import de.bgg_home.texdroid.ai.AiSettings
import de.bgg_home.texdroid.ai.ContextScope
import kotlinx.coroutines.launch

private enum class Stage { INPUT, LOADING, CHAT, ERROR }

/** Eine abgeschlossene Runde: angezeigte Frage, tatsächlich gesendeter Text, Antwort. */
private data class Turn(val displayQuestion: String, val apiUserContent: String, val answer: String)

/** Wie viele der letzten Runden als Gesprächskontext mitgeschickt werden. */
private const val CONTEXT_ROUNDS = 3

private val CODE_FENCE = Regex("```(?:latex|tex)?[ \\t]*\\r?\\n(.*?)```", RegexOption.DOT_MATCHES_ALL)

/**
 * Bereitet die KI-Antwort fürs Einfügen ins Dokument auf: Steckt der LaTeX-Code
 * in Markdown-Zäunen (```` ```latex … ``` ````), wird nur der Code-Inhalt
 * genommen (mehrere Blöcke aneinandergehängt). Ohne Zäune bleibt die Antwort wie
 * sie ist.
 */
private fun latexForInsert(answer: String): String {
    val blocks = CODE_FENCE.findAll(answer).map { it.groupValues[1].trim('\n', '\r') }.toList()
    return if (blocks.isNotEmpty()) blocks.joinToString("\n\n") else answer.trim()
}

/**
 * KI-Assistent (QW A2/A4/A5). Frage stellen, Kontext-Umfang wählen, **vor jedem
 * Aufruf** den zu sendenden Text im Vorschau-Dialog bestätigen, echter Roundtrip.
 * Rückfragen bleiben im Gespräch: die letzten [CONTEXT_ROUNDS] Runden werden
 * mitgeschickt, sodass die KI den Zusammenhang kennt.
 *
 * Verlässt das Gerät nur nach ausdrücklicher Bestätigung im Vorschau-Dialog.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiAssistantSheet(
    settings: AiSettings,
    selection: String,
    document: String,
    onInsert: (String) -> Unit,
    onInsertBody: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    initialQuestion: String = "",
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var stage by remember { mutableStateOf(Stage.INPUT) }
    var question by remember { mutableStateOf(initialQuestion) }
    var turns by remember { mutableStateOf(listOf<Turn>()) }
    var contextScope by remember {
        mutableStateOf(
            when {
                selection.isNotBlank() -> ContextScope.SELECTION
                document.isNotBlank() -> ContextScope.DOCUMENT
                else -> ContextScope.NONE
            },
        )
    }
    var showPreview by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    val ready = settings.enabled && settings.activeKey.isNotBlank()
    val isFollowUp = turns.isNotEmpty()

    // Text der aktuellen Frage, so wie er real gesendet wird: erste Runde mit
    // Editor-Kontext, Rückfragen ohne (der Kontext steckt schon im Verlauf).
    fun currentApiContent(): String =
        if (isFollowUp) question else AiPrompt.build(question, contextScope, selection, document)

    fun send() {
        val apiContent = currentApiContent()
        val display = question
        showPreview = false
        stage = Stage.LOADING
        scope.launch {
            val history = turns.takeLast(CONTEXT_ROUNDS).flatMap {
                listOf(AiMessage("user", it.apiUserContent), AiMessage("assistant", it.answer))
            }
            val messages = history + AiMessage("user", apiContent)
            when (val r = AiClient.complete(
                provider = settings.provider,
                model = settings.activeModel,
                apiKey = settings.activeKey,
                systemPrompt = AiPrompt.SYSTEM,
                messages = messages,
            )) {
                is AiResult.Success -> {
                    turns = turns + Turn(display, apiContent, r.text)
                    question = ""
                    stage = Stage.CHAT
                }
                is AiResult.Failure -> { errorMsg = r.message; stage = Stage.ERROR }
            }
        }
    }

    KeyboardAwareDialog(onDismiss = onDismiss) {
        Text(
            "KI-Assistent (Beta)",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        if (!ready) {
            Text(
                if (!settings.enabled) {
                    "Der KI-Assistent ist noch nicht aktiviert."
                } else {
                    "Für ${settings.provider.displayName} ist noch kein API-Key hinterlegt."
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
            Button(
                onClick = { onDismiss(); onOpenSettings() },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Einstellungen öffnen") }
            return@KeyboardAwareDialog
        }

        Text(
            "${settings.provider.displayName} · ${settings.activeModel}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Bisheriger Gesprächsverlauf (falls vorhanden).
        turns.forEach { turn ->
            Conversation(turn)
        }

        when (stage) {
            Stage.INPUT -> InputStage(
                question = question,
                onQuestionChange = { question = it },
                contextScope = contextScope,
                onScopeChange = { contextScope = it },
                hasSelection = selection.isNotBlank(),
                hasDocument = document.isNotBlank(),
                onAsk = { showPreview = true },
            )

            Stage.LOADING -> Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 24.dp),
            ) {
                CircularProgressIndicator()
                Text("Frage wird gesendet …", modifier = Modifier.padding(start = 16.dp))
            }

            Stage.CHAT -> ChatStage(
                followUp = question,
                onFollowUpChange = { question = it },
                hasSelection = selection.isNotBlank(),
                canInsertBody = document.contains("\\end{document}"),
                onAsk = { showPreview = true },
                onInsert = { onInsert(latexForInsert(turns.last().answer)); onDismiss() },
                onInsertBody = { onInsertBody(latexForInsert(turns.last().answer)); onDismiss() },
                onCopy = { clipboard.setText(AnnotatedString(turns.last().answer)) },
                onNewConversation = { turns = emptyList(); question = ""; stage = Stage.INPUT },
            )

            Stage.ERROR -> Column(Modifier.padding(top = 12.dp)) {
                Text(
                    errorMsg,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 12.dp),
                ) {
                    Button(onClick = { stage = if (isFollowUp) Stage.CHAT else Stage.INPUT }) {
                        Text("Zurück")
                    }
                    TextButton(onClick = { showPreview = true }) { Text("Erneut senden") }
                }
            }
        }
    }

    // Verpflichtende Vorschau: zeigt exakt, was das Gerät verlässt.
    if (showPreview) {
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("Das wird gesendet") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "An ${settings.provider.displayName} (${settings.activeModel}). " +
                            "Es können Kosten anfallen." +
                            if (isFollowUp) " Die letzten Runden werden als Kontext mitgeschickt." else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    ) {
                        Text(
                            currentApiContent(),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .heightIn(max = 320.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(12.dp),
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { send() }, enabled = question.isNotBlank()) { Text("Senden") }
            },
            dismissButton = { TextButton(onClick = { showPreview = false }) { Text("Abbrechen") } },
        )
    }
}

/** Eine Runde im Verlauf: Frage (fett) + Antwort in einer Box. */
@Composable
private fun Conversation(turn: Turn) {
    Text(
        turn.displayQuestion,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
    ) {
        Text(
            turn.answer.ifBlank { "(leere Antwort)" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun InputStage(
    question: String,
    onQuestionChange: (String) -> Unit,
    contextScope: ContextScope,
    onScopeChange: (ContextScope) -> Unit,
    hasSelection: Boolean,
    hasDocument: Boolean,
    onAsk: () -> Unit,
) {
    OutlinedTextField(
        value = question,
        onValueChange = onQuestionChange,
        label = { Text("Frage an die KI") },
        placeholder = { Text("z. B. „Erzeuge eine 3×3-Tabelle mit Kopfzeile\"") },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )

    Text(
        "Kontext",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val scopes = listOf(
            ContextScope.NONE to true,
            ContextScope.SELECTION to hasSelection,
            ContextScope.DOCUMENT to hasDocument,
        )
        scopes.forEach { (s, enabled) ->
            FilterChip(
                selected = contextScope == s,
                enabled = enabled,
                onClick = { onScopeChange(s) },
                label = { Text(s.label) },
            )
        }
    }

    Button(
        onClick = onAsk,
        enabled = question.isNotBlank(),
        modifier = Modifier.padding(top = 16.dp),
    ) { Text("Vorschau & senden") }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatStage(
    followUp: String,
    onFollowUpChange: (String) -> Unit,
    hasSelection: Boolean,
    canInsertBody: Boolean,
    onAsk: () -> Unit,
    onInsert: () -> Unit,
    onInsertBody: () -> Unit,
    onCopy: () -> Unit,
    onNewConversation: () -> Unit,
) {
    HorizontalDivider(Modifier.padding(top = 12.dp))
    OutlinedTextField(
        value = followUp,
        onValueChange = onFollowUpChange,
        label = { Text("Nachfragen …") },
        placeholder = { Text("z. B. „Und wie mache ich die Kopfzeile fett?\"") },
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    )
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Button(onClick = onAsk, enabled = followUp.isNotBlank()) { Text("Nachfragen") }
        // Nur reinen Code-Block einfügen (Markdown-Zäune werden entfernt).
        if (canInsertBody && !hasSelection) {
            TextButton(onClick = onInsertBody) { Text("Am Dokumentende") }
        }
        TextButton(onClick = onInsert) { Text(if (hasSelection) "Ersetzen" else "Am Cursor") }
        TextButton(onClick = onCopy) { Text("Kopieren") }
        TextButton(onClick = onNewConversation) { Text("Neue Frage") }
    }
}

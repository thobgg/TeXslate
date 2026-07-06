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
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.ai.AiClient
import de.bgg_home.texdroid.ai.AiPrompt
import de.bgg_home.texdroid.ai.AiResult
import de.bgg_home.texdroid.ai.AiSettings
import de.bgg_home.texdroid.ai.ContextScope
import kotlinx.coroutines.launch

private enum class Stage { INPUT, LOADING, RESULT, ERROR }

/**
 * KI-Assistent (QW A2). Frage eingeben, Kontext-Umfang wählen, **vor jedem
 * Aufruf** den exakt zu sendenden Text im Vorschau-Dialog bestätigen, dann ein
 * echter API-Roundtrip. Ergebnis lässt sich einfügen oder kopieren.
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
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var stage by remember { mutableStateOf(Stage.INPUT) }
    var question by remember { mutableStateOf("") }
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
    var answer by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    val ready = settings.enabled && settings.activeKey.isNotBlank()

    fun send() {
        val finalPrompt = AiPrompt.build(question, contextScope, selection, document)
        showPreview = false
        stage = Stage.LOADING
        scope.launch {
            when (val r = AiClient.complete(
                provider = settings.provider,
                model = settings.activeModel,
                apiKey = settings.activeKey,
                systemPrompt = AiPrompt.SYSTEM,
                userPrompt = finalPrompt,
            )) {
                is AiResult.Success -> { answer = r.text; stage = Stage.RESULT }
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

                Stage.RESULT -> ResultStage(
                    answer = answer,
                    onInsert = { onInsert(answer); onDismiss() },
                    onCopy = { clipboard.setText(AnnotatedString(answer)) },
                    onNewQuestion = { answer = ""; stage = Stage.INPUT },
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
                        Button(onClick = { stage = Stage.INPUT }) { Text("Zurück") }
                        TextButton(onClick = { showPreview = true }) { Text("Erneut senden") }
                    }
                }
            }
    }

    // Verpflichtende Vorschau: zeigt exakt, was das Gerät verlässt.
    if (showPreview) {
        val previewText = AiPrompt.build(question, contextScope, selection, document)
        AlertDialog(
            onDismissRequest = { showPreview = false },
            title = { Text("Das wird gesendet") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "An ${settings.provider.displayName} (${settings.activeModel}). " +
                            "Es können Kosten anfallen.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    ) {
                        Text(
                            previewText,
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
private fun ResultStage(
    answer: String,
    onInsert: () -> Unit,
    onCopy: () -> Unit,
    onNewQuestion: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
    ) {
        Text(
            answer.ifBlank { "(leere Antwort)" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .heightIn(max = 360.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        )
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Button(onClick = onInsert) { Text("Einfügen") }
        TextButton(onClick = onCopy) { Text("Kopieren") }
        TextButton(onClick = onNewQuestion) { Text("Neue Frage") }
    }
}

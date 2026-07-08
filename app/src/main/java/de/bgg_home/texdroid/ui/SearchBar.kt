package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import de.bgg_home.texdroid.R

/**
 * Suchen-&-Ersetzen-Leiste (Editor-Komfort). Bindet nur die Bedienung an – die
 * eigentliche Suche macht die Engine von sora-editor (siehe
 * [de.bgg_home.texdroid.editor.runSearch] & Co.). Erscheint über dem Editor und
 * fokussiert beim Öffnen automatisch das Suchfeld.
 *
 * @param matchIndex 0-basierter Index des aktuellen Treffers (-1 = keiner).
 * @param matchCount Anzahl der Treffer.
 * @param invalidRegex true, wenn [regex] aktiv und das Muster ungültig ist.
 */
@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    replacement: String,
    onReplacementChange: (String) -> Unit,
    caseInsensitive: Boolean,
    onCaseInsensitiveChange: (Boolean) -> Unit,
    regex: Boolean,
    onRegexChange: (Boolean) -> Unit,
    matchIndex: Int,
    matchCount: Int,
    invalidRegex: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onReplace: () -> Unit,
    onReplaceAll: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            // Zeile 1: Suchfeld + Treffer + Navigation + Optionen + Schließen.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    isError = invalidRegex,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onNext() }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                )
                Text(
                    text = matchLabel(
                        query, matchIndex, matchCount, invalidRegex,
                        stringResource(R.string.search_invalid_pattern),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (invalidRegex) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.widthIn(min = 44.dp).padding(horizontal = 2.dp),
                )
                IconButton(onClick = onPrev, enabled = matchCount > 0) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.search_prev_match),
                    )
                }
                IconButton(onClick = onNext, enabled = matchCount > 0) {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.search_next_match),
                    )
                }
                FilterChip(
                    selected = !caseInsensitive,
                    onClick = { onCaseInsensitiveChange(!caseInsensitive) },
                    label = { Text("Aa") },
                )
                FilterChip(
                    selected = regex,
                    onClick = { onRegexChange(!regex) },
                    label = { Text(".*") },
                )
                IconButton(onClick = { keyboard?.hide(); onClose() }) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.search_close))
                }
            }
            // Zeile 2: Ersetzen-Feld + Aktionen.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                OutlinedTextField(
                    value = replacement,
                    onValueChange = onReplacementChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.search_replace_hint)) },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onReplace, enabled = matchCount > 0) {
                    Text(stringResource(R.string.search_replace))
                }
                TextButton(onClick = onReplaceAll, enabled = matchCount > 0) {
                    Text(stringResource(R.string.search_replace_all))
                }
            }
        }
    }
}

private fun matchLabel(
    query: String,
    index: Int,
    count: Int,
    invalidRegex: Boolean,
    invalidLabel: String,
): String = when {
    invalidRegex -> invalidLabel
    query.isEmpty() -> ""
    count == 0 -> "0"
    else -> "${index + 1}/$count"
}

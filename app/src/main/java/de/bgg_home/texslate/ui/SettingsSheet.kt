// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bgg_home.texslate.R
import de.bgg_home.texslate.editor.EditorTextSize

/**
 * Allgemeine Einstellungen (Issue #10): Editor-Schriftgröße, Kürzelleiste,
 * Fokusmodus. Die KI-Einstellungen haben weiter ihren eigenen Dialog, erreichbar
 * über den Knopf unten.
 *
 * Alle Werte wirken sofort und werden vom Aufrufer gespeichert – kein
 * „Übernehmen", damit man die Schriftgröße hinter dem Dialog direkt sieht.
 */
@Composable
fun SettingsSheet(
    textSizeSp: Float,
    onTextSizeChange: (Float) -> Unit,
    showShortcutBar: Boolean,
    onShowShortcutBarChange: (Boolean) -> Unit,
    focusMode: Boolean,
    onFocusModeChange: (Boolean) -> Unit,
    onOpenAiSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    KeyboardAwareDialog(onDismiss = onDismiss) {
        Text(
            stringResource(R.string.menu_settings),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )

        Label(stringResource(R.string.settings_section_editor))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.settings_font_size, formatSp(textSizeSp)))
            TextButton(
                onClick = { onTextSizeChange(EditorTextSize.DEFAULT_SP) },
                enabled = textSizeSp != EditorTextSize.DEFAULT_SP,
            ) { Text(stringResource(R.string.settings_font_size_reset)) }
        }
        Slider(
            value = textSizeSp,
            onValueChange = { onTextSizeChange(EditorTextSize.normalize(it)) },
            valueRange = EditorTextSize.MIN_SP..EditorTextSize.MAX_SP,
            // Ganze sp: (40 − 8) Schritte = 31 Zwischenpunkte.
            steps = (EditorTextSize.MAX_SP - EditorTextSize.MIN_SP).toInt() - 1,
        )
        Hint(stringResource(R.string.settings_font_size_hint))

        SwitchRow(
            text = stringResource(R.string.settings_shortcut_bar),
            checked = showShortcutBar,
            onCheckedChange = onShowShortcutBarChange,
        )
        SwitchRow(
            text = stringResource(R.string.settings_focus_mode),
            checked = focusMode,
            onCheckedChange = onFocusModeChange,
        )
        Hint(stringResource(R.string.settings_focus_mode_hint))

        Label(stringResource(R.string.ai_assistant))
        OutlinedButton(onClick = onOpenAiSettings) {
            Text(stringResource(R.string.settings_open_ai))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        }
    }
}

/** „18" statt „18.0", aber „14.5" bleibt. */
private fun formatSp(sp: Float): String =
    if (sp % 1f == 0f) sp.toInt().toString() else sp.toString()

@Composable
private fun SwitchRow(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

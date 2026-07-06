package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.ai.AI_MODELS
import de.bgg_home.texdroid.ai.AiSettings

/**
 * KI-Einstellungen (QW A1): Opt-in-Schalter, API-Key (verschlüsselt gespeichert),
 * Modellwahl. Noch **kein** API-Call — nur Konfiguration. Nichts verlässt das Gerät.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsSheet(settings: AiSettings, onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.enabled) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var model by remember { mutableStateOf(settings.model) }
    var showKey by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "KI-Assistent (Beta)",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = enabled, onCheckedChange = { enabled = it })
                Text("Aktivieren (Opt-in)", modifier = Modifier.padding(start = 8.dp))
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Anthropic API-Key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (showKey) "Key verbergen" else "Key anzeigen",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            Text(
                "Modell",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AI_MODELS.forEach { (label, id) ->
                    FilterChip(selected = model == id, onClick = { model = id }, label = { Text(label) })
                }
            }

            Text(
                "Hinweis: Dein Text wird nur nach ausdrücklicher Bestätigung (Vorschau-Dialog) " +
                    "an die Anthropic-API gesendet. Der Key bleibt verschlüsselt auf dem Gerät. " +
                    "Es können API-Kosten anfallen. Die Kern-App funktioniert vollständig offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )

            Button(
                onClick = {
                    settings.enabled = enabled
                    settings.apiKey = apiKey
                    settings.model = model
                    onDismiss()
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Speichern") }
        }
    }
}

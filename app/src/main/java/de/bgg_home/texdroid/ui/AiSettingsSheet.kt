package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import de.bgg_home.texdroid.ai.AiProvider
import de.bgg_home.texdroid.ai.AiSettings

/**
 * KI-Einstellungen (QW A1+): Opt-in, **Anbieter-Auswahl** (Anthropic · OpenAI ·
 * Gemini), API-Key **pro Anbieter** (verschlüsselt), Modell als Presets +
 * Freitext. Noch **kein** API-Call — reine Konfiguration, nichts verlässt das Gerät.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiSettingsSheet(settings: AiSettings, onDismiss: () -> Unit) {
    var enabled by remember { mutableStateOf(settings.enabled) }
    var provider by remember { mutableStateOf(settings.provider) }
    var key by remember { mutableStateOf(settings.keyFor(settings.provider)) }
    var model by remember { mutableStateOf(settings.modelFor(settings.provider)) }
    var showKey by remember { mutableStateOf(false) }

    // Anbieterwechsel: Eingaben des bisherigen sichern, den neuen laden.
    fun selectProvider(p: AiProvider) {
        if (p == provider) return
        settings.setKeyFor(provider, key)
        settings.setModelFor(provider, model)
        provider = p
        key = settings.keyFor(p)
        model = settings.modelFor(p)
        showKey = false
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
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

            SectionLabel("Anbieter")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AiProvider.entries.forEach { p ->
                    FilterChip(
                        selected = provider == p,
                        onClick = { selectProvider(p) },
                        label = { Text(p.displayName) },
                    )
                }
            }

            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                label = { Text("API-Key (${provider.displayName})") },
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

            SectionLabel("Modell")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                provider.modelPresets.forEach { preset ->
                    FilterChip(
                        selected = model == preset,
                        onClick = { model = preset },
                        label = { Text(preset) },
                    )
                }
            }
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Modell-ID (frei überschreibbar)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            Text(
                "Hinweis: Dein Text wird nur nach ausdrücklicher Bestätigung (Vorschau-Dialog) " +
                    "an den gewählten Anbieter gesendet. Keys bleiben verschlüsselt auf dem Gerät. " +
                    "Es können API-Kosten anfallen. Die Kern-App funktioniert vollständig offline.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )

            Button(
                onClick = {
                    settings.enabled = enabled
                    settings.provider = provider
                    settings.setKeyFor(provider, key)
                    settings.setModelFor(provider, model)
                    onDismiss()
                },
                modifier = Modifier.padding(top = 16.dp),
            ) { Text("Speichern") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

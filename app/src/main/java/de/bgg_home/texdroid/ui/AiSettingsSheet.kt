package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.R
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

    KeyboardAwareDialog(onDismiss = onDismiss) {
            Text(
                stringResource(R.string.ai_settings_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = enabled, onCheckedChange = { enabled = it })
                Text(stringResource(R.string.ai_enable_optin), modifier = Modifier.padding(start = 8.dp))
            }

            SectionLabel(stringResource(R.string.ai_provider))
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
                label = { Text(stringResource(R.string.ai_api_key_label, provider.displayName)) },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showKey = !showKey }) {
                        Icon(
                            if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = stringResource(
                                if (showKey) R.string.ai_key_hide else R.string.ai_key_show,
                            ),
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            SectionLabel(stringResource(R.string.ai_model))
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
                label = { Text(stringResource(R.string.ai_model_id_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )

            Text(
                stringResource(R.string.ai_privacy_note),
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
            ) { Text(stringResource(R.string.save)) }
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

package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.R
import de.bgg_home.texdroid.storage.UserTemplate

/**
 * Kleine, feste Offline-Template-Bibliothek: kuratierte Dokument-*typen*, die der
 * (parametrische) Dokument-Assistent nicht abdeckt. Die `.tex`-Dateien liegen in
 * `assets/templates/`. Auswahl → Inhalt wird in den Editor geladen (neues Dokument).
 *
 * Dazu **eigene** Vorlagen: der Nutzer kann das aktuelle Dokument als Vorlage
 * ablegen (interner Speicher, siehe [de.bgg_home.texdroid.storage.UserTemplateStore]);
 * sie erscheinen hier in einer eigenen Sektion und lassen sich wieder löschen.
 */
data class DocTemplate(val nameRes: Int, val descRes: Int, val asset: String)

val DOC_TEMPLATES = listOf(
    DocTemplate(
        R.string.tmpl_beamer_name,
        R.string.tmpl_beamer_desc,
        "templates/beamer_presentation.tex",
    ),
    DocTemplate(
        R.string.tmpl_thesis_name,
        R.string.tmpl_thesis_desc,
        "templates/academic_thesis.tex",
    ),
    DocTemplate(
        R.string.tmpl_letter_name,
        R.string.tmpl_letter_desc,
        "templates/letter_scrlttr2.tex",
    ),
    DocTemplate(
        R.string.tmpl_exam_name,
        R.string.tmpl_exam_desc,
        "templates/exam_worksheet.tex",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(
    userTemplates: List<UserTemplate>,
    canSaveCurrent: Boolean,
    onPickBuiltin: (DocTemplate) -> Unit,
    onPickUser: (UserTemplate) -> Unit,
    onSaveCurrent: (String) -> Unit,
    onDeleteUser: (UserTemplate) -> Unit,
    onDismiss: () -> Unit,
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<UserTemplate?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                stringResource(R.string.templates_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )

            // Aktuelles Dokument als eigene Vorlage ablegen.
            Surface(
                onClick = { showNameDialog = true },
                enabled = canSaveCurrent,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                tonalElevation = 1.dp,
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 12.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.template_save_current),
                        style = MaterialTheme.typography.titleSmall,
                        color = if (canSaveCurrent) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            if (userTemplates.isNotEmpty()) {
                Text(
                    stringResource(R.string.templates_user_section),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                )
                userTemplates.forEach { template ->
                    Surface(
                        onClick = { onPickUser(template) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        tonalElevation = 1.dp,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(start = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Filled.Description,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                            Text(
                                template.name,
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f).padding(vertical = 12.dp),
                            )
                            IconButton(onClick = { pendingDelete = template }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.template_delete),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Text(
                stringResource(R.string.templates_builtin_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
            )
            DOC_TEMPLATES.forEach { template ->
                Surface(
                    onClick = { onPickBuiltin(template) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    tonalElevation = 1.dp,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Description,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column {
                            Text(stringResource(template.nameRes), style = MaterialTheme.typography.titleSmall)
                            Text(
                                stringResource(template.descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        var nameText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text(stringResource(R.string.template_save_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = nameText,
                    onValueChange = { nameText = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.template_name_label)) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSaveCurrent(nameText)
                        showNameDialog = false
                    },
                    enabled = nameText.isNotBlank(),
                ) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { showNameDialog = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    pendingDelete?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.template_delete_dialog_title)) },
            text = { Text(stringResource(R.string.template_delete_confirm, template.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteUser(template)
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

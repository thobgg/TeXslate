package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Kleine, feste Offline-Template-Bibliothek: kuratierte Dokument-*typen*, die der
 * (parametrische) Dokument-Assistent nicht abdeckt. Die `.tex`-Dateien liegen in
 * `assets/templates/`. Auswahl → Inhalt wird in den Editor geladen (neues Dokument).
 */
data class DocTemplate(val name: String, val description: String, val asset: String)

val DOC_TEMPLATES = listOf(
    DocTemplate(
        "Beamer-Präsentation",
        "Folien mit Titelframe, Gliederung und Beispiel-Frames",
        "templates/beamer_presentation.tex",
    ),
    DocTemplate(
        "Akademische Arbeit",
        "Titelseite, Inhaltsverzeichnis, Kapitel, Literatur (report)",
        "templates/academic_thesis.tex",
    ),
    DocTemplate(
        "Brief (KOMA scrlttr2)",
        "Geschäftsbrief mit Absender, Empfänger und Betreff",
        "templates/letter_scrlttr2.tex",
    ),
    DocTemplate(
        "Klausur / Übungsblatt",
        "Aufgaben mit Punkten und Lösungen (exam-Klasse)",
        "templates/exam_worksheet.tex",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatePickerSheet(onPick: (DocTemplate) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            Text(
                "Vorlage wählen",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            DOC_TEMPLATES.forEach { template ->
                Surface(
                    onClick = { onPick(template) },
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
                            Text(template.name, style = MaterialTheme.typography.titleSmall)
                            Text(
                                template.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

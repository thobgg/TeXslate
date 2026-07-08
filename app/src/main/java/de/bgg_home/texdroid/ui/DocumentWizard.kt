package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.R

/**
 * Dokument-Assistent (Kiles „Wizard → Document"): Dokumentklasse, Schriftgröße,
 * Sprache und Pakete wählen, optional Titelblock → fertige Präambel + Grundgerüst.
 * Ersetzt den Editor-Inhalt (neues Dokument); der Cursor landet im Textkörper.
 */
private val CLASSES = listOf("article", "report", "book")
private val SIZES = listOf("10pt", "11pt", "12pt")
private val LANGS = listOf("—" to null, "Deutsch" to "ngerman", "English" to "english")
private val PACKAGES = listOf("amsmath", "amssymb", "graphicx", "hyperref")

@Composable
fun DocumentWizardDialog(onCreate: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var docClass by remember { mutableStateOf("article") }
    var fontSize by remember { mutableStateOf("11pt") }
    var lang by remember { mutableStateOf<String?>("ngerman") }
    val packages = remember {
        mutableStateMapOf("amsmath" to true, "amssymb" to false, "graphicx" to true, "hyperref" to false)
    }
    var includeTitle by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.doc_wizard_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChipRow(stringResource(R.string.doc_class), CLASSES, docClass) { docClass = it }
                ChipRow(stringResource(R.string.doc_font_size), SIZES, fontSize) { fontSize = it }

                Label(stringResource(R.string.doc_language))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LANGS.forEach { (label, value) ->
                        FilterChip(selected = lang == value, onClick = { lang = value }, label = { Text(label) })
                    }
                }

                Label(stringResource(R.string.doc_packages))
                PACKAGES.forEach { pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = packages[pkg] == true, onCheckedChange = { packages[pkg] = it })
                        Text(pkg)
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Checkbox(checked = includeTitle, onCheckedChange = { includeTitle = it })
                    Text(stringResource(R.string.doc_title_block))
                }
                if (includeTitle) {
                    OutlinedTextField(
                        value = title, onValueChange = { title = it },
                        label = { Text(stringResource(R.string.doc_title)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    OutlinedTextField(
                        value = author, onValueChange = { author = it },
                        label = { Text(stringResource(R.string.doc_author)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val selectedPkgs = PACKAGES.filter { packages[it] == true }
                val (text, line) = generateDocument(
                    docClass, fontSize, lang, selectedPkgs, includeTitle, title, author,
                )
                onCreate(text, line)
                onDismiss()
            }) { Text(stringResource(R.string.doc_create)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
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
private fun ChipRow(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Label(label)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { opt ->
            FilterChip(selected = selected == opt, onClick = { onSelect(opt) }, label = { Text(opt) })
        }
    }
}

/** Baut das Grundgerüst und liefert die (0-basierte) Cursor-Zeile im Textkörper. */
private fun generateDocument(
    docClass: String,
    fontSize: String,
    lang: String?,
    packages: List<String>,
    includeTitle: Boolean,
    title: String,
    author: String,
): Pair<String, Int> {
    val lines = mutableListOf<String>()
    lines.add("\\documentclass[$fontSize,a4paper]{$docClass}")
    packages.forEach { lines.add("\\usepackage{$it}") }
    lang?.let { lines.add("\\usepackage[$it]{babel}") }
    lines.add("")
    if (includeTitle) {
        lines.add("\\title{$title}")
        lines.add("\\author{$author}")
        lines.add("")
    }
    lines.add("\\begin{document}")
    if (includeTitle) lines.add("\\maketitle")
    lines.add("")
    val cursorLine = lines.lastIndex // leere Zeile im Textkörper
    lines.add("\\end{document}")
    return lines.joinToString("\n") to cursorLine
}

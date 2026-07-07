package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Dokumentstruktur (Gliederung) wie in Kile: die Sektionsbefehle des aktuellen
 * Dokuments als antippbare Liste. Ein Tipp springt zur Zeile — ersetzt das
 * mühsame Scrollen in langen Dokumenten.
 */
data class OutlineEntry(val level: Int, val title: String, val line: Int)

private val OUTLINE_LEVELS = mapOf(
    "part" to 0,
    "chapter" to 1,
    "section" to 2,
    "subsection" to 3,
    "subsubsection" to 4,
    "paragraph" to 5,
    "subparagraph" to 6,
    "frametitle" to 3, // Beamer-Folien
)

private val OUTLINE_REGEX =
    Regex("""\\(part|chapter|section|subsection|subsubsection|paragraph|subparagraph|frametitle)\*?\s*(?:\[[^\]]*\])?\s*\{""")

/** Parst die Sektionsbefehle. Zeilennummern sind 1-basiert. */
fun parseOutline(text: String): List<OutlineEntry> {
    val entries = mutableListOf<OutlineEntry>()
    text.split("\n").forEachIndexed { idx, line ->
        val m = OUTLINE_REGEX.find(line) ?: return@forEachIndexed
        val level = OUTLINE_LEVELS[m.groupValues[1]] ?: return@forEachIndexed
        val title = extractBraced(line, m.range.last)?.trim().orEmpty()
        if (title.isNotEmpty()) entries.add(OutlineEntry(level, title, idx + 1))
    }
    return entries
}

// Liest die balancierte Gruppe ab der öffnenden Klammer an [openIndex].
private fun extractBraced(s: String, openIndex: Int): String? {
    if (openIndex >= s.length || s[openIndex] != '{') return null
    var depth = 0
    val sb = StringBuilder()
    for (i in openIndex until s.length) {
        when (val c = s[i]) {
            '{' -> { if (depth > 0) sb.append(c); depth++ }
            '}' -> { depth--; if (depth == 0) return sb.toString(); sb.append(c) }
            else -> sb.append(c)
        }
    }
    return sb.toString() // unbalanciert (Titel über Zeilenende) – nimm, was da ist
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutlineSheet(
    entries: List<OutlineEntry>,
    onJump: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp),
        ) {
            Text(
                "Dokumentstruktur",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (entries.isEmpty()) {
                Text(
                    "Keine Abschnitte gefunden. \\section{…}, \\subsection{…} usw. " +
                        "erscheinen hier zum Anspringen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                Column(
                    Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                ) {
                    entries.forEach { entry ->
                        Surface(
                            onClick = { onJump(entry.line) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            tonalElevation = 1.dp,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Row(
                                Modifier.fillMaxWidth()
                                    .padding(start = (12 + entry.level * 16).dp, end = 12.dp)
                                    .padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    entry.title,
                                    style = if (entry.level <= 2) {
                                        MaterialTheme.typography.titleSmall
                                    } else {
                                        MaterialTheme.typography.bodyMedium
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                                )
                                Text(
                                    "Z. ${entry.line}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

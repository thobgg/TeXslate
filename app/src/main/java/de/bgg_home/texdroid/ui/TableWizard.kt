package de.bgg_home.texdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.R

/**
 * Tabellen-Assistent (Kiles „Wizard → Tabular", tablet-tauglich): Spalten,
 * Zeilen, Ausrichtung und Rahmen wählen → fertiges `tabular`-Gerüst einfügen.
 * So muss man die Syntax auf dem Tablet nicht abtippen.
 */
@Composable
fun TableWizardDialog(onInsert: (String, Int) -> Unit, onDismiss: () -> Unit) {
    var cols by remember { mutableIntStateOf(3) }
    var rows by remember { mutableIntStateOf(3) }
    var align by remember { mutableStateOf('c') }
    var borders by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.table_wizard_title)) },
        text = {
            Column {
                Stepper(stringResource(R.string.table_columns), cols, 1, 12) { cols = it }
                Stepper(stringResource(R.string.table_rows), rows, 1, 30) { rows = it }
                Text(
                    stringResource(R.string.table_alignment),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        'l' to R.string.align_left,
                        'c' to R.string.align_center,
                        'r' to R.string.align_right,
                    ).forEach { (a, labelRes) ->
                        FilterChip(
                            selected = align == a,
                            onClick = { align = a },
                            label = { Text(stringResource(labelRes)) },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Checkbox(checked = borders, onCheckedChange = { borders = it })
                    Text(stringResource(R.string.table_borders))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val (text, caret) = generateTable(cols, rows, align, borders)
                onInsert(text, caret)
                onDismiss()
            }) { Text(stringResource(R.string.insert)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Stepper(label: String, value: Int, min: Int, max: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        IconButton(onClick = { if (value > min) onChange(value - 1) }) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.stepper_decrease))
        }
        Text("$value", modifier = Modifier.widthIn(min = 28.dp), textAlign = TextAlign.Center)
        IconButton(onClick = { if (value < max) onChange(value + 1) }) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.stepper_increase))
        }
    }
}

/** Baut das tabular-Gerüst und liefert den Cursor-Offset (erste Zelle). */
private fun generateTable(cols: Int, rows: Int, align: Char, borders: Boolean): Pair<String, Int> {
    val colSpec = if (borders) "|" + "$align|".repeat(cols) else "$align".repeat(cols)
    val sb = StringBuilder()
    sb.append("\\begin{tabular}{").append(colSpec).append("}\n")
    if (borders) sb.append("\\hline\n")
    val caret = sb.length + 4 // Cursor in die erste (leere) Zelle, nach dem Einzug
    val cellLine = "    " + (1..cols).joinToString(" & ") { "" } + " \\\\\n"
    repeat(rows) {
        sb.append(cellLine)
        if (borders) sb.append("\\hline\n")
    }
    sb.append("\\end{tabular}")
    return sb.toString() to caret
}

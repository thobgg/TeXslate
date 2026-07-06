package de.bgg_home.texdroid.ui

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * „Gehe zu Zeile" (Editor-Komfort). Nimmt eine Zeilennummer (1-basiert) entgegen
 * und springt dorthin. Eingaben werden auf Ziffern beschränkt und gegen
 * [lineCount] geprüft; ungültige Werte deaktivieren „Springen".
 */
@Composable
fun GoToLineDialog(
    lineCount: Int,
    onGo: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val line = text.toIntOrNull()
    val valid = line != null && line in 1..lineCount

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gehe zu Zeile") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { s -> text = s.filter { it.isDigit() }.take(7) },
                singleLine = true,
                label = { Text("Zeile (1–$lineCount)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { if (valid) onGo(line!!) }),
                modifier = Modifier.focusRequester(focusRequester),
            )
        },
        confirmButton = {
            TextButton(onClick = { if (valid) onGo(line!!) }, enabled = valid) {
                Text("Springen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        },
    )
}

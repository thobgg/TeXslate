package de.bgg_home.texdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.ui.theme.TexDroidTheme
import kotlin.concurrent.thread

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TexDroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    RustDemo(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun RustDemo(modifier: Modifier = Modifier) {
    // Kopfzeile: Rust-Brücke (QW 0.2) + eingebettete Engine (QW 0.3).
    val header: String = try {
        val sum = RustBridge.add(2, 3)
        val tectonic = RustBridge.tectonicVersion()
        "2 + 3 = $sum · berechnet in Rust 🦀\n\n$tectonic"
    } catch (t: Throwable) {
        "Rust-Bibliothek nicht geladen:\n${t.message}"
    }

    // QW 0.4-Kern: echten Compile im Hintergrund-Thread laufen lassen (blockiert
    // + Netzwerk → niemals auf dem UI-Thread) und das Ergebnis anzeigen.
    val context = LocalContext.current
    var compileStatus by remember {
        mutableStateOf("⏳ Kompiliere Mini-LaTeX …\n(lädt beim ersten Mal das Tectonic-Bundle)")
    }
    LaunchedEffect(Unit) {
        thread {
            compileStatus = try {
                RustBridge.tectonicCompile(context.cacheDir.absolutePath)
            } catch (t: Throwable) {
                "Compile fehlgeschlagen:\n${t.message}"
            }
        }
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = header,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = compileStatus,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RustDemoPreview() {
    // Hinweis: Die @Preview läuft im Android-Studio-Renderer OHNE die native
    // Lib – hier greifen daher die catch-Zweige. Auf dem Gerät kommt "= 5".
    TexDroidTheme {
        RustDemo()
    }
}

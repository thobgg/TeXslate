package de.bgg_home.texdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.ui.theme.TexDroidTheme

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
    // Ergebnis von der nativen Rust-Seite holen. Falls die .so (noch) fehlt,
    // fangen wir den UnsatisfiedLinkError ab und zeigen ihn lesbar an,
    // statt die App abstürzen zu lassen.
    val line: String = try {
        val sum = RustBridge.add(2, 3)
        val tectonic = RustBridge.tectonicVersion()
        "2 + 3 = $sum · berechnet in Rust 🦀\n\n$tectonic"
    } catch (t: Throwable) {
        "Rust-Bibliothek nicht geladen:\n${t.message}"
    }

    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = line,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RustDemoPreview() {
    // Hinweis: Die @Preview läuft im Android-Studio-Renderer OHNE die native
    // Lib – hier greift daher der catch-Zweig. Auf dem Gerät kommt "= 5".
    TexDroidTheme {
        RustDemo()
    }
}

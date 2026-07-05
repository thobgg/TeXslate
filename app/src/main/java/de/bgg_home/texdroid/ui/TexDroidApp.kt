package de.bgg_home.texdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.compile.CompileError
import de.bgg_home.texdroid.compile.LatexCompiler
import de.bgg_home.texdroid.editor.LatexEditor
import de.bgg_home.texdroid.editor.jumpToErrorLine
import de.bgg_home.texdroid.editor.showErrorDiagnostics
import de.bgg_home.texdroid.pdf.PdfPreview
import de.bgg_home.texdroid.storage.DocumentStore
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

/** Wartezeit nach dem letzten Tastendruck, bevor automatisch kompiliert wird. */
private const val AUTO_COMPILE_DEBOUNCE_MS = 800L

/** Beispiel-Dokument beim ersten Start – zeigt alle gehighlighteten Elemente. */
private val SAMPLE_TEX = """
\documentclass{article}
\usepackage{amsmath}

\title{TexDroid}
\author{Auf dem Tablet geschrieben}

\begin{document}
\maketitle

% Ein Kommentar: alles ab Prozentzeichen ist grau.
Willkommen bei \textbf{TexDroid}! Inline-Mathe: ${'$'}E = mc^2${'$'}.

\begin{equation}
    \int_0^\infty e^{-x^2}\,\mathrm{d}x = \frac{\sqrt{\pi}}{2}
\end{equation}

\begin{itemize}
    \item Editor mit LaTeX-Syntax-Highlighting
    \item Lokaler Compile via Tectonic
    \item PDF-Vorschau nebenan
\end{itemize}

\end{document}
""".trimIndent()

/** Zustand des Compile-Laufs für die UI. */
private enum class Tab { Editor, Preview }

/**
 * Wurzel-Composable der App. Entscheidet anhand der [WindowSizeClass] zwischen
 * Split-View (breite Tablets) und Tab-Umschaltung (Phone/Portrait).
 */
@Composable
fun TexDroidApp(windowSizeClass: WindowSizeClass) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkTheme = isSystemInDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }

    // Referenz auf den konkreten Editor (für das Auslesen des Textes beim Compile).
    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var compiling by remember { mutableStateOf(false) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var errors by remember { mutableStateOf<List<CompileError>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(Tab.Editor) }

    // Auto-Compile (QW 3.1): zählt Editor-Änderungen; ein LaunchedEffect debounced darauf.
    var autoCompile by remember { mutableStateOf(true) }
    var textVersion by remember { mutableIntStateOf(0) }

    // Aktuell geöffnete Datei (SAF): Uri, Anzeigename und ob wir zurückschreiben dürfen.
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var currentName by remember { mutableStateOf<String?>(null) }
    var canWrite by remember { mutableStateOf(false) }

    // SAF: Datei öffnen (ACTION_OPEN_DOCUMENT).
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            canWrite = DocumentStore.takePersistablePermission(context, uri)
            scope.launch {
                val text = DocumentStore.read(context, uri)
                editor?.setText(text)
                currentUri = uri
                currentName = DocumentStore.displayName(context, uri) ?: "dokument.tex"
                snackbarHostState.showSnackbar("Geöffnet: $currentName")
            }
        }
    }

    // SAF: „Speichern unter…" (ACTION_CREATE_DOCUMENT).
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val text = editor?.text?.toString()
        if (uri != null && text != null) {
            canWrite = DocumentStore.takePersistablePermission(context, uri)
            scope.launch {
                DocumentStore.write(context, uri, text)
                currentUri = uri
                currentName = DocumentStore.displayName(context, uri) ?: "dokument.tex"
                snackbarHostState.showSnackbar("Gespeichert: $currentName")
            }
        }
    }

    fun openDocument() {
        // */* – .tex meldet je nach Gerät text/plain, text/x-tex oder octet-stream.
        openLauncher.launch(arrayOf("*/*"))
    }

    fun saveDocument() {
        val text = editor?.text?.toString() ?: return
        val uri = currentUri
        if (uri != null && canWrite) {
            scope.launch {
                DocumentStore.write(context, uri, text)
                snackbarHostState.showSnackbar("Gespeichert: ${currentName ?: "Dokument"}")
            }
        } else {
            // Noch keine (beschreibbare) Datei → „Speichern unter…".
            saveAsLauncher.launch(currentName ?: "dokument.tex")
        }
    }

    fun runCompile() {
        val source = editor?.text?.toString()
        if (source == null || compiling) return
        compiling = true
        scope.launch {
            val result = LatexCompiler.compile(context, source)
            errors = result.errors
            if (result.ok && result.pdfPath.isNotEmpty()) {
                pdfFile = File(result.pdfPath)
                reloadToken++ // Preview neu laden, Scroll-Position bleibt erhalten.
            }
            compiling = false
            snackbarHostState.showSnackbar(result.summary())
        }
    }

    // QW 3.1: Debounce – nach der letzten Änderung kurz warten, dann kompilieren.
    // Da der Effekt bei jedem textVersion-Wechsel neu startet, wird ein noch
    // wartender Auto-Compile bei jedem weiteren Tastendruck verworfen (= entprellt).
    LaunchedEffect(textVersion, autoCompile) {
        if (!autoCompile || textVersion == 0) return@LaunchedEffect
        delay(AUTO_COMPILE_DEBOUNCE_MS)
        while (compiling) delay(150) // läuft gerade ein Compile? kurz warten.
        runCompile()
    }

    // QW 3.2: Fehlerzeilen im Editor markieren, sobald sich die Fehlerliste ändert.
    LaunchedEffect(errors, editor) {
        editor?.showErrorDiagnostics(errors)
    }

    // QW 3.2: Tipp auf einen Fehler → Sprung zur Zeile (auf dem Phone zuerst
    // zum Editor-Tab wechseln, damit man den Cursor sieht).
    val onErrorClick: (CompileError) -> Unit = { err ->
        selectedTab = Tab.Editor
        err.line?.let { editor?.jumpToErrorLine(it) }
    }

    val isWide = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppHeader(
                fileName = currentName,
                compiling = compiling,
                autoCompile = autoCompile,
                onAutoCompileChange = { autoCompile = it },
                onOpen = ::openDocument,
                onSave = ::saveDocument,
                onCompile = ::runCompile,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Box(Modifier.padding(innerPadding).fillMaxSize()) {
            if (isWide) {
                // Tablet-Landscape: echte Split-View (Editor | Preview).
                Row(Modifier.fillMaxSize()) {
                    EditorPane(
                        errors = errors,
                        darkTheme = darkTheme,
                        onEditorCreated = { editor = it },
                        onTextChanged = { textVersion++ },
                        onErrorClick = onErrorClick,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                    VerticalDivider()
                    PreviewPane(
                        pdfFile = pdfFile,
                        reloadToken = reloadToken,
                        compiling = compiling,
                        modifier = Modifier.weight(1f).fillMaxSize(),
                    )
                }
            } else {
                // Phone / schmal: Tab-Umschaltung Editor ↔ Vorschau.
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab.ordinal) {
                        Tab(
                            selected = selectedTab == Tab.Editor,
                            onClick = { selectedTab = Tab.Editor },
                            text = { Text("Editor") },
                        )
                        Tab(
                            selected = selectedTab == Tab.Preview,
                            onClick = { selectedTab = Tab.Preview },
                            text = { Text("Vorschau") },
                        )
                    }
                    when (selectedTab) {
                        Tab.Editor -> EditorPane(
                            errors = errors,
                            darkTheme = darkTheme,
                            onEditorCreated = { editor = it },
                            onTextChanged = { textVersion++ },
                            onErrorClick = onErrorClick,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Tab.Preview -> PreviewPane(
                            pdfFile = pdfFile,
                            reloadToken = reloadToken,
                            compiling = compiling,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(
    fileName: String?,
    compiling: Boolean,
    autoCompile: Boolean,
    onAutoCompileChange: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onCompile: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Edge-to-edge: nicht hinter Statusleiste / Kamera-Notch rutschen.
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Titel + aktueller Dateiname. weight(1f) + Ellipsis: der Titel schrumpft,
            // damit die Buttons rechts IMMER sichtbar bleiben (nie aus dem Bild geschoben).
            Text(
                text = if (fileName != null) "TexDroid — $fileName" else "TexDroid",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Auto-Compile-Schalter (QW 3.1).
                Text("Auto", style = MaterialTheme.typography.labelLarge)
                Switch(checked = autoCompile, onCheckedChange = onAutoCompileChange)
                TextButton(onClick = onOpen, enabled = !compiling) { Text("Öffnen") }
                TextButton(onClick = onSave, enabled = !compiling) { Text("Speichern") }
                CompileButton(compiling = compiling, onCompile = onCompile)
            }
        }
    }
}

@Composable
private fun CompileButton(compiling: Boolean, onCompile: () -> Unit) {
    Button(onClick = onCompile, enabled = !compiling) {
        if (compiling) {
            CircularProgressIndicator(
                modifier = Modifier.padding(end = 8.dp),
                strokeWidth = 2.dp,
            )
            Text("Kompiliere …")
        } else {
            Text("Kompilieren")
        }
    }
}

@Composable
private fun EditorPane(
    errors: List<CompileError>,
    darkTheme: Boolean,
    onEditorCreated: (CodeEditor) -> Unit,
    onTextChanged: () -> Unit,
    onErrorClick: (CompileError) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        LatexEditor(
            initialText = SAMPLE_TEX,
            darkTheme = darkTheme,
            onEditorCreated = onEditorCreated,
            onTextChanged = onTextChanged,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (errors.isNotEmpty()) {
            ErrorPanel(errors, onErrorClick)
        }
    }
}

@Composable
private fun ErrorPanel(errors: List<CompileError>, onErrorClick: (CompileError) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .heightIn(max = 160.dp)
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "Fehler (${errors.size}) – zum Springen antippen",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            errors.forEach { err ->
                val prefix = err.line?.let { "Zeile $it: " } ?: ""
                Text(
                    text = "• $prefix${err.message}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onErrorClick(err) } // QW 3.2: Sprung zur Fehlerzeile
                        .padding(vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PreviewPane(
    pdfFile: File?,
    reloadToken: Int,
    compiling: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            pdfFile != null -> PdfPreview(file = pdfFile, reloadToken = reloadToken)
            compiling -> CircularProgressIndicator()
            else -> Text(
                text = "Noch nicht kompiliert.\nTippe auf „Kompilieren“.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

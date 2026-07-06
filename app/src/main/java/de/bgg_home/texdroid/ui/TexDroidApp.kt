package de.bgg_home.texdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** Minimales Grundgerüst für „Neu". */
private val NEW_DOC_TEMPLATE = """
\documentclass{article}

\begin{document}

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

    // Split-View: Anteil des Editors an der Breite (per Trenner verschiebbar, 0.2–0.8).
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    // LaTeX-Einfüge-Sheet (der „mehr"-Button der Favoriten-Leiste) + Tabellen-Wizard.
    var showInsertSheet by remember { mutableStateOf(false) }
    var showTableWizard by remember { mutableStateOf(false) }
    var showDocumentWizard by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }

    // Build-Komfort: volles Log, laufender Compile-Job (zum Stoppen), Fehler-Cursor.
    var lastLog by remember { mutableStateOf("") }
    var showLog by remember { mutableStateOf(false) }
    var compileJob by remember { mutableStateOf<Job?>(null) }
    var errorIndex by remember { mutableIntStateOf(0) }

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

    // SAF: kompiliertes PDF exportieren (ACTION_CREATE_DOCUMENT).
    val exportPdfLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri ->
        val pdf = pdfFile
        if (uri != null && pdf != null) {
            scope.launch {
                DocumentStore.exportFile(context, pdf, uri)
                snackbarHostState.showSnackbar(
                    "PDF gespeichert: ${DocumentStore.displayName(context, uri) ?: "PDF"}",
                )
            }
        }
    }

    fun openDocument() {
        // */* – .tex meldet je nach Gerät text/plain, text/x-tex oder octet-stream.
        openLauncher.launch(arrayOf("*/*"))
    }

    fun exportPdf() {
        if (pdfFile == null) {
            scope.launch { snackbarHostState.showSnackbar("Erst kompilieren – es gibt noch kein PDF.") }
            return
        }
        val base = currentName?.removeSuffix(".tex") ?: "dokument"
        exportPdfLauncher.launch("$base.pdf")
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

    fun newDocument() {
        editor?.setText(NEW_DOC_TEMPLATE)
        currentUri = null
        currentName = null
        canWrite = false
    }

    fun runCompile() {
        val source = editor?.text?.toString()
        if (source == null || compiling) return
        compiling = true
        compileJob = scope.launch {
            try {
                val result = LatexCompiler.compile(context, source)
                lastLog = result.log.ifBlank { result.engineError }
                errors = result.errors
                errorIndex = 0
                if (result.ok && result.pdfPath.isNotEmpty()) {
                    pdfFile = File(result.pdfPath)
                    reloadToken++ // Preview neu laden, Scroll-Position bleibt erhalten.
                }
                snackbarHostState.showSnackbar(result.summary())
            } finally {
                // Läuft auch bei Abbruch (finally) – erst wenn der native Aufruf
                // wirklich zurückkehrt, damit keine zwei Compiles gleichzeitig laufen.
                compiling = false
            }
        }
    }

    fun stopCompile() {
        // Cancel verwirft das Ergebnis; der native Tectonic-Aufruf lässt sich
        // aber nicht mitten drin abbrechen – er läuft im Hintergrund zu Ende.
        compileJob?.cancel()
        scope.launch { snackbarHostState.showSnackbar("Compile abgebrochen") }
    }

    // Build-Komfort: zwischen Fehlern springen (zyklisch).
    fun jumpToError(index: Int) {
        if (errors.isEmpty()) return
        val i = ((index % errors.size) + errors.size) % errors.size
        errorIndex = i
        selectedTab = Tab.Editor
        errors[i].line?.let { editor?.jumpToErrorLine(it) }
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
                onNew = ::newDocument,
                onOpen = ::openDocument,
                onSave = ::saveDocument,
                onExportPdf = ::exportPdf,
                canExportPdf = pdfFile != null,
                onShowLog = { showLog = true },
                canShowLog = lastLog.isNotBlank(),
                onUndo = { editor?.undo() },
                onRedo = { editor?.redo() },
                onCompile = ::runCompile,
                onStop = ::stopCompile,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // imePadding(): Inhalt über der Software-Tastatur halten (edge-to-edge
        // verkleinert das Fenster sonst nicht → Tastatur überdeckte den Editor).
        Column(Modifier.padding(innerPadding).imePadding().fillMaxSize()) {
            // LaTeX-Favoriten-Leiste – nur zeigen, wenn der Editor sichtbar ist.
            if (isWide || selectedTab == Tab.Editor) {
                LatexFavoritesBar(
                    onInsert = { text, caret ->
                        editor?.insertText(text, caret)
                        editor?.requestFocus()
                    },
                    onOpenMore = { showInsertSheet = true },
                )
                HorizontalDivider()
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (isWide) {
                // Tablet-Landscape: echte Split-View (Editor | Preview) mit
                // verschiebbarem Trenner. splitFraction = Breitenanteil des Editors.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val totalPx = constraints.maxWidth.toFloat()
                    Row(Modifier.fillMaxSize()) {
                        EditorPane(
                            errors = errors,
                            darkTheme = darkTheme,
                            onEditorCreated = { editor = it },
                            onTextChanged = { textVersion++ },
                            onErrorClick = onErrorClick,
                            onPrevError = { jumpToError(errorIndex - 1) },
                            onNextError = { jumpToError(errorIndex + 1) },
                            modifier = Modifier.weight(splitFraction).fillMaxSize(),
                        )
                        SplitHandle(
                            onDragDelta = { deltaPx ->
                                splitFraction = (splitFraction + deltaPx / totalPx).coerceIn(0.2f, 0.8f)
                            },
                        )
                        PreviewPane(
                            pdfFile = pdfFile,
                            reloadToken = reloadToken,
                            compiling = compiling,
                            modifier = Modifier.weight(1f - splitFraction).fillMaxSize(),
                        )
                    }
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
                            onPrevError = { jumpToError(errorIndex - 1) },
                            onNextError = { jumpToError(errorIndex + 1) },
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

    // „Mehr"-Bausteine als kategorisiertes Bottom-Sheet (öffnet der ▦-Button).
    if (showInsertSheet) {
        LatexInsertSheet(
            onInsert = { text, caret ->
                editor?.insertText(text, caret)
                editor?.requestFocus()
            },
            onOpenTableWizard = {
                showInsertSheet = false
                showTableWizard = true
            },
            onOpenDocumentWizard = {
                showInsertSheet = false
                showDocumentWizard = true
            },
            onOpenTemplates = {
                showInsertSheet = false
                showTemplates = true
            },
            onDismiss = { showInsertSheet = false },
        )
    }

    // Offline-Vorlagen-Bibliothek (QW T1): Auswahl lädt die Vorlage in den Editor.
    if (showTemplates) {
        TemplatePickerSheet(
            onPick = { template ->
                showTemplates = false
                scope.launch {
                    val text = withContext(Dispatchers.IO) {
                        context.assets.open(template.asset).bufferedReader().use { it.readText() }
                    }
                    editor?.setText(text)
                    editor?.setSelection(0, 0)
                    editor?.requestFocus()
                    currentUri = null
                    currentName = null
                    canWrite = false
                }
            },
            onDismiss = { showTemplates = false },
        )
    }

    // Tabellen-Assistent (Wizard-Dialog).
    if (showTableWizard) {
        TableWizardDialog(
            onInsert = { text, caret ->
                editor?.insertText(text, caret)
                editor?.requestFocus()
            },
            onDismiss = { showTableWizard = false },
        )
    }

    // Dokument-Assistent (ersetzt den Editor-Inhalt = neues Dokument).
    if (showDocumentWizard) {
        DocumentWizardDialog(
            onCreate = { text, line ->
                editor?.setText(text)
                editor?.setSelection(line, 0)
                editor?.requestFocus()
                currentUri = null
                currentName = null
                canWrite = false
            },
            onDismiss = { showDocumentWizard = false },
        )
    }

    // Volles TeX-Log ansehen (Kebab → „Log ansehen").
    if (showLog) {
        LogSheet(log = lastLog, onDismiss = { showLog = false })
    }
}

@Composable
private fun AppHeader(
    fileName: String?,
    compiling: Boolean,
    autoCompile: Boolean,
    onAutoCompileChange: (Boolean) -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onExportPdf: () -> Unit,
    canExportPdf: Boolean,
    onShowLog: () -> Unit,
    canShowLog: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onCompile: () -> Unit,
    onStop: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Edge-to-edge: nicht hinter Statusleiste / Kamera-Notch rutschen.
                .windowInsetsPadding(
                    WindowInsets.systemBars.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Titel + aktueller Dateiname. weight(1f) + Ellipsis: der Titel schrumpft,
            // damit die Toolbar rechts IMMER sichtbar bleibt (nie aus dem Bild geschoben).
            Text(
                text = if (fileName != null) "TexDroid — $fileName" else "TexDroid",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            // Häufige Aktionen direkt sichtbar; Seltenes im Kebab-Überlauf (⋮).
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = MaterialTheme.colorScheme.onPrimaryContainer
                ToolbarIcon(Icons.Filled.FolderOpen, "Öffnen", !compiling, tint, onOpen)
                ToolbarIcon(Icons.Filled.Save, "Speichern", !compiling, tint, onSave)
                ToolbarSeparator(tint)
                ToolbarIcon(Icons.AutoMirrored.Filled.Undo, "Rückgängig", !compiling, tint, onUndo)
                ToolbarIcon(Icons.AutoMirrored.Filled.Redo, "Wiederherstellen", !compiling, tint, onRedo)
                ToolbarSeparator(tint)
                CompileButton(compiling = compiling, onCompile = onCompile, onStop = onStop)
                OverflowMenu(
                    tint = tint,
                    compiling = compiling,
                    autoCompile = autoCompile,
                    canExportPdf = canExportPdf,
                    canShowLog = canShowLog,
                    onNew = onNew,
                    onExportPdf = onExportPdf,
                    onShowLog = onShowLog,
                    onAutoCompileChange = onAutoCompileChange,
                )
            }
        }
    }
}

/**
 * Verschiebbarer Trenner zwischen Editor und Vorschau (nur Split-View).
 * Ziehen nach links/rechts ändert das Breitenverhältnis; [onDragDelta] liefert
 * die horizontale Verschiebung in Pixeln.
 */
@Composable
private fun SplitHandle(onDragDelta: (Float) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(12.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> onDragDelta(delta) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        // Griff-Anzeige: kurzer abgerundeter Balken in der Mitte.
        Box(
            Modifier
                .height(36.dp)
                .width(4.dp)
                .background(
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(2.dp),
                ),
        )
    }
}

/** Kebab-Überlauf (⋮) für seltene Aktionen: Neu · PDF speichern · Auto-Compile. */
@Composable
private fun OverflowMenu(
    tint: Color,
    compiling: Boolean,
    autoCompile: Boolean,
    canExportPdf: Boolean,
    canShowLog: Boolean,
    onNew: () -> Unit,
    onExportPdf: () -> Unit,
    onShowLog: () -> Unit,
    onAutoCompileChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Mehr", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Neu") },
                leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) },
                enabled = !compiling,
                onClick = { expanded = false; onNew() },
            )
            DropdownMenuItem(
                text = { Text("PDF speichern") },
                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                enabled = canExportPdf && !compiling,
                onClick = { expanded = false; onExportPdf() },
            )
            DropdownMenuItem(
                text = { Text("Log ansehen") },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                enabled = canShowLog,
                onClick = { expanded = false; onShowLog() },
            )
            DropdownMenuItem(
                text = { Text("Auto-Compile") },
                leadingIcon = {
                    Icon(
                        if (autoCompile) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                    )
                },
                onClick = { onAutoCompileChange(!autoCompile) }, // offen lassen: Haken-Wechsel sichtbar
            )
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(imageVector = icon, contentDescription = description, tint = tint)
    }
}

@Composable
private fun ToolbarSeparator(tint: Color) {
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .height(24.dp)
            .width(1.dp)
            .background(tint.copy(alpha = 0.3f)),
    )
}

@Composable
private fun CompileButton(compiling: Boolean, onCompile: () -> Unit, onStop: () -> Unit) {
    if (compiling) {
        // Während des Compiles wird der Button zum Stopp-Button.
        Button(onClick = onStop) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Stop, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Stopp")
        }
    } else {
        Button(onClick = onCompile) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("Kompilieren")
        }
    }
}

/** Volles TeX-Compile-Log als Bottom-Sheet (Monospace, scrollbar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogSheet(log: String, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(
                "Compile-Log",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                text = log.ifBlank { "(kein Log vorhanden)" },
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            )
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
    onPrevError: () -> Unit,
    onNextError: () -> Unit,
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
            ErrorPanel(errors, onErrorClick, onPrevError, onNextError)
        }
    }
}

@Composable
private fun ErrorPanel(
    errors: List<CompileError>,
    onErrorClick: (CompileError) -> Unit,
    onPrevError: () -> Unit,
    onNextError: () -> Unit,
) {
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
            // Kopfzeile mit Fehler-Navigation (springt zum vorigen/nächsten Fehler).
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Fehler (${errors.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                val navTint = MaterialTheme.colorScheme.onErrorContainer
                IconButton(onClick = onPrevError, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, "Vorheriger Fehler", tint = navTint)
                }
                IconButton(onClick = onNextError, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, "Nächster Fehler", tint = navTint)
                }
            }
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

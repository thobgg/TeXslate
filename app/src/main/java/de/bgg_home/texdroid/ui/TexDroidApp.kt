package de.bgg_home.texdroid.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import de.bgg_home.texdroid.R
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Toc
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.bgg_home.texdroid.ai.AiSettings
import de.bgg_home.texdroid.compile.CompileError
import de.bgg_home.texdroid.compile.LatexCompiler
import de.bgg_home.texdroid.editor.LatexEditor
import de.bgg_home.texdroid.editor.goToLine
import de.bgg_home.texdroid.editor.insertBeforeEndDocument
import de.bgg_home.texdroid.editor.jumpToErrorLine
import de.bgg_home.texdroid.editor.replaceAllMatches
import de.bgg_home.texdroid.editor.replaceCurrentMatch
import de.bgg_home.texdroid.editor.runSearch
import de.bgg_home.texdroid.editor.searchNext
import de.bgg_home.texdroid.editor.searchPrevious
import de.bgg_home.texdroid.editor.selectedText
import de.bgg_home.texdroid.editor.showErrorDiagnostics
import de.bgg_home.texdroid.editor.stopSearch
import de.bgg_home.texdroid.editor.toggleLineComment
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import de.bgg_home.texdroid.pdf.PdfPreview
import de.bgg_home.texdroid.storage.DocumentStore
import de.bgg_home.texdroid.storage.DraftStore
import de.bgg_home.texdroid.storage.ProjectEntry
import de.bgg_home.texdroid.storage.ProjectStore
import de.bgg_home.texdroid.storage.UserTemplate
import de.bgg_home.texdroid.storage.UserTemplateStore
import io.github.rosemoe.sora.widget.CodeEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Wartezeit nach dem letzten Tastendruck, bevor automatisch kompiliert wird. */
private const val AUTO_COMPILE_DEBOUNCE_MS = 800L

/**
 * Beispiel-Dokument beim ersten Start – zeigt alle gehighlighteten Elemente.
 * Der *Inhalt* folgt der UI-Sprache (Deutsch bei deutscher Locale, sonst Englisch);
 * die LaTeX-Struktur bleibt identisch.
 */
private val SAMPLE_TEX: String
    get() = if (java.util.Locale.getDefault().language == "de") SAMPLE_TEX_DE else SAMPLE_TEX_EN

private val SAMPLE_TEX_DE = """
\documentclass{article}
\usepackage{amsmath}

\title{TeXslate}
\author{Auf dem Tablet geschrieben}

\begin{document}
\maketitle

% Ein Kommentar: alles ab Prozentzeichen ist grau.
Willkommen bei \textbf{TeXslate}! Inline-Mathe: ${'$'}E = mc^2${'$'}.

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

private val SAMPLE_TEX_EN = """
\documentclass{article}
\usepackage{amsmath}

\title{TeXslate}
\author{Written on the tablet}

\begin{document}
\maketitle

% A comment: everything after a percent sign is grey.
Welcome to \textbf{TeXslate}! Inline math: ${'$'}E = mc^2${'$'}.

\begin{equation}
    \int_0^\infty e^{-x^2}\,\mathrm{d}x = \frac{\sqrt{\pi}}{2}
\end{equation}

\begin{itemize}
    \item Editor with LaTeX syntax highlighting
    \item Local compile via Tectonic
    \item PDF preview alongside
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
 * Hält den Composable komponiert (sein Zustand – z.B. der Editor-Text – bleibt also
 * erhalten), zeigt ihn aber nur, wenn [visible]. Der unsichtbare Pane wird aus dem
 * sichtbaren Bereich heraus platziert: So bekommt er weder Pixel noch Touch-Events
 * (kein versehentlicher Editor-Fokus hinter einer leeren Vorschau), verliert dabei
 * aber seinen Zustand nicht. Grundlage des Tab-Wechsels ohne Editor-Neuaufbau.
 */
private fun Modifier.keepAlive(visible: Boolean): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    layout(placeable.width, placeable.height) {
        if (visible) placeable.place(0, 0) else placeable.place(x = -placeable.width * 2, y = 0)
    }
}

/**
 * Befehle, die weitere Dateien einbinden (mehrteiliges Projekt). Trifft einer
 * davon zu, braucht der Compile die Geschwisterdateien im Arbeitsverzeichnis –
 * also einen als Projektordner geöffneten Tree, nicht nur die Einzeldatei.
 */
private val EXTERNAL_FILE_REGEX =
    Regex("""\\(input|include|includeonly|subfile|subfileinclude|import|subimport|includegraphics|bibliography|addbibresource|addglobalbib)\b""")

/**
 * Grobheuristik: Bindet [text] weitere Dateien ein? Kommentare (ab unmaskiertem
 * `%` bis Zeilenende) werden ignoriert, damit ein auskommentiertes `\input`
 * keine unnötige Warnung auslöst.
 */
private fun referencesExternalFiles(text: String): Boolean =
    text.lineSequence().any { line ->
        val code = stripLatexComment(line)
        EXTERNAL_FILE_REGEX.containsMatchIn(code)
    }

/** Schneidet den `%`-Kommentar einer Zeile ab (ein `\%` bleibt erhalten). */
private fun stripLatexComment(line: String): String {
    var i = 0
    while (i < line.length) {
        if (line[i] == '%' && (i == 0 || line[i - 1] != '\\')) return line.substring(0, i)
        i++
    }
    return line
}

/**
 * Wurzel-Composable der App. Entscheidet anhand der [WindowSizeClass] zwischen
 * Split-View (breite Tablets) und Tab-Umschaltung (Phone/Portrait).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TexDroidApp(
    windowSizeClass: WindowSizeClass,
    onRegisterDraftSaver: (() -> Unit) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val darkTheme = isSystemInDarkTheme()
    val snackbarHostState = remember { SnackbarHostState() }

    // Zuletzt gesicherter Entwurf (überlebt Prozess-Tod). Einmalig beim Start gelesen;
    // vorhanden → Startinhalt & Dateibezug wiederherstellen, sonst Beispiel-Dokument.
    val startupDraft = remember { DraftStore.load(context) }
    val startupText = startupDraft?.text ?: SAMPLE_TEX

    // Breitenklassen: Expanded (Tablet quer) → Split-View; Compact (Phone hoch)
    // → zusätzlich reduzierter Header, sonst quetscht die volle Toolbar den
    // Compile-Button auf Null-Breite und sein Text bricht zeichenweise um.
    val isWide = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded
    val isCompact = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact

    // Referenz auf den konkreten Editor (für das Auslesen des Textes beim Compile).
    var editor by remember { mutableStateOf<CodeEditor?>(null) }
    var compiling by remember { mutableStateOf(false) }
    // Erster Compile pro Installation lädt einmalig das Tectonic-Paket-Bundle
    // (kann auf schwacher Hardware/langsamem Netz ~1 Min dauern) → Hinweis zeigen.
    val appPrefs = remember { context.getSharedPreferences("app", Context.MODE_PRIVATE) }
    var compiledOnce by remember { mutableStateOf(appPrefs.getBoolean("compiledOnce", false)) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var reloadToken by remember { mutableIntStateOf(0) }
    var errors by remember { mutableStateOf<List<CompileError>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(Tab.Editor) }

    // Auto-Compile (QW 3.1): zählt Editor-Änderungen; ein LaunchedEffect debounced darauf.
    var autoCompile by remember { mutableStateOf(true) }
    var textVersion by remember { mutableIntStateOf(0) }

    // „Trotz Fehlern kompilieren" (Issue #2, Overleaf-Vorbild): Engine läuft bei
    // TeX-Fehlern durch statt anzuhalten. Persistiert — wer es braucht (z.B.
    // .aux-Zweipass-Makros), braucht es in jeder Sitzung.
    var continueOnErrors by remember { mutableStateOf(appPrefs.getBoolean("continueOnErrors", false)) }

    // Split-View: Anteil des Editors an der Breite (per Trenner verschiebbar, 0.2–0.8).
    var splitFraction by remember { mutableFloatStateOf(0.5f) }

    // LaTeX-Einfüge-Sheet (der „mehr"-Button der Favoriten-Leiste) + Tabellen-Wizard.
    var showInsertSheet by remember { mutableStateOf(false) }
    var showTableWizard by remember { mutableStateOf(false) }
    var showDocumentWizard by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var userTemplates by remember { mutableStateOf<List<UserTemplate>>(emptyList()) }

    // KI-Assistenz (QW A1): Einstellungen (verschlüsselter Key, Opt-in).
    val aiSettings = remember { AiSettings(context) }
    var showSettings by remember { mutableStateOf(false) }
    var showAi by remember { mutableStateOf(false) }
    var aiInitialQuestion by remember { mutableStateOf("") }

    // Suchen & Ersetzen (Editor-Komfort). Die eigentliche Suche macht sora-editor;
    // matchCount/matchIndex kommen per PublishSearchResultEvent zurück.
    var showGoToLine by remember { mutableStateOf(false) }
    var showOutline by remember { mutableStateOf(false) }
    var outlineEntries by remember { mutableStateOf<List<OutlineEntry>>(emptyList()) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var replaceText by remember { mutableStateOf("") }
    var searchCaseInsensitive by remember { mutableStateOf(true) }
    var searchRegex by remember { mutableStateOf(false) }
    var searchMatchCount by remember { mutableIntStateOf(0) }
    var searchMatchIndex by remember { mutableIntStateOf(-1) }
    var searchInvalidRegex by remember { mutableStateOf(false) }

    // Build-Komfort: volles Log, laufender Compile-Job (zum Stoppen), Fehler-Cursor.
    var lastLog by remember { mutableStateOf("") }
    var showLog by remember { mutableStateOf(false) }
    var showShare by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var compileJob by remember { mutableStateOf<Job?>(null) }
    var errorIndex by remember { mutableIntStateOf(0) }

    // Aktuell geöffnete Datei (SAF): Uri, Anzeigename und ob wir zurückschreiben dürfen.
    var currentUri by remember { mutableStateOf<Uri?>(null) }
    var currentName by remember { mutableStateOf<String?>(null) }
    var canWrite by remember { mutableStateOf(false) }
    // Gehört das aktuell offene Dokument zum aktiven Projektbaum? Nur dann darf der
    // Compile den Tree ins Arbeitsverzeichnis synchronisieren – sonst würde beim
    // Einzeldatei-Öffnen aus einem fremden Ordner das falsche Projekt kopiert und
    // \input{...} bräche mit „File not found" ab.
    var currentInProject by remember { mutableStateOf(false) }

    // Projekt (M4): gewählter Ordner (Tree-Uri) + aktuelle Navigationsebene.
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val projectPrefs = remember { context.getSharedPreferences("project", Context.MODE_PRIVATE) }
    var projectTree by remember { mutableStateOf<Uri?>(null) }
    var projectWritable by remember { mutableStateOf(false) }
    var projectFolderName by remember { mutableStateOf<String?>(null) }
    var projectStack by remember { mutableStateOf(listOf<ProjectEntry>()) }
    var projectEntries by remember { mutableStateOf(listOf<ProjectEntry>()) }

    // Zuletzt geöffnetes Projekt wiederherstellen (Uri-Recht überlebt Neustart).
    LaunchedEffect(Unit) {
        val saved = projectPrefs.getString("tree", null)?.let(Uri::parse) ?: return@LaunchedEffect
        val perms = context.contentResolver.persistedUriPermissions
        if (perms.none { it.uri == saved && it.isReadPermission }) return@LaunchedEffect
        projectTree = saved
        projectWritable = perms.any { it.uri == saved && it.isWritePermission }
        projectFolderName = ProjectStore.folderName(context, saved) ?: context.getString(R.string.fallback_project)
        projectEntries = ProjectStore.list(context, saved)
    }

    // Entwurf wiederherstellen: Datei-Uri/Name/Schreibrecht des zuletzt offenen
    // Dokuments, sofern das SAF-Recht noch gilt (sonst reiner Entwurf → „Speichern
    // unter…"). Der Editor-Text selbst kommt bereits über startupText hinein.
    LaunchedEffect(Unit) {
        val d = startupDraft ?: return@LaunchedEffect
        currentName = d.name
        val uri = d.uri ?: return@LaunchedEffect
        val perms = context.contentResolver.persistedUriPermissions
        if (perms.none { it.uri == uri && it.isReadPermission }) return@LaunchedEffect
        currentUri = uri
        canWrite = d.canWrite && perms.any { it.uri == uri && it.isWritePermission }
        projectTree?.let { currentInProject = ProjectStore.isWithinTree(uri, it) }
    }

    // Aktuellen Editor-Inhalt als Entwurf sichern lassen (Aufruf aus onStop). Der
    // Rückruf liest Editor-/Datei-Zustand erst bei Ausführung → immer aktuell.
    DisposableEffect(Unit) {
        onRegisterDraftSaver {
            editor?.text?.toString()?.let { text ->
                DraftStore.save(context, text, currentUri, currentName, canWrite)
            }
        }
        onDispose { onRegisterDraftSaver {} }
    }

    // Trefferzähler live halten: sora meldet fertige Suchergebnisse per Event.
    // Ohne aktives Muster (z.B. nach stopSearch) werfen die Getter „pattern not
    // set" – deshalb erst hasQuery() prüfen.
    LaunchedEffect(editor) {
        val ed = editor ?: return@LaunchedEffect
        ed.subscribeEvent(PublishSearchResultEvent::class.java) { _, _ ->
            if (ed.searcher.hasQuery()) {
                searchMatchCount = ed.searcher.matchedPositionCount
                searchMatchIndex = ed.searcher.currentMatchedPositionIndex
            } else {
                searchMatchCount = 0
                searchMatchIndex = -1
            }
        }
    }

    // Suche (neu) ausführen, sobald sich Text/Optionen ändern oder die Leiste öffnet.
    LaunchedEffect(searchQuery, searchCaseInsensitive, searchRegex, showSearch, editor) {
        val ed = editor
        if (!showSearch || ed == null) return@LaunchedEffect
        searchInvalidRegex = !ed.runSearch(searchQuery, searchCaseInsensitive, searchRegex)
        if (searchQuery.isEmpty() || searchInvalidRegex) {
            searchMatchCount = 0
            searchMatchIndex = -1
        }
    }

    fun closeSearch() {
        editor?.stopSearch()
        showSearch = false
        searchInvalidRegex = false
        searchMatchCount = 0
        searchMatchIndex = -1
    }

    // Trefferzähler nach Navigation/Ersetzen auffrischen (das Event feuert nur
    // bei einer Neusuche, nicht beim Weiterspringen).
    fun refreshSearchCounts() {
        val ed = editor ?: return
        if (ed.searcher.hasQuery()) {
            searchMatchCount = ed.searcher.matchedPositionCount
            searchMatchIndex = ed.searcher.currentMatchedPositionIndex
        } else {
            searchMatchCount = 0
            searchMatchIndex = -1
        }
    }

    // SAF: Datei öffnen (ACTION_OPEN_DOCUMENT).
    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            canWrite = DocumentStore.takePersistablePermission(context, uri)
            val tree = projectTree
            val within = tree != null && ProjectStore.isWithinTree(uri, tree)
            currentInProject = within
            scope.launch {
                val text = DocumentStore.read(context, uri)
                editor?.setText(text)
                currentUri = uri
                currentName = DocumentStore.displayName(context, uri) ?: "dokument.tex"
                // Mehrteilige Datei außerhalb des Projekts geöffnet → \input & Co.
                // finden ihre Geschwisterdateien nicht. Klar darauf hinweisen,
                // statt den Nutzer später ins „File not found" laufen zu lassen.
                if (!within && referencesExternalFiles(text)) {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.snackbar_multifile_hint, currentName),
                    )
                } else {
                    snackbarHostState.showSnackbar(context.getString(R.string.snackbar_opened, currentName))
                }
            }
        }
    }

    // Aktuelle Ordner-Ebene (Wurzel oder Unterordner) neu laden.
    fun reloadProjectLevel() {
        val tree = projectTree ?: return
        scope.launch {
            projectEntries = if (projectStack.isEmpty()) {
                ProjectStore.list(context, tree)
            } else {
                ProjectStore.listSubdir(context, tree, projectStack.last().uri)
            }
        }
    }

    // SAF: Projektordner öffnen (ACTION_OPEN_DOCUMENT_TREE).
    val openFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            projectWritable = DocumentStore.takePersistablePermission(context, uri)
            projectTree = uri
            projectStack = emptyList()
            projectPrefs.edit().putString("tree", uri.toString()).apply()
            scope.launch {
                projectFolderName = ProjectStore.folderName(context, uri) ?: context.getString(R.string.fallback_project)
                projectEntries = ProjectStore.list(context, uri)
            }
        }
    }

    fun openProjectEntry(entry: ProjectEntry) {
        if (entry.isDir) {
            projectStack = projectStack + entry
            reloadProjectLevel()
            return
        }
        scope.launch {
            val text = DocumentStore.read(context, entry.uri)
            editor?.setText(text)
            currentUri = entry.uri
            currentName = entry.name
            canWrite = projectWritable // Tree-Recht deckt alle Dateien darin ab.
            currentInProject = true // aus dem Projektbaum → \input findet die Geschwister.
            drawerState.close()
            snackbarHostState.showSnackbar(context.getString(R.string.snackbar_opened, entry.name))
        }
    }

    fun projectUp() {
        if (projectStack.isNotEmpty()) {
            projectStack = projectStack.dropLast(1)
            reloadProjectLevel()
        }
    }

    // SAF: „Speichern unter…" (ACTION_CREATE_DOCUMENT).
    // MIME „text/x-tex": Der SAF-Picker leitet die Endung aus dem MIME-Typ ab –
    // mit „text/plain" hängte er fälschlich „.txt" an den vorgeschlagenen „…​.tex"-Namen.
    val saveAsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/x-tex"),
    ) { uri ->
        val text = editor?.text?.toString()
        if (uri != null && text != null) {
            canWrite = DocumentStore.takePersistablePermission(context, uri)
            val tree = projectTree
            currentInProject = tree != null && ProjectStore.isWithinTree(uri, tree)
            scope.launch {
                DocumentStore.write(context, uri, text)
                currentUri = uri
                currentName = DocumentStore.displayName(context, uri) ?: "dokument.tex"
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_saved, currentName))
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
                    context.getString(
                        R.string.snackbar_pdf_saved,
                        DocumentStore.displayName(context, uri) ?: "PDF",
                    ),
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
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_compile_first)) }
            return
        }
        val base = currentName?.removeSuffix(".tex") ?: "dokument"
        exportPdfLauncher.launch("$base.pdf")
    }

    // Teilt TeX-Quelle und/oder PDF in einem Zug. Für sinnvolle Dateinamen im
    // Share-Sheet (statt „document.*") werden nett benannte Cache-Kopien via
    // content:// (FileProvider) gereicht. Bei zwei Dateien: ACTION_SEND_MULTIPLE.
    fun shareSelected(includeTex: Boolean, includePdf: Boolean) {
        val source = editor?.text?.toString()
        val pdf = pdfFile
        val base = currentName?.removeSuffix(".tex") ?: "dokument"
        scope.launch {
            val uris = withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, "shared").apply { mkdirs() }
                val list = ArrayList<Uri>()
                if (includeTex && source != null) {
                    val f = File(dir, "$base.tex").apply { writeText(source) }
                    list += FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                }
                if (includePdf && pdf != null) {
                    val f = File(dir, "$base.pdf").also { pdf.copyTo(it, overwrite = true) }
                    list += FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                }
                list
            }
            if (uris.isEmpty()) {
                snackbarHostState.showSnackbar(context.getString(R.string.snackbar_nothing_to_share))
                return@launch
            }
            val send = if (uris.size == 1) {
                Intent(Intent.ACTION_SEND).apply {
                    type = if (uris[0].toString().endsWith(".pdf")) "application/pdf" else "text/x-tex"
                    putExtra(Intent.EXTRA_STREAM, uris[0])
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    type = "*/*"
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                }
            }
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, context.getString(R.string.share_title)))
        }
    }

    fun saveDocument() {
        val text = editor?.text?.toString() ?: return
        val uri = currentUri
        if (uri != null && canWrite) {
            scope.launch {
                DocumentStore.write(context, uri, text)
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.snackbar_saved,
                        currentName ?: context.getString(R.string.fallback_document),
                    ),
                )
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
        currentInProject = false
    }

    fun runCompile(manual: Boolean = false) {
        val source = editor?.text?.toString()
        if (source == null || compiling) return
        compiling = true
        compileJob = scope.launch {
            try {
                // Tree nur synchronisieren, wenn das offene Dokument wirklich dazu
                // gehört – sonst kopierte syncToDir das falsche Projekt (Fußangel).
                val result = LatexCompiler.compile(
                    context, source, projectTree.takeIf { currentInProject },
                    continueOnErrors = continueOnErrors,
                )
                lastLog = result.log.ifBlank { result.engineError }
                errors = result.errors
                errorIndex = 0
                if (result.ok && result.pdfPath.isNotEmpty()) {
                    pdfFile = File(result.pdfPath)
                    reloadToken++ // Preview neu laden, Scroll-Position bleibt erhalten.
                    // Tab-Layout: Ein bewusst gedrückter Compile will das Ergebnis
                    // sehen → zur Vorschau wechseln. Beim Auto-Compile nicht –
                    // der Nutzer tippt gerade und würde aus dem Editor gerissen.
                    if (manual && !isWide) selectedTab = Tab.Preview
                } else if (!isWide && result.errors.isNotEmpty()) {
                    // Fehlerpanel sitzt unter dem Editor → dorthin wechseln.
                    selectedTab = Tab.Editor
                }
                snackbarHostState.showSnackbar(result.summary())
            } finally {
                // Läuft auch bei Abbruch (finally) – erst wenn der native Aufruf
                // wirklich zurückkehrt, damit keine zwei Compiles gleichzeitig laufen.
                compiling = false
                // Nach dem ersten Compile ist das Bundle geladen → Hinweis künftig aus.
                if (!compiledOnce) {
                    compiledOnce = true
                    appPrefs.edit().putBoolean("compiledOnce", true).apply()
                }
            }
        }
    }

    fun stopCompile() {
        // Cancel verwirft das Ergebnis; der native Tectonic-Aufruf lässt sich
        // aber nicht mitten drin abbrechen – er läuft im Hintergrund zu Ende.
        compileJob?.cancel()
        scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.snackbar_compile_cancelled)) }
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

    // QW A4: „Fehler erklären" – KI-Assistent mit vorbefüllter Frage zum Fehler öffnen.
    val onExplainError: (CompileError) -> Unit = { err ->
        val where = err.line?.let { context.getString(R.string.error_line_paren, it) } ?: ""
        aiInitialQuestion = context.getString(R.string.ai_explain_error, where, err.message)
        showAi = true
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Wisch-Gesten NUR bei offener Sidebar (zum Zuwischen). Sonst fängt der
        // Drawer jedes horizontale Ziehen im Editor ab – u.a. das Ziehen der
        // Auswahlgriffe beim Markieren riss ständig die Sidebar auf. Geöffnet
        // wird sie weiterhin über den ☰-Knopf.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            ModalDrawerSheet {
                ProjectDrawer(
                    folderName = projectFolderName,
                    canGoUp = projectStack.isNotEmpty(),
                    entries = projectEntries,
                    currentUri = currentUri,
                    onOpenFolder = { openFolderLauncher.launch(null) },
                    onUp = ::projectUp,
                    onEntryClick = ::openProjectEntry,
                )
            }
        },
    ) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppHeader(
                compact = isCompact,
                fileName = currentName,
                compiling = compiling,
                autoCompile = autoCompile,
                onAutoCompileChange = { autoCompile = it },
                continueOnErrors = continueOnErrors,
                onContinueOnErrorsChange = {
                    continueOnErrors = it
                    appPrefs.edit().putBoolean("continueOnErrors", it).apply()
                },
                onMenu = { scope.launch { drawerState.open() } },
                onNew = ::newDocument,
                onOpen = ::openDocument,
                onSave = ::saveDocument,
                onExportPdf = ::exportPdf,
                onShare = { showShare = true },
                onSearch = { if (showSearch) closeSearch() else showSearch = true },
                onGoToLine = { showGoToLine = true },
                onToggleComment = { editor?.toggleLineComment() },
                onOutline = {
                    outlineEntries = parseOutline(editor?.text?.toString() ?: "")
                    showOutline = true
                },
                onAi = { aiInitialQuestion = ""; showAi = true },
                canExportPdf = pdfFile != null,
                onShowLog = { showLog = true },
                canShowLog = lastLog.isNotBlank(),
                onSettings = { showSettings = true },
                onAbout = { showAbout = true },
                onUndo = { editor?.undo() },
                onRedo = { editor?.redo() },
                onCompile = { runCompile(manual = true) },
                onStop = ::stopCompile,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // imePadding(): Inhalt über der Software-Tastatur halten (edge-to-edge
        // verkleinert das Fenster sonst nicht → Tastatur überdeckte den Editor).
        Column(Modifier.padding(innerPadding).imePadding().fillMaxSize()) {
            // Suchleiste – nur über dem sichtbaren Editor.
            if (showSearch && (isWide || selectedTab == Tab.Editor)) {
                SearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    replacement = replaceText,
                    onReplacementChange = { replaceText = it },
                    caseInsensitive = searchCaseInsensitive,
                    onCaseInsensitiveChange = { searchCaseInsensitive = it },
                    regex = searchRegex,
                    onRegexChange = { searchRegex = it },
                    matchIndex = searchMatchIndex,
                    matchCount = searchMatchCount,
                    invalidRegex = searchInvalidRegex,
                    onPrev = { editor?.searchPrevious(); refreshSearchCounts() },
                    onNext = { editor?.searchNext(); refreshSearchCounts() },
                    onReplace = { editor?.replaceCurrentMatch(replaceText); refreshSearchCounts() },
                    onReplaceAll = { editor?.replaceAllMatches(replaceText); refreshSearchCounts() },
                    onClose = { closeSearch() },
                )
                HorizontalDivider()
            }
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
            // Editor und Vorschau als „movable content": Über den Tab-Wechsel (Phone)
            // UND den Split↔Tab-Wechsel (Zoom/Fenstergröße) hinweg bleibt dieselbe
            // Editor-Instanz (CodeEditor-View samt Text) erhalten. Früher entsorgte
            // jeder solche Wechsel die AndroidView → Factory lief neu →
            // setText(SAMPLE_TEX) überschrieb das Dokument mit dem Demo-Text.
            val darkThemeState = rememberUpdatedState(darkTheme)
            val editorPane = remember {
                movableContentOf { paneModifier: Modifier ->
                    EditorPane(
                        initialText = startupText,
                        errors = errors,
                        darkTheme = darkThemeState.value,
                        onEditorCreated = { editor = it },
                        onTextChanged = { textVersion++ },
                        onErrorClick = onErrorClick,
                        onExplainError = onExplainError,
                        onPrevError = { jumpToError(errorIndex - 1) },
                        onNextError = { jumpToError(errorIndex + 1) },
                        modifier = paneModifier,
                    )
                }
            }
            val previewPane = remember {
                movableContentOf { paneModifier: Modifier ->
                    PreviewPane(
                        pdfFile = pdfFile,
                        reloadToken = reloadToken,
                        compiling = compiling,
                        firstCompile = compiling && !compiledOnce,
                        modifier = paneModifier,
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
            if (isWide) {
                // Tablet-Landscape: echte Split-View (Editor | Preview) mit
                // verschiebbarem Trenner. splitFraction = Breitenanteil des Editors.
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val totalPx = constraints.maxWidth.toFloat()
                    Row(Modifier.fillMaxSize()) {
                        editorPane(Modifier.weight(splitFraction).fillMaxSize())
                        SplitHandle(
                            onDragDelta = { deltaPx ->
                                splitFraction = (splitFraction + deltaPx / totalPx).coerceIn(0.2f, 0.8f)
                            },
                        )
                        previewPane(Modifier.weight(1f - splitFraction).fillMaxSize())
                    }
                }
            } else {
                // Phone / schmal: Tab-Umschaltung Editor ↔ Vorschau. Beide Panes
                // bleiben komponiert (Zustand bleibt); der aktive liegt sichtbar oben,
                // der inaktive transparent darunter. Die Umschaltleiste bleibt IMMER
                // sichtbar – sie früher bei offener Tastatur auszublenden konnte den
                // Nutzer stranden lassen (hängende IME-Insets = kein Zurück).
                Column(Modifier.fillMaxSize()) {
                    TabRow(selectedTabIndex = selectedTab.ordinal) {
                        Tab(
                            selected = selectedTab == Tab.Editor,
                            onClick = { selectedTab = Tab.Editor },
                            text = { Text(stringResource(R.string.tab_editor)) },
                        )
                        Tab(
                            selected = selectedTab == Tab.Preview,
                            onClick = { selectedTab = Tab.Preview },
                            text = { Text(stringResource(R.string.tab_preview)) },
                        )
                    }
                    Box(Modifier.weight(1f).fillMaxSize()) {
                        editorPane(Modifier.fillMaxSize().keepAlive(selectedTab == Tab.Editor))
                        previewPane(Modifier.fillMaxSize().keepAlive(selectedTab == Tab.Preview))
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
                userTemplates = UserTemplateStore.list(context)
                showTemplates = true
            },
            onDismiss = { showInsertSheet = false },
        )
    }

    // Offline-Vorlagen-Bibliothek (QW T1): Auswahl lädt die Vorlage in den Editor.
    if (showTemplates) {
        TemplatePickerSheet(
            userTemplates = userTemplates,
            canSaveCurrent = editor?.text?.toString()?.isNotBlank() == true,
            onPickBuiltin = { template ->
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
            onPickUser = { template ->
                showTemplates = false
                scope.launch {
                    val text = withContext(Dispatchers.IO) { UserTemplateStore.read(template) }
                    editor?.setText(text)
                    editor?.setSelection(0, 0)
                    editor?.requestFocus()
                    currentUri = null
                    currentName = null
                    canWrite = false
                }
            },
            onSaveCurrent = { name ->
                val content = editor?.text?.toString() ?: ""
                scope.launch {
                    val saved = withContext(Dispatchers.IO) {
                        UserTemplateStore.save(context, name, content)
                    }
                    userTemplates = UserTemplateStore.list(context)
                    snackbarHostState.showSnackbar(
                        if (saved != null) {
                            context.getString(R.string.template_saved, saved)
                        } else {
                            context.getString(R.string.template_save_failed)
                        },
                    )
                }
            },
            onDeleteUser = { template ->
                UserTemplateStore.delete(template)
                userTemplates = UserTemplateStore.list(context)
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

    // Dokumentstruktur (Kebab → „Dokumentstruktur…"): Sektion antippen → springen.
    if (showOutline) {
        OutlineSheet(
            entries = outlineEntries,
            onJump = { line -> editor?.goToLine(line); showOutline = false },
            onDismiss = { showOutline = false },
        )
    }

    // Gehe zu Zeile (Kebab → „Gehe zu Zeile…").
    if (showGoToLine) {
        GoToLineDialog(
            lineCount = (editor?.text?.lineCount ?: 1).coerceAtLeast(1),
            onGo = { line -> editor?.goToLine(line); showGoToLine = false },
            onDismiss = { showGoToLine = false },
        )
    }

    // KI-Einstellungen (Kebab → „Einstellungen").
    if (showSettings) {
        AiSettingsSheet(settings = aiSettings, onDismiss = { showSettings = false })
    }
    if (showAi) {
        AiAssistantSheet(
            settings = aiSettings,
            selection = editor?.selectedText() ?: "",
            document = editor?.text?.toString() ?: "",
            onInsert = { text -> editor?.insertText(text, text.length); editor?.requestFocus() },
            onInsertBody = { text -> editor?.insertBeforeEndDocument(text) },
            onOpenSettings = { showSettings = true },
            onDismiss = { showAi = false },
            initialQuestion = aiInitialQuestion,
        )
    }
    if (showShare) {
        ShareDialog(
            hasText = !editor?.text?.toString().isNullOrEmpty(),
            hasPdf = pdfFile != null,
            onDismiss = { showShare = false },
            onShare = { tex, pdf -> showShare = false; shareSelected(tex, pdf) },
        )
    }
    if (showAbout) {
        AboutDialog(onDismiss = { showAbout = false })
    }
    } // ModalNavigationDrawer
}

/** Auswahl beim Teilen: TeX-Quelle, PDF oder beides – in einem Dialog. */
@Composable
private fun ShareDialog(
    hasText: Boolean,
    hasPdf: Boolean,
    onDismiss: () -> Unit,
    onShare: (tex: Boolean, pdf: Boolean) -> Unit,
) {
    var tex by remember { mutableStateOf(hasText) }
    var pdf by remember { mutableStateOf(hasPdf) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_title)) },
        text = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasText) { tex = !tex }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = tex && hasText, enabled = hasText, onCheckedChange = { tex = it })
                    Text(stringResource(R.string.share_tex_source), modifier = Modifier.padding(start = 4.dp))
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasPdf) { pdf = !pdf }
                        .padding(vertical = 4.dp),
                ) {
                    Checkbox(checked = pdf && hasPdf, enabled = hasPdf, onCheckedChange = { pdf = it })
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(stringResource(R.string.share_pdf))
                        if (!hasPdf) {
                            Text(
                                stringResource(R.string.share_compile_first_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onShare(tex && hasText, pdf && hasPdf) },
                enabled = (tex && hasText) || (pdf && hasPdf),
            ) { Text(stringResource(R.string.share_title)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * „Über TeXslate": App-Version, Entwickler, Lizenz und die Nennung der
 * enthaltenen Open-Source-Komponenten. Die Fonts (Latin Modern / TeX Gyre) stehen
 * unter der GUST Font License (LPPL) und MÜSSEN genannt werden – dieser Dialog ist
 * damit auch die Lizenz-Erfüllung, nicht nur Deko.
 */
@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val repoUrl = "https://github.com/thobgg/TeXslate"
    val contactMail = "texslate@bgg-mail.de"

    @Suppress("DEPRECATION")
    val version = runCatching {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        "${pi.versionName} (${pi.versionCode})"
    }.getOrDefault("")

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { AppLogo() },
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (version.isNotBlank()) {
                    Text(
                        stringResource(R.string.about_version, version),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Text(
                    stringResource(R.string.about_description),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.about_developer),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    stringResource(R.string.about_contact, contactMail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri("mailto:$contactMail") },
                )
                Text(
                    stringResource(R.string.about_license),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    repoUrl,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri(repoUrl) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    stringResource(R.string.about_components_header),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.about_components),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun AppHeader(
    compact: Boolean,
    fileName: String?,
    compiling: Boolean,
    autoCompile: Boolean,
    onAutoCompileChange: (Boolean) -> Unit,
    continueOnErrors: Boolean,
    onContinueOnErrorsChange: (Boolean) -> Unit,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onSave: () -> Unit,
    onExportPdf: () -> Unit,
    onShare: () -> Unit,
    onSearch: () -> Unit,
    onGoToLine: () -> Unit,
    onToggleComment: () -> Unit,
    onOutline: () -> Unit,
    onAi: () -> Unit,
    canExportPdf: Boolean,
    onShowLog: () -> Unit,
    canShowLog: Boolean,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
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
            // Projekt-Sidebar öffnen (Dateibaum).
            ToolbarIcon(
                Icons.Filled.Menu, stringResource(R.string.header_project), true,
                MaterialTheme.colorScheme.onPrimaryContainer, onMenu,
            )
            if (!compact) {
                // App-Logo (Branding): TeX-Monogramm als kleines Icon-Badge.
                AppLogo()
                Spacer(Modifier.width(8.dp))
            }
            // Titel + aktueller Dateiname. weight(1f) + Ellipsis: der Titel schrumpft,
            // damit die Toolbar rechts IMMER sichtbar bleibt (nie aus dem Bild geschoben).
            Text(
                text = if (fileName != null) {
                    stringResource(R.string.header_title_with_file, fileName)
                } else {
                    stringResource(R.string.app_name)
                },
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 8.dp),
            )
            // Häufige Aktionen direkt sichtbar; Seltenes im Kebab-Überlauf (⋮).
            // Compact (Phone hoch): nur Speichern · Undo · Compile-Icon passen
            // neben den Titel – der Rest wandert zusätzlich ins Kebab-Menü.
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tint = MaterialTheme.colorScheme.onPrimaryContainer
                if (compact) {
                    ToolbarIcon(Icons.Filled.Save, stringResource(R.string.save), !compiling, tint, onSave)
                    ToolbarIcon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), !compiling, tint, onUndo)
                    CompileIconButton(compiling = compiling, tint = tint, onCompile = onCompile, onStop = onStop)
                } else {
                    ToolbarIcon(Icons.Filled.FolderOpen, stringResource(R.string.open), !compiling, tint, onOpen)
                    ToolbarIcon(Icons.Filled.Save, stringResource(R.string.save), !compiling, tint, onSave)
                    ToolbarSeparator(tint)
                    ToolbarIcon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.undo), !compiling, tint, onUndo)
                    ToolbarIcon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.redo), !compiling, tint, onRedo)
                    ToolbarSeparator(tint)
                    ToolbarIcon(Icons.Filled.Search, stringResource(R.string.search_replace_title), true, tint, onSearch)
                    ToolbarIcon(Icons.Filled.AutoAwesome, stringResource(R.string.ai_assistant), !compiling, tint, onAi)
                    CompileButton(compiling = compiling, onCompile = onCompile, onStop = onStop)
                }
                OverflowMenu(
                    tint = tint,
                    compact = compact,
                    compiling = compiling,
                    autoCompile = autoCompile,
                    continueOnErrors = continueOnErrors,
                    onContinueOnErrorsChange = onContinueOnErrorsChange,
                    canExportPdf = canExportPdf,
                    canShowLog = canShowLog,
                    onNew = onNew,
                    onOpen = onOpen,
                    onRedo = onRedo,
                    onSearch = onSearch,
                    onAi = onAi,
                    onExportPdf = onExportPdf,
                    onShare = onShare,
                    onShowLog = onShowLog,
                    onGoToLine = onGoToLine,
                    onToggleComment = onToggleComment,
                    onOutline = onOutline,
                    onSettings = onSettings,
                    onAbout = onAbout,
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

/**
 * Kebab-Überlauf (⋮) für seltene Aktionen: Neu · PDF speichern · Auto-Compile.
 * Im [compact]-Modus (Phone hoch) zusätzlich die Aktionen, die dort nicht mehr
 * als eigene Toolbar-Icons passen: Öffnen · Redo · Suchen · KI-Assistent.
 */
@Composable
private fun OverflowMenu(
    tint: Color,
    compact: Boolean,
    compiling: Boolean,
    autoCompile: Boolean,
    continueOnErrors: Boolean,
    onContinueOnErrorsChange: (Boolean) -> Unit,
    canExportPdf: Boolean,
    canShowLog: Boolean,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onRedo: () -> Unit,
    onSearch: () -> Unit,
    onAi: () -> Unit,
    onExportPdf: () -> Unit,
    onShare: () -> Unit,
    onShowLog: () -> Unit,
    onGoToLine: () -> Unit,
    onToggleComment: () -> Unit,
    onOutline: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
    onAutoCompileChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.more), tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (compact) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.open)) },
                    leadingIcon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                    enabled = !compiling,
                    onClick = { expanded = false; onOpen() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.redo)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = null) },
                    enabled = !compiling,
                    onClick = { expanded = false; onRedo() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_replace_title)) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    onClick = { expanded = false; onSearch() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_assistant)) },
                    leadingIcon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    enabled = !compiling,
                    onClick = { expanded = false; onAi() },
                )
                HorizontalDivider()
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_new)) },
                leadingIcon = { Icon(Icons.Filled.NoteAdd, contentDescription = null) },
                enabled = !compiling,
                onClick = { expanded = false; onNew() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_save_pdf)) },
                leadingIcon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                enabled = canExportPdf && !compiling,
                onClick = { expanded = false; onExportPdf() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_share)) },
                leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                enabled = !compiling,
                onClick = { expanded = false; onShare() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_view_log)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Article, contentDescription = null) },
                enabled = canShowLog,
                onClick = { expanded = false; onShowLog() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_goto_line)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.List, contentDescription = null) },
                onClick = { expanded = false; onGoToLine() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_outline)) },
                leadingIcon = { Icon(Icons.Filled.Toc, contentDescription = null) },
                onClick = { expanded = false; onOutline() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_toggle_comment)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Comment, contentDescription = null) },
                onClick = { expanded = false; onToggleComment() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_autocompile)) },
                leadingIcon = {
                    Icon(
                        if (autoCompile) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                    )
                },
                onClick = { onAutoCompileChange(!autoCompile) }, // offen lassen: Haken-Wechsel sichtbar
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_continue_on_errors)) },
                leadingIcon = {
                    Icon(
                        if (continueOnErrors) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                        contentDescription = null,
                    )
                },
                onClick = { onContinueOnErrorsChange(!continueOnErrors) }, // offen lassen: Haken-Wechsel sichtbar
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_settings)) },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = { expanded = false; onSettings() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_about)) },
                leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
                onClick = { expanded = false; onAbout() },
            )
        }
    }
}

/** App-Logo fürs Header-Branding: ∑-Monogramm auf Schiefer-Badge. */
@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF2B3940), Color(0xFF3A4C58))),
            ),
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxSize(),
        )
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

/**
 * Platzsparender Compile-Button für den Compact-Header (Phone hoch): nur ein
 * Icon statt Text-Button. Während des Compiles wird er zum Stopp-Button mit
 * umlaufendem Fortschrittsring.
 */
@Composable
private fun CompileIconButton(
    compiling: Boolean,
    tint: Color,
    onCompile: () -> Unit,
    onStop: () -> Unit,
) {
    if (compiling) {
        IconButton(onClick = onStop) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                Icon(
                    Icons.Filled.Stop,
                    contentDescription = stringResource(R.string.stop),
                    tint = tint,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    } else {
        IconButton(onClick = onCompile) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.compile),
                tint = tint,
            )
        }
    }
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
            Text(stringResource(R.string.stop))
        }
    } else {
        Button(onClick = onCompile) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text(stringResource(R.string.compile))
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
                stringResource(R.string.compile_log),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Text(
                text = log.ifBlank { stringResource(R.string.log_empty) },
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
    initialText: String,
    errors: List<CompileError>,
    darkTheme: Boolean,
    onEditorCreated: (CodeEditor) -> Unit,
    onTextChanged: () -> Unit,
    onErrorClick: (CompileError) -> Unit,
    onExplainError: (CompileError) -> Unit,
    onPrevError: () -> Unit,
    onNextError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        LatexEditor(
            initialText = initialText,
            darkTheme = darkTheme,
            onEditorCreated = onEditorCreated,
            onTextChanged = onTextChanged,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        if (errors.isNotEmpty()) {
            ErrorPanel(errors, onErrorClick, onExplainError, onPrevError, onNextError)
        }
    }
}

@Composable
private fun ErrorPanel(
    errors: List<CompileError>,
    onErrorClick: (CompileError) -> Unit,
    onExplainError: (CompileError) -> Unit,
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
                    text = stringResource(R.string.errors_count, errors.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f),
                )
                val navTint = MaterialTheme.colorScheme.onErrorContainer
                IconButton(onClick = onPrevError, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.prev_error), tint = navTint)
                }
                IconButton(onClick = onNextError, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.next_error), tint = navTint)
                }
            }
            errors.forEach { err ->
                val prefix = err.line?.let { stringResource(R.string.error_line_prefix, it) } ?: ""
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "• $prefix${err.message}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onErrorClick(err) } // QW 3.2: Sprung zur Fehlerzeile
                            .padding(vertical = 2.dp),
                    )
                    // QW A4: KI direkt am Fehler – öffnet den Assistenten mit vorbefüllter Frage.
                    AssistChip(
                        onClick = { onExplainError(err) },
                        label = { Text(stringResource(R.string.explain)) },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewPane(
    pdfFile: File?,
    reloadToken: Int,
    compiling: Boolean,
    firstCompile: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        when {
            pdfFile != null -> PdfPreview(file = pdfFile, reloadToken = reloadToken)
            compiling -> Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp),
            ) {
                CircularProgressIndicator()
                if (firstCompile) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.loading_bundle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> Text(
                text = stringResource(R.string.preview_not_compiled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

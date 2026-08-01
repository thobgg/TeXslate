package de.bgg_home.texdroid.compile

import android.content.Context
import android.net.Uri
import de.bgg_home.texdroid.R
import de.bgg_home.texdroid.RustBridge
import de.bgg_home.texdroid.storage.FontStore
import de.bgg_home.texdroid.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dünne Orchestrierungsschicht über der nativen Compile-Brücke.
 *
 * Verantwortlich für:
 *  • das beschreibbare Arbeits-/Cache-Verzeichnis (`filesDir/job`) bereitstellen,
 *  • den Aufruf auf einen Hintergrund-Thread verlagern (Compile blockiert!),
 *  • das JSON-Ergebnis in ein [CompileResult] parsen,
 *  • sauber abfangen, falls die native Lib die neue Funktion noch nicht kennt.
 */
object LatexCompiler {

    /**
     * Kompiliert [source] und gibt das Ergebnis zurück. Läuft komplett auf
     * [Dispatchers.IO] – vom UI-Thread via `launch { compile(...) }` aufrufbar.
     *
     * Ist [projectTree] gesetzt (offenes Mehrdatei-Projekt, QW 4.2), werden vorher
     * alle Projektdateien ins Arbeitsverzeichnis kopiert, damit `\input{...}` und
     * `\include{...}` auf die Geschwisterdateien auflösen.
     */
    suspend fun compile(
        context: Context,
        source: String,
        projectTree: Uri? = null,
        mainFileUri: Uri? = null,
        continueOnErrors: Boolean = false,
    ): CompileResult =
        withContext(Dispatchers.IO) {
            // biblatex mit biber-Backend (Issue #1). In der **thesis**-Edition die
            // cross-gebaute biber-Runtime aufsetzen und normal weiterkompilieren –
            // Tectonic ruft `biber` dann selbst (tex→biber→tex). In **core** (oder
            // fremder ABI / Setup-Fehler) den erklärenden Preflight-Fehler liefern,
            // statt Tectonic mit „can't open path …bib" abbrechen zu lassen.
            // Leeres Dokument: LaTeX erzeugt keine Seite, also auch keine .xdv –
            // der PDF-Schritt bricht dann mit „cannot open document.xdv" ab. Das ist
            // ausgerechnet der erste Fehler, den ein neuer Nutzer sieht: „Neu"
            // antippen, „Kompilieren" antippen. Lieber vorher klar sagen, was fehlt.
            if (hasEmptyBody(source)) {
                return@withContext CompileResult.preflightError(
                    line = null,
                    message = context.getString(R.string.error_empty_document),
                )
            }
            val biberLine = findBiberLine(source)
            if (biberLine != null && !BiberRuntime.ensureReady(context)) {
                return@withContext CompileResult.preflightError(
                    line = biberLine,
                    message = context.getString(R.string.error_biber_unsupported),
                )
            }
            val jobDir = File(context.filesDir, "job").apply { mkdirs() }
            try {
                cleanAuxArtifacts(jobDir, mainFileUri?.toString(), source)
                if (projectTree != null) {
                    // Ab dem Ordner der Hauptdatei spiegeln, damit Geschwister-
                    // dateien (Klasse, \input) neben `document.tex` in der Job-
                    // Wurzel landen – auch wenn das Projekt in einem Unterordner
                    // des gewählten Baums liegt. Ohne bekannte Hauptdatei: ab Wurzel.
                    if (mainFileUri != null) {
                        ProjectStore.syncProjectOf(context, projectTree, mainFileUri, jobDir)
                    } else {
                        ProjectStore.syncToDir(context, projectTree, jobDir)
                    }
                }
                // Fonts auspacken + fonts.conf sicherstellen, damit \setmainfont{<Name>}
                // (Latin Modern / TeX Gyre / Systemfonts) per Name aufgelöst wird.
                // Dateinamen angleichen, die sich nur in der Schreibweise unterscheiden
                // (PC-Dokumente, siehe [FileCaseFix]) – vor allen weiteren Prüfungen,
                // damit die EPS-Erkennung die Dateien schon unter dem richtigen Namen sieht.
                val caseFixed = FileCaseFix.fixCaseMismatches(source, jobDir)

                // EPS abfangen, BEVOR die Engine läuft: der fehlgeschlagene Einbau
                // vergiftet dvipdfmx' globalen Zustand, der nächste Compile im selben
                // Prozess bricht dann per C-Assertion ab (siehe [GraphicsCheck]).
                val epsResult = EpsPlaceholder.withPlaceholders(
                    source,
                    jobDir.list()?.asList().orEmpty(),
                    context.getString(R.string.eps_placeholder_label),
                )
                if (epsResult.figures.isNotEmpty() && epsResult.rewritten == null) {
                    // Ohne \begin{document} lässt sich das Makro nicht unterbringen.
                    return@withContext CompileResult.preflightError(
                        line = null,
                        message = context.getString(
                            R.string.error_eps_unsupported,
                            epsResult.figures.joinToString(", "),
                        ),
                    )
                }
                val toCompile = epsResult.rewritten ?: source
                val fontConfig = FontStore.ensureReady(context)
                val compiled =
                    compileWithFontFallback(context, toCompile, jobDir, fontConfig, continueOnErrors)
                val result = withIndex(compiled, jobDir, toCompile, fontConfig, continueOnErrors)
                val extraNotes = buildList {
                    // Tectonic fährt tex → bibtex/biber → tex, aber KEIN makeindex.
                    // `document.idx` entsteht also, `document.ind` nie – das
                    // Stichwortverzeichnis bliebe still leer, obwohl der Compile
                    // „fertig" meldet. Auf dem Tab S5e nachgewiesen (31.07.2026).
                    if (File(jobDir, "document.idx").exists() &&
                        !File(jobDir, "document.ind").exists()
                    ) {
                        add(context.getString(R.string.note_index_not_built))
                    }
                    if (caseFixed.isNotEmpty()) {
                        add(context.getString(R.string.note_file_case_fixed, caseFixed.joinToString(", ")))
                    }
                    if (epsResult.figures.isNotEmpty()) {
                        add(
                            context.getString(
                                R.string.note_eps_placeholder,
                                epsResult.figures.joinToString(", "),
                            ),
                        )
                    }
                }
                if (extraNotes.isEmpty()) result else result.copy(notes = result.notes + extraNotes)
            } catch (t: UnsatisfiedLinkError) {
                // Alte .so ohne tectonicCompileToFile → freundlich erklären statt crashen.
                CompileResult.nativeUnavailable(t)
            } catch (t: Throwable) {
                CompileResult.nativeUnavailable(t)
            }
        }

    /** Wie viele Ersatz-Durchläufe höchstens – deckt mehrere fehlende Schriften ab. */
    private const val MAX_FONT_FALLBACK_ROUNDS = 5

    /**
     * Kompiliert und ersetzt dabei fehlende Schriften, statt am ersten Fehler
     * stehen zu bleiben: Meldet die Engine „font X cannot be found", tritt
     * [FontFallback] an, der Quelltext der **Compile-Kopie** wird umgeschrieben und
     * erneut kompiliert. Jede Ersetzung landet als Hinweis im Ergebnis, damit der
     * Nutzer weiß, dass das PDF nicht mit seiner Wunschschrift gesetzt ist.
     *
     * Kann die Schrift nicht ersetzt werden (Name steht in keinem `\set*font{…}`,
     * etwa weil eine Dokumentklasse sie intern anfordert), bleibt es beim Ergebnis
     * des letzten Laufs – dann greift die erklärende Meldung aus
     * [withFriendlyFontErrors].
     */
    private fun compileWithFontFallback(
        context: Context,
        source: String,
        jobDir: File,
        fontConfig: String,
        continueOnErrors: Boolean,
    ): CompileResult {
        var current = source
        val notes = mutableListOf<String>()

        // Bekannte PC-Schriften ALLE auf einmal ersetzen, bevor die Engine startet.
        // Reaktiv erführe man immer nur die eine Schrift, an der es gerade scheitert –
        // eine Vorlage mit vier verdrahteten Windows-Schriften bräuchte vier komplette
        // Durchläufe (chinesische Wettbewerbsvorlage: Times New Roman, SimSun, simkai,
        // Arial). Klassen- und Paketdateien im Arbeitsverzeichnis gehören dazu.
        val (batchSource, batchSubs) = FontFallback.replaceAllKnown(current)
        current = batchSource
        val allSubs = batchSubs + FontFallback.replaceAllKnownInFiles(jobDir)
        if (allSubs.isNotEmpty()) {
            notes += context.getString(
                R.string.font_fallback_batch,
                allSubs.joinToString(", ") { "${it.requested} → ${it.replacement}" },
            )
        }

        // Dokumente für pdfLaTeX vorab XeTeX-tauglich machen (inputenc, Treiberoptionen).
        // Das ist deterministisch, kostet also keinen Extra-Durchlauf – anders als die
        // Schrift-Ersetzung, die erst auf einen Fehler der Engine reagieren kann.
        EngineCompat.adapt(current)?.let { (adapted, what) ->
            current = adapted
            what.forEach { a ->
                notes += context.getString(
                    when (a) {
                        EngineCompat.Adaptation.INPUTENC_ENTFERNT -> R.string.compat_inputenc_removed
                        EngineCompat.Adaptation.TREIBER_UMGESTELLT -> R.string.compat_driver_rewritten
                    },
                )
            }
        }
        var result = runEngine(current, jobDir, fontConfig, continueOnErrors)
        var rounds = 0
        while (rounds++ < MAX_FONT_FALLBACK_ROUNDS) {
            val missing = result.errors
                .firstNotNullOfOrNull { LatexLog.fontNotFoundName(it.message) } ?: break
            val inSource = FontFallback.replaceMissingFont(current, missing)
            val replacement = if (inSource != null) {
                current = inSource.first
                inSource.second
            } else {
                // Steht die Schrift nicht im Hauptdokument, verdrahtet sie meist die
                // Dokumentklasse (siehe [FontFallback.replaceMissingFontInFiles]).
                FontFallback.replaceMissingFontInFiles(jobDir, missing) ?: break
            }
            notes += context.getString(R.string.font_fallback_note, missing, replacement)
            result = runEngine(current, jobDir, fontConfig, continueOnErrors)
        }
        // Fehler gegen den ZULETZT kompilierten Quelltext auswerten, Hinweise anhängen.
        return withFriendlyEngineError(context, withFriendlyFontErrors(context, result)).copy(notes = notes)
    }

    /**
     * Baut das Stichwortverzeichnis und kompiliert einmal nach.
     *
     * LaTeX schreibt seine Einträge nach `document.idx`, braucht daraus aber ein
     * `document.ind` – erzeugt normalerweise vom Programm `makeindex`, das es auf
     * dem Gerät nicht gibt. [MakeIndex] übernimmt das; der zusätzliche Durchlauf
     * ist nötig, damit `\printindex` die fertige Datei einliest.
     *
     * Kostet nur etwas, wenn das Dokument überhaupt einen Index hat – und nur beim
     * ersten Mal: Ändert sich nichts, bleibt die vorhandene `.ind` stehen und es
     * wird nicht neu kompiliert.
     */
    private fun withIndex(
        result: CompileResult,
        jobDir: File,
        source: String,
        fontConfig: String,
        continueOnErrors: Boolean,
    ): CompileResult {
        val idx = File(jobDir, "document.idx")
        if (!idx.exists()) return result
        val built = runCatching { MakeIndex.build(idx.readText()) }.getOrNull() ?: return result
        val ind = File(jobDir, "document.ind")
        if (ind.exists() && runCatching { ind.readText() }.getOrNull() == built) return result
        val withIndex = runCatching {
            ind.writeText(built)
            runEngine(source, jobDir, fontConfig, continueOnErrors)
        }.getOrNull() ?: return result

        // Sicherheitsnetz: Ein selbstgebautes Verzeichnis darf ein Dokument, das
        // vorher lief, niemals kaputt machen. Kommt der Lauf MIT Index schlechter
        // heraus als der ohne, wird die .ind verworfen und das alte Ergebnis
        // behalten – dann greift der Hinweis „Verzeichnis bleibt leer".
        // (Bei lshort mit eigener .ist-Stildatei genau so passiert.)
        if (result.ok && !withIndex.ok) {
            runCatching { ind.delete() }
            return result
        }
        return withIndex
    }

    /** Ein einzelner nativer Lauf; Fehler werden gegen [source] ausgewertet. */
    private fun runEngine(
        source: String,
        jobDir: File,
        fontConfig: String,
        continueOnErrors: Boolean,
    ): CompileResult {
        val json = RustBridge.tectonicCompileToFile(
            source, jobDir.absolutePath, localWallClockEpoch(), fontConfig, continueOnErrors,
        )
        return CompileResult.fromJson(json, source)
    }

    /**
     * Ersetzt fontspec-Rohtext („The font "X" cannot be found") durch eine Meldung,
     * mit der man etwas anfangen kann: welche Familien mitgeliefert sind, wohin
     * eigene Schriften gehören und – falls zutreffend – dass eben abgelegte
     * Schriften erst nach einem App-Neustart gefunden werden (siehe FontStore).
     */
    private fun withFriendlyFontErrors(context: Context, result: CompileResult): CompileResult {
        if (result.errors.isEmpty()) return result
        var changed = false
        val errors = result.errors.map { err ->
            val font = LatexLog.fontNotFoundName(err.message) ?: return@map err
            changed = true
            err.copy(message = fontNotFoundMessage(context, font))
        }
        return if (changed) result.copy(errors = errors) else result
    }

    /**
     * Übersetzt einen abgefangenen Engine-Panic (Präfix `PANIC:`, siehe die
     * catch_unwind-Klammer in `rust/src/lib.rs`) in eine Meldung, mit der man etwas
     * anfangen kann. Häufigster Fall: der erste Compile kann das TeX-Bundle nicht
     * laden, weil kein Netz da ist – vorher riss dieser Panic den ganzen Prozess mit.
     * Der technische Wortlaut bleibt im Log, damit er zur Fehlersuche nicht verloren ist.
     */
    private fun withFriendlyEngineError(context: Context, result: CompileResult): CompileResult {
        val raw = result.engineError
        if (result.ok || !raw.startsWith("PANIC:")) return result
        val bundleTrouble = BUNDLE_HINTS.any { raw.contains(it, ignoreCase = true) }
        val message = context.getString(
            if (bundleTrouble) R.string.error_bundle_unreachable else R.string.error_engine_internal,
        )
        return result.copy(
            errors = listOf(CompileError(null, message)),
            log = if (result.log.isBlank()) raw else result.log + "\n" + raw,
        )
    }

    /** Wortfetzen, die einen fehlgeschlagenen Bundle-/Netzzugriff verraten. */
    private val BUNDLE_HINTS = listOf(
        "bundle", "Paket-Bundle", "http", "url", "dns", "network", "connect", "tls", "resolve",
    )

    private fun fontNotFoundMessage(context: Context, font: String): String = buildString {
        append(context.getString(R.string.error_font_not_found, font))
        FontStore.bundledFamilies(context).takeIf { it.isNotEmpty() }?.let { families ->
            append(' ')
            append(context.getString(R.string.error_font_bundled, families.joinToString(", ")))
        }
        FontStore.userDir(context)?.let { dir ->
            append(' ')
            append(context.getString(R.string.error_font_own_folder, dir.absolutePath))
        }
        FontStore.userFontFiles(context).takeIf { it.isNotEmpty() }?.let { files ->
            append(' ')
            append(context.getString(R.string.error_font_user_files, files.joinToString(", ")))
        }
        if (FontStore.fontSetChangedSinceStart(context)) {
            append(' ')
            append(context.getString(R.string.error_font_restart))
        }
    }

    /**
     * Steht zwischen `\begin{document}` und `\end{document}` nur Leerraum oder
     * Kommentar? Dann entsteht keine Seite (JVM-testbar).
     *
     * Bewusst konservativ: Gibt es keinen `\begin{document}`, wird nichts
     * behauptet – das kann eine Klassendatei oder ein eingebundener Teil sein.
     */
    fun hasEmptyBody(source: String): Boolean {
        val begin = BEGIN_DOCUMENT.find(source) ?: return false
        val rest = source.substring(begin.range.last + 1)
        val end = END_DOCUMENT.find(rest)
        val body = if (end != null) rest.substring(0, end.range.first) else rest
        return body.lineSequence()
            .map { it.substringBefore('%').trim() }
            .none { it.isNotEmpty() }
    }

    private val BEGIN_DOCUMENT = Regex("""\\begin\s*\{\s*document\s*\}""")
    private val END_DOCUMENT = Regex("""\\end\s*\{\s*document\s*\}""")

    /**
     * Reine Erkennung (JVM-testbar): 1-basierte Zeile des biblatex-Ladens ohne
     * bibtex-Backend, sonst null. Kommentierte Zeilen zählen nicht; `%` mitten
     * in der Zeile schneidet ab (ein escaptes `\%` ist in einer
     * usepackage-Zeile praktisch nie relevant).
     */
    fun findBiberLine(source: String): Int? {
        source.lineSequence().forEachIndexed { index, raw ->
            val line = raw.substringBefore('%')
            val match = BIBLATEX_USEPACKAGE.find(line) ?: return@forEachIndexed
            val options = match.groupValues[1]
            if (!BACKEND_BIBTEX.containsMatchIn(options)) return index + 1
        }
        return null
    }

    /**
     * `\usepackage[...]{biblatex}` — Optionen (Gruppe 1) können fehlen.
     * ⚠️ Alle literalen Klammern explizit maskiert: Androids ICU-Regex-Engine
     * lehnt `[^]]` und unmaskierte `}`/`]` ab (Desktop-Java toleriert beides —
     * Unit-Tests grün, App crasht → auf dem Gerät getestet, 12.07.2026).
     */
    private val BIBLATEX_USEPACKAGE =
        Regex("""\\usepackage\s*(?:\[([^\]]*)\])?\s*\{\s*biblatex\s*\}""")
    private val BACKEND_BIBTEX =
        Regex("""backend\s*=\s*bibtex""")

    /**
     * Aktuelle LOKALE Wanduhrzeit als „UTC-kodierte" Epoch-Sekunden: die echte
     * Epoch plus den Zeitzonen-Offset des Geräts. Die native Seite kompiliert mit
     * TZ=UTC und interpretiert diese Sekunden direkt als Datum/Uhrzeit — so zeigt
     * `\today` das lokale Datum statt „1. Januar 1970" (Tectonic-Default) oder
     * einer um den Offset verschobenen UTC-Zeit.
     */
    private fun localWallClockEpoch(): Long {
        val nowMillis = System.currentTimeMillis()
        val offsetMillis = java.util.TimeZone.getDefault().getOffset(nowMillis)
        return (nowMillis + offsetMillis) / 1000L
    }

    /**
     * Bibliografie-Zwischendateien des letzten Laufs löschen, bevor neu gebaut wird.
     * Nötig, damit eine veraltete `.bbl` (z.B. aus einem anderen Bib-System) den
     * nächsten Lauf nicht bricht: biblatex bricht sonst mit „File 'document.bbl'
     * not created by biblatex" ab.
     *
     * **Nicht** gelöscht werden `.aux`, `.toc` & Co: Tectonic läuft, bis das Ergebnis
     * stabil ist – findet es die Hilfsdateien des letzten Laufs vor, stimmen
     * Querverweise und Inhaltsverzeichnis schon im ersten Durchlauf und der zweite
     * entfällt. Genau wie latexmk auf dem PC. Sie zu löschen kostete bei jedem
     * Compile einen kompletten Extra-Durchlauf (auf dem Gerät gemessen).
     * PDF, SyncTeX, Bundle-Cache und Projektquellen bleiben ebenfalls stehen.
     *
     * Der Hauptinput heißt nativ immer `document.tex`, daher alle Namen `document.*`.
     */
    private fun cleanAuxArtifacts(jobDir: File, docUri: String?, source: String) {
        BIB_EXTENSIONS.forEach { ext -> File(jobDir, "document.$ext").delete() }
        File(jobDir, "document-blx.bib").delete() // biblatex-Kontrolldatei

        // Beim Wechsel des Dokuments gilt das Behalten nicht (siehe [documentSignature]).
        val marker = File(jobDir, ".lastdoc")
        val signature = documentSignature(docUri, source)
        if (marker.takeIf { it.exists() }?.readText() != signature) {
            REF_EXTENSIONS.forEach { ext -> File(jobDir, "document.$ext").delete() }
            runCatching { marker.writeText(signature) }
        }
    }

    /**
     * Kennung des Dokuments für die Frage „dürfen die Hilfsdateien des letzten Laufs
     * bleiben?": die Datei-Uri **plus** ein Fingerabdruck der Präambel (alles vor
     * `\begin{document}`).
     *
     * Die Präambel muss mit hinein, weil die Hilfsdateien immer `document.*` heißen:
     * Zwei verschiedene Dokumente ohne Uri (Entwürfe) – oder ein Wechsel von Klasse
     * bzw. Sprache – erbten sonst die `.aux` des anderen. babel bricht dann hart ab
     * („You haven't defined the language '*' yet"), auf dem Gerät reproduziert.
     * Beim normalen Tippen im Textteil bleibt die Präambel gleich, der Tempo-Gewinn
     * also erhalten.
     */
    internal fun documentSignature(docUri: String?, source: String): String {
        val preamble = source.substringBefore("\\begin{document}")
        return "${docUri ?: "(entwurf)"}|${preamble.hashCode()}"
    }

    /** Bibliografie-Artefakte: immer weg (veraltete `.bbl` bricht biblatex). */
    private val BIB_EXTENSIONS = listOf("bbl", "blg", "bcf", "run.xml")

    /** Verweis-/Gliederungs-Artefakte: nur beim Dokumentwechsel weg. */
    private val REF_EXTENSIONS = listOf(
        "aux", "toc", "out", "nav", "snm", "lof", "lot", "idx", "ilg", "ind",
    )
}

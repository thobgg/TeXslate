package de.bgg_home.texdroid.compile

import android.content.Context
import android.net.Uri
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
        continueOnErrors: Boolean = false,
    ): CompileResult =
        withContext(Dispatchers.IO) {
            // Issue #1 (Tester-Feedback): biblatex mit biber-Backend scheitert in
            // Tectonic mit der irreführenden Meldung „can't open path …bib". Vorab
            // erkennen und den echten Grund + die Lösung nennen.
            detectBiberUsage(context, source)?.let { return@withContext it }
            val jobDir = File(context.filesDir, "job").apply { mkdirs() }
            try {
                cleanAuxArtifacts(jobDir)
                if (projectTree != null) {
                    ProjectStore.syncToDir(context, projectTree, jobDir)
                }
                // Fonts auspacken + fonts.conf sicherstellen, damit \setmainfont{<Name>}
                // (Latin Modern / TeX Gyre / Systemfonts) per Name aufgelöst wird.
                val fontConfig = FontStore.ensureReady(context)
                val json = RustBridge.tectonicCompileToFile(
                    source, jobDir.absolutePath, localWallClockEpoch(), fontConfig,
                    continueOnErrors,
                )
                CompileResult.fromJson(json)
            } catch (t: UnsatisfiedLinkError) {
                // Alte .so ohne tectonicCompileToFile → freundlich erklären statt crashen.
                CompileResult.nativeUnavailable(t)
            } catch (t: Throwable) {
                CompileResult.nativeUnavailable(t)
            }
        }

    /**
     * Erkennt `\usepackage{biblatex}` ohne `backend=bibtex` (biblatex' Default
     * ist biber — und biber ist Perl, das Tectonic nicht ausführen kann).
     * Liefert dann ein erklärendes Fehl-Ergebnis mit Sprung zur Zeile, sonst null.
     */
    private fun detectBiberUsage(context: Context, source: String): CompileResult? =
        findBiberLine(source)?.let { line ->
            CompileResult.preflightError(
                line = line,
                message = context.getString(de.bgg_home.texdroid.R.string.error_biber_unsupported),
            )
        }

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
     * Zwischen-/Hilfsdateien des letzten Compile-Laufs löschen, bevor neu gebaut
     * wird. Nötig, damit eine veraltete `.bbl` (z.B. aus einem anderen Bib-System)
     * den nächsten Lauf nicht bricht: biblatex bricht sonst mit „File 'document.bbl'
     * not created by biblatex" ab. PDF, SyncTeX, das Tectonic-Bundle/den Cache und
     * die Projektquellen lassen wir bewusst stehen (Neuaufbau wäre teuer).
     *
     * Der Hauptinput heißt nativ immer `document.tex`, daher alle Namen `document.*`.
     */
    private fun cleanAuxArtifacts(jobDir: File) {
        AUX_EXTENSIONS.forEach { ext -> File(jobDir, "document.$ext").delete() }
        File(jobDir, "document-blx.bib").delete() // biblatex-Kontrolldatei
    }

    private val AUX_EXTENSIONS = listOf(
        "aux", "bbl", "blg", "bcf", "run.xml",
        "toc", "out", "nav", "snm", "lof", "lot",
        "idx", "ilg", "ind",
    )
}

package de.bgg_home.texdroid.compile

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.bgg_home.texdroid.RustBridge
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * MACHBARKEITS-SONDEN für biber (nicht die reguläre Test-Suite).
 *
 * Ziel: nicht „biber funktioniert" behaupten, sondern die **Realisierbarkeit
 * belegbar machen** — konkrete, auf dem Gerät ausgeführte Messpunkte, die die
 * offenen Technik-Fragen der biber-Notiz mit Evidenz füllen statt mit Vermutung.
 *
 * Läuft **nur nativ** (Tectonic/XeTeX ist eine JNI-Rust-Lib) → Instrumentation-
 * Test auf dem S25 Ultra. Ruft [RustBridge.tectonicCompileToFile] DIREKT, also
 * am Preflight ([LatexCompiler.detectBiberUsage]) vorbei — der Preflight ist ja
 * gerade das, was wir hier probeweise umgehen wollen.
 *
 * Erster Lauf lädt Tectonics Bundle (Netzwerk) → kann dauern; Gerät online halten.
 *
 * Auswertung: `adb logcat -s BIBER_PROBE` liefert die Diagnostik der drei Sonden.
 *
 * Ausführen (Gerät verbunden):
 *   ./gradlew :app:connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *        de.bgg_home.texdroid.compile.BiberFeasibilityProbe
 */
@RunWith(AndroidJUnit4::class)
class BiberFeasibilityProbe {

    private val tag = "BIBER_PROBE"

    /** Frischer, beschreibbarer Job-/Cache-Ordner; Aufrufer darf Dateien vorlegen. */
    private fun freshJobDir(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return File(ctx.cacheDir, "biber-probe/$name").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private fun compile(jobDir: File, src: String): CompileResult {
        // continueOnErrors=true: der biber-Fall bricht mit Fehler ab; wir wollen
        // die Engine trotzdem durchlaufen lassen, um die Artefakte zu inspizieren.
        val json = RustBridge.tectonicCompileToFile(
            src, jobDir.absolutePath, 0L, "", /* continueOnErrors = */ true,
        )
        return CompileResult.fromJson(json)
    }

    private fun logHead(label: String, text: String, maxLines: Int = 40) {
        Log.i(tag, "----- $label (erste $maxLines Zeilen) -----")
        text.lineSequence().take(maxLines).forEach { Log.i(tag, it) }
    }

    /** Das ENDE des Logs — dort steht der Abbruch-Kontext (os error 2). */
    private fun logTail(label: String, text: String, maxLines: Int = 60) {
        val lines = text.lines()
        val from = (lines.size - maxLines).coerceAtLeast(0)
        Log.i(tag, "----- $label (letzte $maxLines von ${lines.size} Zeilen) -----")
        lines.drop(from).forEach { Log.i(tag, it) }
    }

    private val BIBER_DOC = """
        \documentclass{article}
        \usepackage[backend=biber]{biblatex}
        \addbibresource{refs.bib}
        \begin{document}
        Test~\cite{knuth1984}.
        \printbibliography
        \end{document}
    """.trimIndent()

    private val REFS_BIB = """
        @book{knuth1984,
          author    = {Knuth, Donald E.},
          title     = {The {\TeX}book},
          year      = {1984},
          publisher = {Addison-Wesley},
        }
    """.trimIndent()

    /**
     * SONDE A (entscheidend): Erzeugt Tectonics TeX-Engine für ein
     * `backend=biber`-Dokument die Steuerdatei `document.bcf`?
     *
     * Die `.bcf` ist genau der Input, den biber liest. Existiert sie:
     *  → Die TeX-Seite ist biber-bereit; die EINZIGE Lücke ist das biber-Binary
     *    (Perl) selbst → Machbarkeit reduziert sich auf „biber für arm64 bauen".
     * Fehlt sie:
     *  → Tectonic erzeugt biber-Input nicht → deutlich tiefere Baustelle.
     */
    @Test
    fun sondeA_engineErzeugtBcfSteuerdatei() {
        val jobDir = freshJobDir("A-bcf")
        File(jobDir, "refs.bib").writeText(REFS_BIB)

        val result = compile(jobDir, BIBER_DOC)

        val bcf = File(jobDir, "document.bcf")
        Log.i(tag, "SONDE A: ok=${result.ok} bcf.exists=${bcf.exists()} " +
            "bcf.size=${if (bcf.exists()) bcf.length() else -1}")
        Log.i(tag, "SONDE A: verzeichnis=${jobDir.list()?.sorted()}")
        logHead("SONDE A .bcf", if (bcf.exists()) bcf.readText() else "(fehlt)")
        logHead("SONDE A engine-log", result.log)

        // Diagnose-Sonde: kein harter Assert (soll die Suite nicht rot färben),
        // sondern ein klares Verdikt ins Log für die menschliche Einschätzung.
        val verdictA = if (bcf.exists() && bcf.length() > 0) {
            "GRÜN — .bcf erzeugt; TeX-Seite biber-bereit, Lücke = biber-Binary (arm64)."
        } else {
            "ROT — keine .bcf; erster biber-Lauf bricht ab, bevor Tectonic den " +
                "biber-Input schreibt. Realisierung weiter entfernt als 'nur Binary'."
        }
        Log.i(tag, "SONDE A VERDIKT: $verdictA")
    }

    /**
     * SONDE B (differenziell, Diagnostik): Konsumiert die Engine eine
     * vorgelegte `document.bbl` (die biber sonst erzeugen würde)?
     *
     * Ohne `.bbl` bricht biblatex mit „File 'document.bbl' not created by
     * biblatex" ab. Legen wir eine `.bbl` vor und dieser spezifische Abbruch
     * verschwindet, ist der Pipeline-Schwanz (bbl → PDF) intakt → Machbarkeit
     * hängt nur noch am biber-Schritt dazwischen.
     *
     * Achtung: eine handgeschriebene `.bbl` ist an die biblatex-Kontrollversion
     * gebunden; ein PDF-Erfolg ist NICHT garantiert. Darum weiche Auswertung +
     * volle Log-Ausgabe zur menschlichen Einschätzung.
     */
    @Test
    fun sondeB_engineKonsumiertVorgelegteBbl() {
        // (1) Referenzlauf OHNE .bbl → erwarteter „not created by biblatex"-Abbruch.
        val without = freshJobDir("B-ohne-bbl")
        File(without, "refs.bib").writeText(REFS_BIB)
        val rWithout = compile(without, BIBER_DOC)
        val abortMarker = "not created by biblatex"
        val sawAbortWithout = rWithout.log.contains(abortMarker)
        Log.i(tag, "SONDE B/ohne: ok=${rWithout.ok} sah-Abbruch=$sawAbortWithout")

        // (2) Lauf MIT vorgelegter .bbl (simuliert biber-Output).
        val with = freshJobDir("B-mit-bbl")
        File(with, "refs.bib").writeText(REFS_BIB)
        File(with, "document.bbl").writeText(MINIMAL_BBL)
        val rWith = compile(with, BIBER_DOC)
        val sawAbortWith = rWith.log.contains(abortMarker)

        // Diskriminator H1 vs H2: taucht JETZT (Lauf kommt weiter) die .bcf auf?
        //  bcf da  → in Sonde A fehlte sie nur wegen des frühen Abbruchs (H1).
        //  bcf weg → Engine flusht die .bcf gar nicht auf die Platte (H2).
        val bcfWith = File(with, "document.bcf")
        Log.i(tag, "SONDE B/mit: ok=${rWith.ok} sah-Abbruch=$sawAbortWith " +
            "pdf=${rWith.pdfPath.ifEmpty { "(keins)" }} " +
            "bcf.exists=${bcfWith.exists()} verzeichnis=${with.list()?.sorted()}")
        logHead("SONDE B/mit engine-log", rWith.log)

        // Diagnose-Sonde: geloggtes Verdikt statt hartem Assert.
        val verdictB = when {
            rWith.ok ->
                "GRÜN — PDF mit vorgelegter .bbl; Pipeline-Schwanz (bbl→PDF) intakt."
            !sawAbortWith ->
                "GELB — kein 'not created by biblatex' mehr; .bbl wird eingelesen, " +
                    "aber kein PDF. Ursache weiter im Log prüfen."
            else ->
                "ROT — 'not created by biblatex' bleibt; die (fremd erzeugte) .bbl " +
                    "wird von biblatex abgelehnt (Kontrollversion/Checksumme)."
        }
        Log.i(tag, "SONDE B VERDIKT: $verdictB")
    }

    /**
     * SONDE C (reine Diagnostik): Rahmendaten für die menschliche Einschätzung —
     * eingebettete Tectonic-Version und das vollständige biber-Fehlerbild.
     * Schlägt nie fehl; dient dem Sammeln der Fakten für die biber-Notiz.
     */
    @Test
    fun sondeC_rahmendatenFuerEinschaetzung() {
        Log.i(tag, "SONDE C: tectonicVersion=${RustBridge.tectonicVersion()}")

        val jobDir = freshJobDir("C-diagnose")
        File(jobDir, "refs.bib").writeText(REFS_BIB)
        val result = compile(jobDir, BIBER_DOC)

        Log.i(tag, "SONDE C: ok=${result.ok} engineError=${result.engineError.take(300)}")
        Log.i(tag, "SONDE C: erkannte Fehler=${result.errors.map { it.message }}")
        // .blg (biber-Log) existiert erwartungsgemäß NICHT (biber läuft nicht) —
        // gerade dieses Fehlen ist ein Datenpunkt.
        Log.i(tag, "SONDE C: .blg vorhanden=${File(jobDir, "document.blg").exists()}")
        // Das Log-ENDE trägt den Abbruch-Kontext: welche Datei/Operation löst
        // den fatalen os-error-2 aus (fehlender .bbl-\input? .bcf-\openout?).
        logTail("SONDE C log-ENDE", result.log, maxLines = 60)
    }

    /**
     * Minimal-`.bbl` im biblatex-Format (eine Buch-Referenz). Bewusst schlank;
     * dient nur der Differenzialfrage aus Sonde B, nicht einem hübschen PDF.
     * Bei Kontrollversions-Mismatch meldet biblatex das im Log (→ Sonde B liest es).
     */
    private val MINIMAL_BBL = """
        \begin{refsection}[0]
        \datalist[entry]{none/global//global/global}
          \entry{knuth1984}{book}{}
            \name{author}{1}{}{%
              {{hash=}{%
                 family={Knuth},
                 familyi={K\bibinitperiod},
                 given={Donald\bibnamedelima E\bibinitperiod},
                 giveni={D\bibinitperiod\bibinitdelim E\bibinitperiod}}}%
            }
            \field{sortinit}{K}
            \field{labeltitlesource}{title}
            \field{title}{The \TeX book}
            \field{year}{1984}
            \field{labelnamesource}{author}
          \endentry
        \enddatalist
        \end{refsection}
    """.trimIndent()
}

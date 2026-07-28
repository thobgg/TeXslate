package de.bgg_home.texdroid.compile

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.bgg_home.texdroid.BuildConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Produktionspfad-Test (KEINE Sonde): ruft das echte [LatexCompiler.compile] mit
 * einem biblatex+backend=biber-Dokument. Prüft, dass die **thesis**-Edition die
 * biber-Runtime via [BiberRuntime] aufsetzt und Tectonic tex→biber→tex fährt →
 * PDF mit aufgelöster Bibliografie. (Die frühere RealBiberProbe verdrahtete das
 * biber-Setup noch selbst; hier läuft die reale App-Logik.)
 *
 * Nur sinnvoll auf `thesisDebug` (HAS_BIBER=true, Runtime in den App-Assets/jniLibs);
 * auf `coreDebug` wird der Test übersprungen (assumeTrue).
 *
 *   ./gradlew :app:connectedThesisDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.class=\
 *      de.bgg_home.texdroid.compile.BiberProductionTest
 * Auswertung zusätzlich: `adb logcat -s BIBER_PROD BiberRuntime`.
 */
@RunWith(AndroidJUnit4::class)
class BiberProductionTest {

    private val tag = "BIBER_PROD"

    private val BIBER_DOC = """
        \documentclass{article}
        \usepackage[backend=biber]{biblatex}
        \addbibresource{refs.bib}
        \begin{document}
        Siehe~\cite{knuth1984} und~\cite{lamport1994}.
        \printbibliography
        \end{document}
    """.trimIndent()

    private val REFS_BIB = """
        @book{knuth1984, author={Knuth, Donald E.}, title={The {\TeX}book}, year={1984}, publisher={Addison-Wesley}}
        @book{lamport1994, author={Lamport, Leslie}, title={{\LaTeX}: A Document Preparation System}, year={1994}, publisher={Addison-Wesley}}
    """.trimIndent()

    @Test
    fun thesisEdition_biberCompilesToPdf() {
        assumeTrue("nur thesis-Edition (HAS_BIBER)", BuildConfig.HAS_BIBER)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // refs.bib ins Arbeitsverzeichnis legen (wie eine Projektdatei), damit
        // \addbibresource{refs.bib} auflöst. compile() räumt nur document.*-Aux weg.
        val jobDir = File(ctx.filesDir, "job").apply { mkdirs() }
        File(jobDir, "refs.bib").writeText(REFS_BIB)

        val result = runBlocking { LatexCompiler.compile(ctx, BIBER_DOC) }

        val pdf = File(result.pdfPath)
        Log.i(tag, "ok=${result.ok} pdf=${result.pdfPath} exists=${pdf.exists()} " +
            "size=${if (pdf.exists()) pdf.length() else -1}")
        Log.i(tag, "engineError=${result.engineError.take(400)}")

        assertTrue(
            "biber-Compile fehlgeschlagen: ok=${result.ok} err=${result.engineError.take(200)}",
            result.ok && pdf.exists() && pdf.length() > 0,
        )
        Log.i(tag, "GRÜN: thesis-Edition kompiliert biblatex+biber produktiv zu PDF.")
    }
}

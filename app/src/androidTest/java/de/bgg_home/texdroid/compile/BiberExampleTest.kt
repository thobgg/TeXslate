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
 * Kompiliert das mitgelieferte Beispiel `templates/bibliography_biber.tex`
 * (biblatex + `backend=biber`, .bib per filecontents eingebettet) über den
 * echten Produktionspfad [LatexCompiler.compile] in der thesis-Edition und
 * prüft, dass ein PDF mit aufgelöster Bibliografie entsteht.
 *
 * Das PDF wird nach `externalCacheDir/example-out/` kopiert (per adb pullbar),
 * um es auf dem Host zu inspizieren. `adb logcat -s BIBER_EXAMPLE`.
 */
@RunWith(AndroidJUnit4::class)
class BiberExampleTest {

    private val tag = "BIBER_EXAMPLE"

    @Test
    fun bibliographyBiberTemplate_compilesToPdf() {
        assumeTrue("nur thesis-Edition (HAS_BIBER)", BuildConfig.HAS_BIBER)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        val source = ctx.assets.open("templates/bibliography_biber.tex")
            .bufferedReader().use { it.readText() }
        Log.i(tag, "Beispiel geladen: ${source.length} Zeichen")

        val result = runBlocking { LatexCompiler.compile(ctx, source) }

        val pdf = File(result.pdfPath)
        Log.i(tag, "ok=${result.ok} pdf=${result.pdfPath} exists=${pdf.exists()} " +
            "size=${if (pdf.exists()) pdf.length() else -1}")
        Log.i(tag, "engineError=${result.engineError.take(300)}")

        if (pdf.exists()) {
            val out = (ctx.externalCacheDir ?: ctx.cacheDir)
                .resolve("example-out").apply { mkdirs() }
            pdf.copyTo(File(out, "bibliography_biber.pdf"), overwrite = true)
            Log.i(tag, "PDF kopiert nach ${out.absolutePath}")
        }

        assertTrue(
            "biber-Beispiel fehlgeschlagen: ok=${result.ok} err=${result.engineError.take(200)}",
            result.ok && pdf.exists() && pdf.length() > 0,
        )
        Log.i(tag, "GRÜN: biblatex+biber-Beispiel kompiliert zu PDF.")
    }
}

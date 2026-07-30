package de.bgg_home.texdroid.compile

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Echter Compile über [LatexCompiler] (Produktionspfad, keine Sonde): PC-Schriften
 * dürfen den Lauf nicht mehr abbrechen.
 *
 *  • `\setmainfont{Arial}` — auf einem Tablet nie vorhanden → muss ersetzt werden,
 *    PDF muss entstehen, und das Ergebnis muss den Hinweis mitbringen.
 *  • `\newfontfamily{DejaVu Sans}` — liegt seit dem DejaVu-Bundle bei → muss OHNE
 *    Ersatz durchlaufen (sonst wäre das mitgelieferte Set nicht auffindbar).
 */
@RunWith(AndroidJUnit4::class)
class FontFallbackCompileTest {

    private val tag = "FONT_FALLBACK"
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun doc(body: String) = """
        \documentclass{article}
        \usepackage{fontspec}
        $body
        \begin{document}
        Schriftprobe.
        \end{document}
    """.trimIndent()

    @Test
    fun fehlendePcSchrift_wirdErsetzt_undPdfEntsteht() {
        val r = runBlocking { LatexCompiler.compile(ctx, doc("\\setmainfont{Arial}")) }
        val pdf = File(r.pdfPath)
        Log.i(tag, "Arial: ok=${r.ok} pdf=${pdf.exists()} notes=${r.notes} err=${r.engineError.take(120)}")
        assertTrue(
            "Fehlende PC-Schrift darf den Compile nicht abbrechen: ok=${r.ok} err=${r.engineError.take(160)}",
            r.ok && pdf.exists() && pdf.length() > 0,
        )
        assertTrue(
            "Ersetzung muss als Hinweis gemeldet werden, war: ${r.notes}",
            r.notes.any { it.contains("Arial") && it.contains("Heros") },
        )
    }

    @Test
    fun mitgelieferteSchrift_brauchtKeinenErsatz() {
        val r = runBlocking {
            LatexCompiler.compile(ctx, doc("\\newfontfamily\\symbfont{DejaVu Sans}"))
        }
        val pdf = File(r.pdfPath)
        Log.i(tag, "DejaVu: ok=${r.ok} pdf=${pdf.exists()} notes=${r.notes} err=${r.engineError.take(120)}")
        assertTrue(
            "DejaVu Sans ist mitgeliefert und muss gefunden werden: ok=${r.ok} " +
                "notes=${r.notes} err=${r.engineError.take(160)}",
            r.ok && pdf.exists() && r.notes.isEmpty(),
        )
    }
}

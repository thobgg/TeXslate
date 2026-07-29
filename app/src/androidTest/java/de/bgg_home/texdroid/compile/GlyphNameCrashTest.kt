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
 * Regressionstest für den nativen `print_glyph_name`-Crash (XeTeX): die Funktion
 * gab einen INNEREN Zeiger frei (die Druckschleife schob `s` weiter, dann
 * `freeGlyphName(s)`), was Androids Scudo-Allocator als „misaligned pointer when
 * deallocating" mit SIGABRT quittierte — jeder Compile, der `\XeTeXglyphname`
 * erreichte, riss die App mit. Fix in tectonic_engine_xetex (xetex-ext.c).
 *
 * Dieses Dokument ruft `\XeTeXglyphname` direkt auf einer gebündelten OTF-Schrift
 * (Latin Modern Roman) auf → früher sofortiger Crash, jetzt sauberes PDF.
 */
@RunWith(AndroidJUnit4::class)
class GlyphNameCrashTest {

    private val tag = "GLYPH_CRASH"

    private val DOC = """
        \documentclass{article}
        \usepackage{fontspec}
        \setmainfont{Latin Modern Roman}
        \begin{document}
        % Löst print_glyph_name über den aktuellen OTF-Font aus (Crash-Auslöser):
        \edef\gn{\XeTeXglyphname\font 5 }
        Glyph 5 = \texttt{\gn}.
        \end{document}
    """.trimIndent()

    @Test
    fun xeTeXglyphname_doesNotCrash_andProducesPdf() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val r = runBlocking { LatexCompiler.compile(ctx, DOC) }
        val pdf = File(r.pdfPath)
        Log.i(tag, "ok=${r.ok} pdf=${r.pdfPath} exists=${pdf.exists()} " +
            "size=${if (pdf.exists()) pdf.length() else -1} err=${r.engineError.take(160)}")
        // Der Test-Prozess überlebt = kein nativer Abort mehr. Zusätzlich PDF erwartet.
        assertTrue(
            "print_glyph_name-Pfad muss ohne Crash zum PDF führen: ok=${r.ok} err=${r.engineError.take(120)}",
            r.ok && pdf.exists() && pdf.length() > 0,
        )
        Log.i(tag, "GRÜN: \\XeTeXglyphname kompiliert ohne Crash.")
    }
}

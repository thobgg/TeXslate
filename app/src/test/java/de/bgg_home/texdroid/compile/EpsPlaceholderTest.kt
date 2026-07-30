package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für den EPS-Platzhalter ([EpsPlaceholder]). */
class EpsPlaceholderTest {

    private val label = "EPS-Abbildung"

    @Test
    fun epsInclude_wirdErsetzt_undMakroDefiniert() {
        val src = "\\documentclass{article}\n\\begin{document}\n\\includegraphics[width=8cm]{bild.eps}\n\\end{document}\n"
        val r = EpsPlaceholder.withPlaceholders(src, listOf("bild.eps"), label)
        val out = r.rewritten!!
        assertEquals(listOf("bild.eps"), r.figures)
        assertTrue("Platzhalter gesetzt", out.contains("\\TeXslateEpsPlaceholder{bild.eps}"))
        assertTrue("Makro definiert", out.contains("\\providecommand{\\TeXslateEpsPlaceholder}"))
        assertTrue("includegraphics ist weg", !out.contains("\\includegraphics"))
    }

    @Test
    fun zeilenzahl_bleibtGleich() {
        val src = "\\documentclass{article}\n\\begin{document}\n\\includegraphics{a.eps}\n\\end{document}\n"
        val out = EpsPlaceholder.withPlaceholders(src, listOf("a.eps"), label).rewritten!!
        assertEquals("Fehlerzeilen müssen weiter stimmen", src.lines().size, out.lines().size)
    }

    @Test
    fun endungsloseReferenz_wirdErkannt() {
        // REVTeX/APS-Schreibweise: \includegraphics{fig_1}, auf der Platte fig_1.eps
        val src = "\\begin{document}\\includegraphics[width=8.6cm]{fig_1}\\end{document}"
        val r = EpsPlaceholder.withPlaceholders(src, listOf("fig_1.eps"), label)
        assertEquals(listOf("fig_1.eps"), r.figures)
        assertTrue(r.rewritten!!.contains("\\TeXslateEpsPlaceholder{fig_1.eps}"))
    }

    @Test
    fun unterstrichImNamen_wirdGeschuetzt() {
        val src = "\\begin{document}\\includegraphics{fig_1.eps}\\end{document}"
        val out = EpsPlaceholder.withPlaceholders(src, listOf("fig_1.eps"), label).rewritten!!
        assertTrue("detokenize schützt _ im Textmodus", out.contains("\\detokenize"))
    }

    @Test
    fun pdfUndPngBleibenUnberuehrt() {
        val src = "\\begin{document}\\includegraphics{a.pdf}\\includegraphics{b.png}\\end{document}"
        val r = EpsPlaceholder.withPlaceholders(src, listOf("a.pdf", "b.png"), label)
        assertTrue(r.figures.isEmpty())
        assertEquals(src, r.rewritten)
    }

    @Test
    fun pdfNebenEps_bleibtUnberuehrt() {
        // LaTeX nimmt von sich aus die PDF-Fassung – vom Nutzer am Gerät bestätigt.
        val src = "\\begin{document}\\includegraphics{fig}\\end{document}"
        val r = EpsPlaceholder.withPlaceholders(src, listOf("fig.eps", "fig.pdf"), label)
        assertTrue(r.figures.isEmpty())
        assertEquals(src, r.rewritten)
    }

    @Test
    fun ohneBeginDocument_meldetUnmoeglich() {
        val src = "\\includegraphics{a.eps}"
        val r = EpsPlaceholder.withPlaceholders(src, listOf("a.eps"), label)
        assertEquals(listOf("a.eps"), r.figures)
        assertNull("Ohne \\begin{document} kein Makro → Aufrufer zeigt den Fehler", r.rewritten)
    }

    @Test
    fun mehrereAbbildungen_werdenAlleErsetzt() {
        val src = "\\begin{document}\\includegraphics{a.eps}\\includegraphics{b.eps}\\end{document}"
        val out = EpsPlaceholder.withPlaceholders(src, listOf("a.eps", "b.eps"), label).rewritten!!
        // Die Definition schreibt „…Placeholder}" – gezählt werden nur die Aufrufe „…Placeholder{".
        assertEquals(2, Regex("TeXslateEpsPlaceholder\\{").findAll(out).count())
    }
}

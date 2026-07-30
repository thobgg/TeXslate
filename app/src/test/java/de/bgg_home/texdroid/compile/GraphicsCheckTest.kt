package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für die EPS-Erkennung. Ein falsch-negatives Ergebnis kostet die App das
 * Leben (dvipdfmx-Assertion beim Folge-Compile), ein falsch-positives blockiert ein
 * Dokument, das laufen würde — beides ist hier abgedeckt.
 */
class GraphicsCheckTest {

    @Test
    fun eps_mitEndung_wirdErkannt() {
        val eps = GraphicsCheck.epsFigures(
            "\\includegraphics[width=8cm]{bild.eps}",
            listOf("bild.eps"),
        )
        assertEquals(listOf("bild.eps"), eps)
    }

    @Test
    fun eps_ohneEndung_wirdErkannt() {
        // So macht es das REVTeX/APS-Beispiel: \includegraphics{fig_1}, auf der Platte fig_1.eps
        val eps = GraphicsCheck.epsFigures(
            "\\includegraphics[width=8.6cm]{fig_1}",
            listOf("fig_1.eps", "apssamp.tex"),
        )
        assertEquals(listOf("fig_1.eps"), eps)
    }

    @Test
    fun pdfDaneben_istKeinProblem() {
        val eps = GraphicsCheck.epsFigures(
            "\\includegraphics{fig_1}",
            listOf("fig_1.eps", "fig_1.pdf"),
        )
        assertTrue("PDF-Fassung vorhanden → LaTeX nimmt die", eps.isEmpty())
    }

    @Test
    fun pngUndPdf_werdenNichtGemeldet() {
        val eps = GraphicsCheck.epsFigures(
            "\\includegraphics{a.png}\n\\includegraphics{b.pdf}\n\\includegraphics{c}",
            listOf("a.png", "b.pdf", "c.png"),
        )
        assertTrue(eps.isEmpty())
    }

    @Test
    fun mehrereEps_werdenAlleGenannt_ohneDoppelte() {
        val eps = GraphicsCheck.epsFigures(
            "\\includegraphics{fig_1}\n\\includegraphics{fig_2}\n\\includegraphics{fig_1}",
            listOf("fig_1.eps", "fig_2.eps"),
        )
        assertEquals(listOf("fig_1.eps", "fig_2.eps"), eps)
    }

    @Test
    fun sternformUndOptionen_werdenErkannt() {
        val eps = GraphicsCheck.epsFigures(
            "\\includegraphics*[angle=90,width=\\linewidth]{plot.eps}",
            listOf("plot.eps"),
        )
        assertEquals(listOf("plot.eps"), eps)
    }

    @Test
    fun ohneAbbildungen_leer() {
        assertTrue(GraphicsCheck.epsFigures("\\documentclass{article}", listOf("x.eps")).isEmpty())
    }
}

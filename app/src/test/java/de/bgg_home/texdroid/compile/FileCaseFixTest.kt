package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests für [FileCaseFix] — den Ausgleich zwischen PC-Dateisystemen (egal, wie man
 * schreibt) und Android (nicht egal).
 */
class FileCaseFixTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun job() = tmp.root

    @Test
    fun abbildungMitAndererSchreibweise_wirdAngeglichen() {
        File(job(), "Fig3_aAs0-hse.pdf").writeText("PDF")
        val fixed = FileCaseFix.fixCaseMismatches(
            "\\includegraphics[width=8cm]{Fig3_aAs0-HSE.pdf}",
            job(),
        )
        assertEquals(listOf("Fig3_aAs0-HSE.pdf"), fixed)
        assertTrue("Kopie unter dem verlangten Namen", File(job(), "Fig3_aAs0-HSE.pdf").exists())
        assertTrue("Original bleibt", File(job(), "Fig3_aAs0-hse.pdf").exists())
    }

    @Test
    fun passendeDatei_wirdNichtAngefasst() {
        File(job(), "bild.png").writeText("PNG")
        assertTrue(FileCaseFix.fixCaseMismatches("\\includegraphics{bild.png}", job()).isEmpty())
    }

    @Test
    fun endungsloseReferenz_findetDieDatei() {
        File(job(), "Kapitel1.tex").writeText("Text")
        val fixed = FileCaseFix.fixCaseMismatches("\\input{kapitel1}", job())
        assertEquals(listOf("kapitel1"), fixed)
        assertTrue(File(job(), "kapitel1.tex").exists())
    }

    @Test
    fun endungsloseReferenz_mitExakterDatei_bleibtUnberuehrt() {
        File(job(), "kapitel1.tex").writeText("Text")
        assertTrue(FileCaseFix.fixCaseMismatches("\\input{kapitel1}", job()).isEmpty())
    }

    @Test
    fun bibliografie_wirdMitgenommen() {
        File(job(), "Literatur.bib").writeText("@book{a,}")
        val fixed = FileCaseFix.fixCaseMismatches("\\addbibresource{literatur.bib}", job())
        assertEquals(listOf("literatur.bib"), fixed)
        assertTrue(File(job(), "literatur.bib").exists())
    }

    @Test
    fun unterordner_funktioniert() {
        val sub = File(job(), "bilder").apply { mkdirs() }
        File(sub, "Plot.PDF").writeText("PDF")
        val fixed = FileCaseFix.fixCaseMismatches("\\includegraphics{bilder/plot.pdf}", job())
        assertEquals(listOf("bilder/plot.pdf"), fixed)
        assertTrue(File(sub, "plot.pdf").exists())
    }

    @Test
    fun fehlendeDatei_bleibtFehlend() {
        // Nichts zu retten – der Compile soll dann normal „nicht gefunden" melden.
        assertTrue(FileCaseFix.fixCaseMismatches("\\includegraphics{gibtsnicht.pdf}", job()).isEmpty())
    }

    @Test
    fun pfadAusbruch_wirdIgnoriert() {
        assertTrue(FileCaseFix.fixCaseMismatches("\\input{../geheim}", job()).isEmpty())
    }
}

package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests für die XeTeX-Anpassung von pdfLaTeX-Dokumenten ([EngineCompat]). */
class EngineCompatTest {

    @Test
    fun inputenc_wirdEntfernt_zeilenZahlBleibt() {
        val src = "\\documentclass{beamer}\n\\usepackage[latin1]{inputenc}\n\\usepackage[T1]{fontenc}\n"
        val (out, what) = EngineCompat.adapt(src)!!
        assertTrue(!out.contains("inputenc"))
        assertTrue("fontenc bleibt", out.contains("\\usepackage[T1]{fontenc}"))
        assertEquals("Zeilenzahl darf sich nicht ändern", src.lines().size, out.lines().size)
        assertEquals(listOf(EngineCompat.Adaptation.INPUTENC_ENTFERNT), what)
    }

    @Test
    fun requirePackage_inputenc_zaehltAuch() {
        val (out, _) = EngineCompat.adapt("\\RequirePackage[utf8]{inputenc}")!!
        assertEquals("", out)
    }

    @Test
    fun pdftex_treiberoption_wirdXetex() {
        val src = "\\usepackage[pdftex, colorlinks=true]{hyperref}"
        val (out, what) = EngineCompat.adapt(src)!!
        assertEquals("\\usepackage[xetex, colorlinks=true]{hyperref}", out)
        assertEquals(listOf(EngineCompat.Adaptation.TREIBER_UMGESTELLT), what)
    }

    @Test
    fun mehrereTreiberoptionen_undDocumentclass() {
        val src = "\\documentclass[dvips]{article}\n\\usepackage[pdftex, marginparwidth=50pt]{geometry}\n"
        val (out, _) = EngineCompat.adapt(src)!!
        assertTrue(out.contains("\\documentclass[xetex]{article}"))
        assertTrue(out.contains("[xetex, marginparwidth=50pt]"))
    }

    @Test
    fun paketnameMitPdftex_bleibtUnberuehrt() {
        // pdftexcmds ist ein Paket, keine Treiberoption.
        assertNull(EngineCompat.adapt("\\usepackage{pdftexcmds}"))
    }

    @Test
    fun textImDokument_bleibtUnberuehrt() {
        assertNull(EngineCompat.adapt("Wir haben das mit pdftex und dvips gesetzt."))
    }

    @Test
    fun xetexDokument_wirdNichtAngefasst() {
        val src = "\\documentclass{article}\n\\usepackage{fontspec}\n\\setmainfont{Latin Modern Roman}\n"
        assertNull("Nichts zu tun → null, damit nichts kopiert wird", EngineCompat.adapt(src))
    }

    @Test
    fun beideEingriffe_werdenGemeldet_undNichtDoppelt() {
        val src = "\\usepackage[latin1]{inputenc}\n\\usepackage[pdftex]{hyperref}\n" +
            "\\usepackage[utf8]{inputenc}\n"
        val (_, what) = EngineCompat.adapt(src)!!
        assertEquals(
            listOf(
                EngineCompat.Adaptation.INPUTENC_ENTFERNT,
                EngineCompat.Adaptation.TREIBER_UMGESTELLT,
            ),
            what,
        )
    }
}

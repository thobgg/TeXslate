// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.compile

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

    // ── Issue #13: [T1]{fontenc} verliert unter XeTeX Zeichen jenseits Latin-1 ──

    @Test
    fun fehlendeZeichen_nurJenseitsLatin1() {
        val log = """
            Missing character: There is no š ("161) in font ec-lmr10!
            Missing character: There is no č ("10D) in font ec-lmr10!
            Missing character: There is no š ("161) in font ec-lmr10!
            Missing character: There is no ä ("E4) in font cmr10!
        """.trimIndent()
        assertEquals(listOf("š", "č"), EngineCompat.missingBeyondLatin1(log))
    }

    @Test
    fun ohneFehlendeZeichen_leer() {
        assertTrue(EngineCompat.missingBeyondLatin1("Output written on document.xdv").isEmpty())
    }

    @Test
    fun t1Fontenc_wirdEntfernt_zeilenZahlBleibt() {
        val src = "\\documentclass{letter}\n\\usepackage[T1]{fontenc}\n\\usepackage[croatian]{babel}\n"
        val out = EngineCompat.dropT1Fontenc(src)!!
        assertTrue(!out.contains("fontenc"))
        assertTrue(out.contains("\\usepackage[croatian]{babel}"))
        assertEquals(src.lines().size, out.lines().size)
    }

    @Test
    fun fontencMitTS1_undRequirePackage() {
        assertEquals("", EngineCompat.dropT1Fontenc("\\RequirePackage[TS1, T1]{fontenc}"))
    }

    @Test
    fun kyrillischeKodierung_bleibt() {
        // T2A ist keine Latin-Kodierung: Herausnehmen würde Kyrillisch kaputt machen.
        assertNull(EngineCompat.dropT1Fontenc("\\usepackage[T2A,T1]{fontenc}"))
    }

    @Test
    fun ohneT1_nichtsZuTun() {
        assertNull(EngineCompat.dropT1Fontenc("\\usepackage{fontspec}"))
        assertNull(EngineCompat.dropT1Fontenc("\\usepackage{fontenc}"))
    }

    // ── Issue #13, zweiter Teil: fontenc steht in der Dokumentklasse ──

    @get:org.junit.Rule
    val tmp = org.junit.rules.TemporaryFolder()

    @Test
    fun fontencInKlasse_wirdEntfernt_dateienAnderswoBleiben() {
        val dir = tmp.newFolder("job")
        val cls = java.io.File(dir, "brief.cls").apply {
            writeText("\\LoadClass{letter}\n\\RequirePackage[T1]{fontenc}\n\\RequirePackage[croatian]{babel}\n")
        }
        val sub = java.io.File(dir, "teile").apply { mkdirs() }
        val sty = java.io.File(sub, "extra.sty").apply { writeText("\\usepackage[T1]{fontenc}\n") }
        val kyrill = java.io.File(dir, "ru.sty").apply { writeText("\\usepackage[T2A,T1]{fontenc}\n") }
        val main = java.io.File(dir, "document.tex").apply { writeText("\\usepackage[T1]{fontenc}\n") }
        val bild = java.io.File(dir, "bild.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val geaendert = EngineCompat.dropT1FontencInFiles(dir)

        assertEquals(setOf("brief.cls", "teile/extra.sty"), geaendert.toSet())
        assertTrue(!cls.readText().contains("fontenc"))
        assertTrue("babel bleibt", cls.readText().contains("[croatian]{babel}"))
        assertEquals("Zeilenzahl bleibt", 4, cls.readText().lines().size)
        assertEquals("", sty.readText().trim())
        assertTrue("T2A bleibt", kyrill.readText().contains("T2A"))
        assertTrue("document.tex ist Sache des Hauptdokuments", main.readText().contains("fontenc"))
        assertEquals(3, bild.length())
    }

    @Test
    fun ordnerOhneT1_nichtsZuTun() {
        val dir = tmp.newFolder("job2")
        java.io.File(dir, "x.cls").writeText("\\LoadClass{article}\n")
        assertTrue(EngineCompat.dropT1FontencInFiles(dir).isEmpty())
    }
}

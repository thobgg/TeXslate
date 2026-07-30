package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für die Ersatzschrift-Auflösung ([FontFallback]): kuratierte Zuordnung,
 * Heuristik für unbekannte Namen und die Textersetzung im Quelltext.
 */
class FontFallbackTest {

    @Test
    fun arial_wirdZuHeros() {
        val (out, used) = FontFallback.replaceMissingFont(
            "\\documentclass{article}\n\\setmainfont{Arial}\n",
            "Arial",
        )!!
        assertEquals("TeX Gyre Heros", used)
        assertTrue(out.contains("\\setmainfont{TeX Gyre Heros}"))
    }

    @Test
    fun timesNewRoman_wirdZuTermes() {
        val (_, used) = FontFallback.replaceMissingFont("\\setmainfont{Times New Roman}", "Times New Roman")!!
        assertEquals("TeX Gyre Termes", used)
    }

    @Test
    fun courierNew_wirdZuLatinModernMono() {
        val (_, used) = FontFallback.replaceMissingFont("\\setmonofont{Courier New}", "Courier New")!!
        assertEquals("Latin Modern Mono", used)
    }

    @Test
    fun optionenUndMakroname_bleibenErhalten() {
        val src = "\\newfontfamily\\symbfont[Scale=0.9]{Nicht Da}"
        val (out, _) = FontFallback.replaceMissingFont(src, "Nicht Da")!!
        assertEquals("\\newfontfamily\\symbfont[Scale=0.9]{Latin Modern Roman}", out)
    }

    @Test
    fun setsansfont_nutztRolleDesBefehls() {
        val (_, used) = FontFallback.replaceMissingFont("\\setsansfont{Hausschrift}", "Hausschrift")!!
        assertEquals("Latin Modern Sans", used)
    }

    @Test
    fun unbekannterName_mitSansImNamen_wirdSans() {
        val (_, used) = FontFallback.replaceMissingFont(
            "\\newfontfamily\\x{Irgendwas Sans Pro}",
            "Irgendwas Sans Pro",
        )!!
        assertEquals("Latin Modern Sans", used)
    }

    @Test
    fun grossKleinschreibung_istEgal() {
        val (out, _) = FontFallback.replaceMissingFont("\\setmainfont{ARIAL}", "arial")!!
        assertTrue(out.contains("TeX Gyre Heros"))
    }

    @Test
    fun mehrfachesVorkommen_wirdKomplettErsetzt() {
        val src = "\\setmainfont{Arial}\n\\setsansfont{Arial}\n"
        val (out, _) = FontFallback.replaceMissingFont(src, "Arial")!!
        assertEquals(2, Regex("TeX Gyre Heros").findAll(out).count())
        assertTrue(!out.contains("Arial"))
    }

    @Test
    fun andereSchriftenBleibenUnberuehrt() {
        val src = "\\setmainfont{Arial}\n\\setmonofont{Latin Modern Mono}\n"
        val (out, _) = FontFallback.replaceMissingFont(src, "Arial")!!
        assertTrue(out.contains("\\setmonofont{Latin Modern Mono}"))
    }

    @Test
    fun schriftNurImText_wirdNichtErsetzt() {
        // Kein \set*font-Befehl → nichts zu retten (etwa Klasse fordert intern an).
        assertNull(FontFallback.replaceMissingFont("Die Schrift Arial ist schön.", "Arial"))
    }

    @Test
    fun leererNameLiefertNull() {
        assertNull(FontFallback.replaceMissingFont("\\setmainfont{Arial}", "  "))
    }
}

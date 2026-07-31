package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für chinesische Schriften. Der Fall stammt aus der Vorlage des nationalen
 * Mathematik-Modellierungswettbewerbs (`cumcmthesis.cls`), die SimSun und simkai.ttf
 * fest verdrahtet — Windows-Schriften, die es auf Android nicht gibt.
 *
 * Wichtig ist nicht nur, DASS ersetzt wird, sondern WOMIT: Eine lateinische
 * Ersatzschrift ergäbe ein PDF voller leerer Kästchen statt einer Fehlermeldung.
 */
class FontFallbackCjkTest {

    @Test
    fun simsun_wirdZuFandolSong() {
        val (out, used) = FontFallback.replaceMissingFont("\\setCJKmainfont{SimSun}", "SimSun")!!
        assertEquals("FandolSong-Regular.otf", used)
        assertTrue(out.contains("\\setCJKmainfont{FandolSong-Regular.otf}"))
    }

    @Test
    fun setCJKfamilyfont_ersetztDasZweiteArgument() {
        // \setCJKfamilyfont{song}[AutoFakeBold]{SimSun} – Familienname muss stehen bleiben
        val src = "\\setCJKfamilyfont{song}[AutoFakeBold]{SimSun}"
        val (out, used) = FontFallback.replaceMissingFont(src, "SimSun")!!
        assertEquals("FandolSong-Regular.otf", used)
        assertEquals("\\setCJKfamilyfont{song}[AutoFakeBold]{FandolSong-Regular.otf}", out)
    }

    @Test
    fun dateiname_alsSchriftangabe() {
        val (_, used) = FontFallback.replaceMissingFont(
            "\\setCJKfamilyfont{kai}[AutoFakeBold]{simkai.ttf}",
            "simkai.ttf",
        )!!
        assertEquals("FandolKai-Regular.otf", used)
    }

    @Test
    fun chinesischerName_wirdErkannt() {
        val (_, used) = FontFallback.replaceMissingFont("\\setCJKmainfont{宋体}", "宋体")!!
        assertEquals("FandolSong-Regular.otf", used)
    }

    @Test
    fun unbekannteCjkSchrift_bleibtImCjkBereich() {
        // Niemals Latin Modern – das gäbe leere Kästchen statt Zeichen.
        val (_, used) = FontFallback.replaceMissingFont(
            "\\setCJKmainfont{Irgendeine Hausschrift}",
            "Irgendeine Hausschrift",
        )!!
        assertTrue("Ersatz muss eine CJK-Schrift sein, war: $used", used.startsWith("Fandol"))
    }

    @Test
    fun sansVariante_wirdHei() {
        val (_, used) = FontFallback.replaceMissingFont("\\setCJKsansfont{Microsoft YaHei}", "Microsoft YaHei")!!
        assertEquals("FandolHei-Regular.otf", used)
    }

    @Test
    fun lateinischeBefehle_bleibenLateinisch() {
        val (_, used) = FontFallback.replaceMissingFont("\\setmainfont{Times New Roman}", "Times New Roman")!!
        assertEquals("TeX Gyre Termes", used)
    }

    @Test
    fun engineMeldetOhneEndung_dokumentSchreibtMitEndung() {
        // Genau der Fall der Wettbewerbsvorlage: Fehler nennt „simkai",
        // im Dokument steht \setCJKfamilyfont{kai}[AutoFakeBold]{simkai.ttf}
        val src = "\\setCJKfamilyfont{kai}[AutoFakeBold]{simkai.ttf}"
        val (out, used) = FontFallback.replaceMissingFont(src, "simkai")!!
        assertEquals("FandolKai-Regular.otf", used)
        assertTrue(out.contains("{FandolKai-Regular.otf}"))
    }
}

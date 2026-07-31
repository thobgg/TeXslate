package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests für die Ersetzung in Klassen- und Paketdateien — der Fall der chinesischen
 * Wettbewerbsvorlage `cumcmthesis.cls`, die `Times New Roman` und `Arial` fest
 * verdrahtet, während im Hauptdokument nichts davon steht.
 */
class FontFallbackFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun schriftInDerDokumentklasse_wirdErsetzt() {
        val cls = File(tmp.root, "cumcmthesis.cls")
        cls.writeText("\\RequirePackage{fontspec}\n\\setmainfont{Times New Roman}\n\\setsansfont{Arial}\n")
        val used = FontFallback.replaceMissingFontInFiles(tmp.root, "Times New Roman")
        assertEquals("TeX Gyre Termes", used)
        val nachher = cls.readText()
        assertTrue(nachher.contains("\\setmainfont{TeX Gyre Termes}"))
        assertTrue("Arial bleibt bis zum nächsten Durchlauf", nachher.contains("\\setsansfont{Arial}"))
    }

    @Test
    fun auchInUnterordnern() {
        val sub = File(tmp.root, "sty").apply { mkdirs() }
        File(sub, "meins.sty").writeText("\\setsansfont{Arial}")
        assertEquals("TeX Gyre Heros", FontFallback.replaceMissingFontInFiles(tmp.root, "Arial"))
    }

    @Test
    fun documentTex_bleibtUnberuehrt() {
        // Das schreibt die Engine selbst – dort greift die Ersetzung im Quelltext.
        File(tmp.root, "document.tex").writeText("\\setmainfont{Arial}")
        assertNull(FontFallback.replaceMissingFontInFiles(tmp.root, "Arial"))
    }

    @Test
    fun andereDateitypen_werdenNichtAngefasst() {
        File(tmp.root, "notiz.txt").writeText("\\setmainfont{Arial}")
        assertNull(FontFallback.replaceMissingFontInFiles(tmp.root, "Arial"))
    }

    @Test
    fun ohneTreffer_null() {
        File(tmp.root, "a.cls").writeText("\\setmainfont{Latin Modern Roman}")
        assertNull(FontFallback.replaceMissingFontInFiles(tmp.root, "Arial"))
    }
}

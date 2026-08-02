package de.bgg_home.texdroid.synctex

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.GZIPOutputStream

/**
 * Tests für [SyncTexParser] — die Zuordnung PDF-Stelle ↔ Quelltext-Zeile.
 *
 * Die Testdatei ist **echt**: `res/synctex/dense.synctex` stammt aus einem
 * XeLaTeX-Lauf über eine volle A4-Seite mit Überschrift, mehrzeiligen Absätzen,
 * einer sehr kurzen Zeile, Formel und Aufzählung. Die erwarteten Ergebnisse sind
 * keine Schätzung, sondern die Ausgabe des Originalwerkzeugs
 * `synctex edit -o <seite>:<x>:<y>:dense.pdf` für dieselben Punkte — der Parser
 * muss also liefern, was ein PC-Viewer auch liefert.
 *
 * Koordinaten sind PDF-Punkte ab der linken oberen Ecke der Seite.
 */
class SyncTexParserTest {

    private val index: SyncTexIndex by lazy {
        val text = javaClass.getResourceAsStream("/synctex/dense.synctex")!!
            .bufferedReader().readText()
        SyncTexParser.parse(text.lineSequence())!!
    }

    private fun lineAt(x: Float, y: Float): Int? =
        index.inverseSearch(PdfPoint(page = 1, x = x, y = y))?.line

    @Test
    fun ueberschriftUndErsterAbsatz() {
        assertEquals("Überschrift", 6, lineAt(90f, 88f))
        assertEquals("erste Absatzzeile", 7, lineAt(90f, 96f))
    }

    /**
     * Der eigentliche Grund für die waagerechte Auswertung: Ein Absatz aus
     * mehreren Quellzeilen wird zu gesetzten Zeilen, die **mittendrin** von der
     * einen zur nächsten Quellzeile wechseln. Links und rechts derselben
     * gesetzten Zeile stehen also verschiedene Antworten – wer nur die Zeile
     * bestimmt, liegt auf einer Seite immer falsch.
     */
    @Test
    fun innerhalbEinerGesetztenZeile_wechseltDieQuellzeile() {
        assertEquals("links auf der Zeile", 7, lineAt(90f, 104f))
        assertEquals("rechts auf derselben Zeile", 8, lineAt(500f, 104f))

        assertEquals(8, lineAt(150f, 112f))
        assertEquals(9, lineAt(450f, 112f))
    }

    /**
     * „Kurz." füllt die gesetzte Zeile nicht aus. Die Kiste um sie ist trotzdem
     * satzbreit und trägt die Leerzeile danach – ein Tipp weit rechts davon muss
     * die Zeile mit dem Wort treffen, nicht die Leerzeile und nicht den Absatz
     * darunter.
     */
    @Test
    fun kurzeZeile_wirdAufIhrerGanzenBreiteGetroffen() {
        assertEquals(13, lineAt(90f, 160f))
        assertEquals(13, lineAt(250f, 160f))
        assertEquals(13, lineAt(500f, 160f))
    }

    @Test
    fun hauptdokument_wirdErkannt() {
        val hit = index.inverseSearch(PdfPoint(1, 90f, 96f))!!
        assertTrue("absoluter Job-Pfad endet auf document.tex", hit.isMainDocument)
        assertEquals("document.tex", hit.fileName)
    }

    /**
     * Gegenprobe mit einer **echten Datei vom Tablet** (Galaxy Tab S8 Ultra,
     * 02.08.2026): Tectonic reicht den Editor-Inhalt als primäre Eingabe durch
     * und nennt sie deshalb `texput`, nicht `document.tex`. Ohne diesen Fall
     * hielte die App auf dem Gerät jede Stelle für eine fremde Datei und
     * spränge nie – der Test hält die Erkennung fest.
     */
    @Test
    fun geraetedatei_texput_giltAlsHauptdokument() {
        val text = javaClass.getResourceAsStream("/synctex/device-texput.synctex")!!
            .bufferedReader().readText()
        val hit = SyncTexParser.parse(text.lineSequence())!!
            .inverseSearch(PdfPoint(page = 1, x = 200f, y = 105f))!!
        assertEquals("texput", hit.fileName)
        assertTrue("texput ist die Hauptdatei", hit.isMainDocument)
        assertTrue("Zeile aus dem Dokument: ${hit.line}", hit.line in 1..20)
    }

    @Test
    fun fremdeDatei_giltNichtAlsHauptdokument() {
        val fremd = SourceLocation(file = "kapitel/eins.tex", line = 3)
        assertFalse(fremd.isMainDocument)
        assertEquals("eins.tex", fremd.fileName)
    }

    @Test
    fun seiteOhneDatensaetze_liefertNichts() {
        assertNull(index.inverseSearch(PdfPoint(page = 7, x = 100f, y = 100f)))
    }

    @Test
    fun forwardSearch_findetDieZeileAufIhrerSeite() {
        val hit = index.forwardSearch(SourceLocation("document.tex", 13))!!
        assertEquals(1, hit.page)
        // „Kurz." steht auf der gesetzten Zeile bei y ≈ 160 (Referenz
        // `synctex view -i 13:0:dense.tex`). Welcher Datensatz der Zeile
        // gewinnt, ist Auslegungssache – deshalb ein Band statt eines Punktes.
        assertTrue("y in der Nähe von 160: ${hit.y}", hit.y in 150f..170f)
    }

    @Test
    fun forwardSearch_unbekannteDatei_liefertNichts() {
        assertNull(index.forwardSearch(SourceLocation("gibtsnicht.tex", 3)))
    }

    @Test
    fun leereOderKaputteEingabe_liefertNullStattAbsturz() {
        assertNull(SyncTexParser.parse(emptySequence()))
        assertNull(SyncTexParser.parse(sequenceOf("völliger Unsinn", "###")))
        // Vorspann ohne Inhalt: formal gültig, aber ohne jede Zuordnung.
        assertNull(SyncTexParser.parse(sequenceOf("SyncTeX Version:1", "Content:", "Postamble:")))
        // Abgeschnittene Datensätze (Compile mittendrin abgebrochen).
        assertNull(
            SyncTexParser.parse(
                sequenceOf("SyncTeX Version:1", "Input:1:document.tex", "Content:", "{1", "[1,13:47"),
            ),
        )
    }

    @Test
    fun gzipDatei_wirdGelesen() {
        val text = javaClass.getResourceAsStream("/synctex/dense.synctex")!!
            .bufferedReader().readText()
        val gz = File.createTempFile("document", ".synctex.gz").apply { deleteOnExit() }
        GZIPOutputStream(gz.outputStream()).bufferedWriter().use { it.write(text) }

        assertEquals(13, SyncTexParser.parse(gz)!!.inverseSearch(PdfPoint(1, 250f, 160f))?.line)
    }

    @Test
    fun fehlendeDatei_liefertNull() {
        assertNull(SyncTexParser.parse(File("/gibt/es/nicht.synctex.gz")))
        assertNull(SyncTexParser.parse(null))
    }
}

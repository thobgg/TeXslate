package de.bgg_home.texdroid.storage

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests für [DocumentStore.decodeText]. Wichtig, weil der stille UTF-8-Ersatz
 * („�") beim Speichern die Umlaute eines Latin-1-Dokuments endgültig
 * vernichtet hätte.
 */
class DecodeTextTest {

    @Test
    fun utf8_wirdGelesen() {
        val text = "Universität Tübingen — Größe ≥ 5"
        assertEquals(text, DocumentStore.decodeText(text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun latin1_faelltZurueck() {
        val text = "Universität Tübingen, Lübeck"
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        assertEquals(text, DocumentStore.decodeText(bytes))
    }

    @Test
    fun keinErsatzzeichen_beiLatin1() {
        val bytes = "Grüße".toByteArray(Charsets.ISO_8859_1)
        val decoded = DocumentStore.decodeText(bytes)
        assertEquals("Kein � im Ergebnis", -1, decoded.indexOf('�'))
    }

    @Test
    fun leereDatei_gibtLeerenText() {
        assertEquals("", DocumentStore.decodeText(ByteArray(0)))
    }

    @Test
    fun reinesAscii_bleibtGleich() {
        assertEquals("\\documentclass{article}", DocumentStore.decodeText("\\documentclass{article}".toByteArray()))
    }
}

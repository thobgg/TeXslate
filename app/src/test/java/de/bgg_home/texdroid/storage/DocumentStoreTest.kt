package de.bgg_home.texdroid.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

/**
 * Tests für die (Android-freie) Kern-Schreiblogik [DocumentStore.writeBytes].
 *
 * Hintergrund-Bug: `write` verschluckte einen null-Stream/eine Exception und der
 * Aufrufer meldete trotzdem „gespeichert" → stille Datenverlust-Gefahr, wenn das
 * SAF-Recht weg war. `writeBytes` gibt jetzt ehrlich Erfolg/Misserfolg zurück und
 * bevorzugt trunkierende Modi.
 */
class DocumentStoreTest {

    private val payload = "Inhalt äöü\n".toByteArray(Charsets.UTF_8)

    @Test
    fun write_succeedsOnFirstMode_andWritesBytes() {
        val sink = ByteArrayOutputStream()
        val modes = mutableListOf<String>()
        val ok = DocumentStore.writeBytes(payload) { mode -> modes += mode; sink }
        assertTrue(ok)
        assertEquals(listOf("wt"), modes) // trunkierender Modus zuerst, reicht
        assertArrayEquals(payload, sink.toByteArray())
    }

    @Test
    fun write_returnsFalse_whenAllModesReturnNull() {
        // Kein Stream verfügbar (z.B. Recht weg): NICHT still als Erfolg werten.
        assertFalse(DocumentStore.writeBytes(payload) { null })
    }

    @Test
    fun write_fallsBackToNextMode_whenOpenThrows() {
        val sink = ByteArrayOutputStream()
        val tried = mutableListOf<String>()
        val ok = DocumentStore.writeBytes(payload) { mode ->
            tried += mode
            if (mode == "wt") throw SecurityException("Provider kann kein 'wt'")
            sink
        }
        assertTrue(ok)
        assertEquals(listOf("wt", "rwt"), tried) // wt scheiterte, rwt griff
        assertArrayEquals(payload, sink.toByteArray())
    }

    @Test
    fun write_returnsFalse_whenWritingAlwaysThrows() {
        val boom = object : OutputStream() {
            override fun write(b: Int) = throw IOException("kaputt")
            override fun write(b: ByteArray) = throw IOException("kaputt")
            override fun write(b: ByteArray, off: Int, len: Int) = throw IOException("kaputt")
        }
        assertFalse(DocumentStore.writeBytes(payload) { boom })
    }
}

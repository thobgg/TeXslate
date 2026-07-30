package de.bgg_home.texdroid.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests für die Entscheidung, ob eine Projektdatei erneut durch SAF kopiert werden
 * muss ([ProjectStore.isUpToDate]). Falsch-positive Treffer wären übel – dann
 * kompilierte die App gegen veraltete Dateien –, deshalb ist die Regel bewusst
 * konservativ: nur bei bekannter Größe UND Änderungszeit wird übersprungen.
 */
class ProjectSyncSkipTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun fehlendeKopie_mussKopiertWerden() {
        val f = java.io.File(tmp.root, "fehlt.tex")
        assertFalse(ProjectStore.isUpToDate(f, 100L, 1000L))
    }

    @Test
    fun gleicheGroesseUndNichtNeuer_wirdUebersprungen() {
        val f = tmp.newFile("a.tex").apply { writeText("12345") }
        f.setLastModified(2000L)
        assertTrue(ProjectStore.isUpToDate(f, 5L, 1000L))
    }

    @Test
    fun quelleNeuerAlsKopie_mussKopiertWerden() {
        val f = tmp.newFile("b.tex").apply { writeText("12345") }
        f.setLastModified(1000L)
        assertFalse(ProjectStore.isUpToDate(f, 5L, 2000L))
    }

    @Test
    fun abweichendeGroesse_mussKopiertWerden() {
        val f = tmp.newFile("c.tex").apply { writeText("12345") }
        f.setLastModified(2000L)
        assertFalse(ProjectStore.isUpToDate(f, 999L, 1000L))
    }

    @Test
    fun unbekannteGroesseOderZeit_mussKopiertWerden() {
        val f = tmp.newFile("d.tex").apply { writeText("12345") }
        f.setLastModified(2000L)
        assertFalse("Provider ohne Größenangabe", ProjectStore.isUpToDate(f, -1L, 1000L))
        assertFalse("Provider ohne Zeitstempel", ProjectStore.isUpToDate(f, 5L, 0L))
    }

    @Test
    fun ordnerIstNieAktuell() {
        val d = tmp.newFolder("unterordner")
        assertFalse(ProjectStore.isUpToDate(d, 0L, 1000L))
    }
}

package de.bgg_home.texdroid.storage

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Prüft die Erkennung nachträglich abgelegter Schriften.
 *
 * Auf dem Gerät gemessen: fontconfig baut sein Fontset **einmal pro Prozess** auf.
 * Eine Schrift, die danach in den Font-Ordner kommt, bleibt bis zum App-Neustart
 * unsichtbar (`\setmainfont` scheitert mit „font cannot be found") — unabhängig vom
 * On-Disk-Cache: ein frischer Prozess findet sie auch mit altem Cache. Die App kann
 * das nicht heilen, aber erkennen und zum Neustart raten; genau das testen wir hier.
 *
 * Läuft ohne Compile, also schnell.
 */
@RunWith(AndroidJUnit4::class)
class FontSetChangeTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    /** Steht für die Schrift, die der Nutzer nachträglich in den Ordner legt. */
    private val addedFont: File
        get() = File(FontStore.bundledDir(ctx), "zz-fontsettest.otf")

    @Before
    fun freshProcessState() {
        addedFont.delete()
        FontStore.resetProcessStateForTest()
    }

    @After
    fun cleanUp() {
        addedFont.delete()
        FontStore.resetProcessStateForTest()
    }

    @Test
    fun unveraendertesFontSet_meldetKeineAenderung() {
        FontStore.ensureReady(ctx)

        assertFalse(
            "Ohne neue Schrift darf kein Neustart-Hinweis kommen",
            FontStore.fontSetChangedSinceStart(ctx),
        )
    }

    @Test
    fun nachtraeglichAbgelegteSchrift_wirdErkannt() {
        // Prozess-Start: fontconfig würde hier sein Fontset aufbauen.
        FontStore.ensureReady(ctx)
        assertFalse(FontStore.fontSetChangedSinceStart(ctx))

        // Der Nutzer legt eine Schrift nach (Inhalt egal – es zählt das Datei-Set).
        ctx.assets.open("fonts/texgyretermes-regular.otf").use { input ->
            addedFont.outputStream().use { input.copyTo(it) }
        }

        assertTrue(
            "Nachträglich abgelegte Schrift muss erkannt werden (Neustart-Hinweis)",
            FontStore.fontSetChangedSinceStart(ctx),
        )
        // Auch ein weiterer Compile ändert daran nichts – der Hinweis bleibt, bis
        // der Prozess neu startet.
        FontStore.ensureReady(ctx)
        assertTrue(FontStore.fontSetChangedSinceStart(ctx))
    }

    @Test
    fun entfernteSchrift_wirdErkannt() {
        ctx.assets.open("fonts/texgyretermes-regular.otf").use { input ->
            addedFont.outputStream().use { input.copyTo(it) }
        }
        FontStore.ensureReady(ctx)
        assertFalse(FontStore.fontSetChangedSinceStart(ctx))

        assertTrue("Testschrift muss löschbar sein", addedFont.delete())

        assertTrue(
            "Entfernte Schrift muss ebenfalls als Änderung gelten",
            FontStore.fontSetChangedSinceStart(ctx),
        )
    }
}

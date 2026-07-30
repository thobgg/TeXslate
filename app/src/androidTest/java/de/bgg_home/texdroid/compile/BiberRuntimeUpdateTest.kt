package de.bgg_home.texdroid.compile

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.bgg_home.texdroid.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Regressionstest für den `biber`-Symlink nach einem App-Update.
 *
 * Beim Update bekommt die App ein neues `nativeLibraryDir`
 * (`/data/app/~~<zufall>/…`). Der beim letzten Start angelegte Symlink
 * `files/biber/launch/biber` zeigt dann ins Leere. `File.exists()` folgt dem Link
 * und meldet für so einen toten Link **false**, worauf das erneute `Os.symlink()`
 * mit `EEXIST` scheiterte – `ensureReady` fing die Ausnahme ab und lieferte
 * `false`. Für den Nutzer hieß das: **biber ist nach jedem App-Update weg**, bis
 * die App-Daten gelöscht werden. Auf dem Tab S8 Ultra reproduziert (30.07.2026).
 *
 * Der Test stellt genau diesen Zustand her: toter Link auf einen Pfad, den es
 * nicht gibt.
 */
@RunWith(AndroidJUnit4::class)
class BiberRuntimeUpdateTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun toterSymlinkNachUpdate_wirdRepariert() {
        assumeTrue("Nur in der thesis-Edition", BuildConfig.HAS_BIBER)

        assertTrue("Ausgangslage: biber muss bereitstehen", BiberRuntime.ensureReady(ctx))

        // Zustand nach einem Update nachstellen: Link zeigt auf einen alten,
        // nicht mehr existierenden nativeLibraryDir-Pfad.
        val link = File(ctx.filesDir, "biber/launch/biber")
        link.delete()
        Os.symlink("/data/app/~~alterpfad/lib/arm64/libbiber_launcher.so", link.absolutePath)
        assertTrue("Der tote Link muss als Datei-Eintrag existieren", link.absolutePath.isNotEmpty())

        assertTrue("Nach dem Update muss biber wieder aufgesetzt werden", BiberRuntime.ensureReady(ctx))

        val target = Os.readlink(link.absolutePath)
        assertEquals(
            "Link muss auf den AKTUELLEN Launcher zeigen",
            File(ctx.applicationInfo.nativeLibraryDir, "libbiber_launcher.so").absolutePath,
            target,
        )
    }
}

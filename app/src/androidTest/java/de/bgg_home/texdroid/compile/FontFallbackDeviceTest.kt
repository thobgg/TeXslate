package de.bgg_home.texdroid.compile

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Führt [FontFallback] auf dem GERÄT aus. Grund: Androids ICU-Regex-Engine lehnt
 * Muster ab, die Desktop-Java akzeptiert (unmaskierte `}`/`]` in Zeichenklassen) –
 * genau daran ist die biblatex-Regex schon einmal gescheitert: Unit-Tests grün, App
 * geflogen. Ein JVM-Test kann das prinzipiell nicht bemerken, dieser hier schon:
 * er würde beim Kompilieren des Musters werfen.
 *
 * Braucht keinen TeX-Lauf und ist entsprechend schnell.
 */
@RunWith(AndroidJUnit4::class)
class FontFallbackDeviceTest {

    @Test
    fun musterUebersetztAufAndroid_undErsetztSchrift() {
        val (out, used) = FontFallback.replaceMissingFont(
            "\\documentclass{article}\n\\newfontfamily\\symbfont[Scale=0.9]{Arial}\n",
            "Arial",
        )!!
        assertEquals("TeX Gyre Heros", used)
        assertTrue(out.contains("\\newfontfamily\\symbfont[Scale=0.9]{TeX Gyre Heros}"))
    }
}

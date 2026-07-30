package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * Tests für [LatexCompiler.documentSignature] — sie entscheidet, ob die
 * Hilfsdateien (`document.aux` & Co.) des letzten Laufs weiterverwendet werden
 * dürfen. Zu großzügig heißt: ein Dokument erbt die `.aux` eines anderen (babel
 * bricht dann hart ab); zu streng heißt: jeder Compile kostet einen Extra-Durchlauf.
 */
class DocumentSignatureTest {

    private fun sig(uri: String?, src: String) = LatexCompiler.documentSignature(uri, src)

    @Test
    fun tippenImTextteil_aendertNichts() {
        val pre = "\\documentclass{article}\n\\usepackage[german]{babel}\n"
        val a = sig("content://doc/1", pre + "\\begin{document}\nHallo\n\\end{document}")
        val b = sig("content://doc/1", pre + "\\begin{document}\nHallo Welt, noch ein Satz.\n\\end{document}")
        assertEquals("Nur der Textteil geändert → Hilfsdateien dürfen bleiben", a, b)
    }

    @Test
    fun geaenderteSprache_inDerPraeambel_verwirft() {
        val a = sig("content://doc/1", "\\documentclass{article}\n\\usepackage[german]{babel}\n\\begin{document}x\\end{document}")
        val b = sig("content://doc/1", "\\documentclass{article}\n\\usepackage[french]{babel}\n\\begin{document}x\\end{document}")
        assertNotEquals(a, b)
    }

    @Test
    fun zweiEntwuerfeOhneUri_werdenUnterschieden() {
        // Der Fall, der auf dem Gerät den babel-Abbruch auslöste.
        val zzv = sig(null, "\\documentclass{zzv-beitrag}\n\\begin{document}x\\end{document}")
        val beamer = sig(null, "\\documentclass{beamer}\n\\usetheme{Darmstadt}\n\\begin{document}x\\end{document}")
        assertNotEquals("Verschiedene Entwürfe dürfen sich keine .aux teilen", zzv, beamer)
    }

    @Test
    fun andereDatei_gleichepraeambel_wirdUnterschieden() {
        val src = "\\documentclass{article}\n\\begin{document}x\\end{document}"
        assertNotEquals(sig("content://doc/1", src), sig("content://doc/2", src))
    }

    @Test
    fun ohneBeginDocument_bleibtStabil() {
        val src = "\\documentclass{article}\n% noch im Aufbau\n"
        assertEquals(sig(null, src), sig(null, src))
    }
}

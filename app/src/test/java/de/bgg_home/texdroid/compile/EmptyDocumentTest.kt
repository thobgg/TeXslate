package de.bgg_home.texdroid.compile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests für die Erkennung leerer Dokumente. Der Fall entsteht beim ersten Kontakt
 * mit der App: „Neu" antippen, „Kompilieren" antippen — ohne diese Prüfung kommt
 * dann `cannot open "document.xdv"`, weil LaTeX ohne Inhalt keine Seite erzeugt.
 */
class EmptyDocumentTest {

    @Test
    fun frischesDokument_istLeer() {
        assertTrue(
            LatexCompiler.hasEmptyBody(
                "\\documentclass{article}\n\n\\begin{document}\n\n\\end{document}\n",
            ),
        )
    }

    @Test
    fun nurKommentare_zaehlenNichtAlsInhalt() {
        assertTrue(
            LatexCompiler.hasEmptyBody(
                "\\begin{document}\n% hier kommt später der Text\n\n\\end{document}",
            ),
        )
    }

    @Test
    fun mitText_istNichtLeer() {
        assertFalse(LatexCompiler.hasEmptyBody("\\begin{document}\nHallo\n\\end{document}"))
    }

    @Test
    fun einBefehlReicht() {
        assertFalse(LatexCompiler.hasEmptyBody("\\begin{document}\n\\maketitle\n\\end{document}"))
    }

    @Test
    fun ohneBeginDocument_wirdNichtsBehauptet() {
        // Klassendatei oder eingebundener Teil – da wissen wir es nicht.
        assertFalse(LatexCompiler.hasEmptyBody("\\ProvidesClass{meins}\n\\LoadClass{article}"))
    }

    @Test
    fun fehlendesEnd_wirdVertragen() {
        assertTrue(LatexCompiler.hasEmptyBody("\\begin{document}\n   \n"))
        assertFalse(LatexCompiler.hasEmptyBody("\\begin{document}\nText ohne Ende"))
    }
}

package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit-Tests für die biber-Vorprüfung (Issue #1, Tester-Feedback): biblatex
 * ohne `backend=bibtex` muss VOR dem Compile erkannt werden, statt in Tectonic
 * mit „can't open path …bib" zu scheitern.
 */
class LatexCompilerTest {

    @Test
    fun biblatexOhneBackend_wirdErkannt() {
        val src = """
            \documentclass{article}
            \usepackage{biblatex}
            \addbibresource{references.bib}
        """.trimIndent()
        assertEquals(2, LatexCompiler.findBiberLine(src))
    }

    @Test
    fun biblatexMitBiberBackend_wirdErkannt() {
        val src = """\usepackage[backend=biber, style=numeric]{biblatex}"""
        assertEquals(1, LatexCompiler.findBiberLine(src))
    }

    @Test
    fun biblatexMitBibtexBackend_istOk() {
        val src = """\usepackage[backend=bibtex, style=numeric]{biblatex}"""
        assertNull(LatexCompiler.findBiberLine(src))
    }

    @Test
    fun bibtexBackendMitLeerzeichen_istOk() {
        val src = """\usepackage[style=numeric, backend = bibtex]{biblatex}"""
        assertNull(LatexCompiler.findBiberLine(src))
    }

    @Test
    fun auskommentierteZeile_zaehltNicht() {
        val src = """
            \documentclass{article}
            % \usepackage{biblatex}
            \usepackage{amsmath} % \usepackage{biblatex}
        """.trimIndent()
        assertNull(LatexCompiler.findBiberLine(src))
    }

    @Test
    fun anderePakete_loesenNichtAus() {
        val src = """
            \usepackage{amsmath}
            \usepackage[style=authoryear]{natbib}
        """.trimIndent()
        assertNull(LatexCompiler.findBiberLine(src))
    }

    @Test
    fun usepackageMitLeerzeichenUmDenNamen_wirdErkannt() {
        val src = """\usepackage [style=numeric] { biblatex }"""
        assertEquals(1, LatexCompiler.findBiberLine(src))
    }
}

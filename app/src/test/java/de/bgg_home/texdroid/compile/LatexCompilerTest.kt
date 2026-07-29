package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit-Tests für die biber-Vorprüfung (Issue #1, Tester-Feedback): biblatex
 * ohne `backend=bibtex` muss VOR dem Compile erkannt werden, statt in Tectonic
 * mit „can't open path …bib" zu scheitern.
 *
 * ⚠️ Nicht abgedeckt: Diese Tests laufen auf der Desktop-JVM. Das Gerät nutzt
 * Androids ICU-Regex-Engine, die sich abweichend verhält (siehe
 * LatexCompiler.BIBLATEX_USEPACKAGE, dort Kommentar) — dieser Crash-Modus ist
 * per src/test prinzipiell nicht testbar und bleibt Geräte-/Instrumentation-Sache.
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

    // --- A. Regressions-Guards: korrektes Verhalten, war nur ungetestet ---

    @Test
    fun leereOptionen_wirdErkannt() {
        // \usepackage[]{biblatex} — keine Optionen ⇒ biber-Default greift.
        val src = """\usepackage[]{biblatex}"""
        assertEquals(1, LatexCompiler.findBiberLine(src))
    }

    @Test
    fun trailingKommentarHinterUsepackage_wirdErkannt() {
        // Gegenstück zu auskommentierteZeile_zaehltNicht: hier steht der Kommentar
        // HINTER dem Paket, substringBefore('%') schneidet ihn ab, Match bleibt.
        val src = """\usepackage{biblatex} % TODO: später backend=bibtex?"""
        assertEquals(1, LatexCompiler.findBiberLine(src))
    }

    @Test
    fun crlfZeilenenden_liefernKorrekteZeile() {
        // Windows-Zeilenenden: lineSequence trennt an \n, das \r bleibt am
        // Zeilenende — hinter `}` und daher für den Match irrelevant.
        val src = "\\documentclass{article}\r\n\\usepackage{biblatex}\r\n"
        assertEquals(2, LatexCompiler.findBiberLine(src))
    }

    @Test
    fun zeilennummerNachVorspann_istExakt() {
        val src = """
            \documentclass{article}
            \usepackage{amsmath}
            \usepackage{graphicx}
            \usepackage{biblatex}
        """.trimIndent()
        assertEquals(4, LatexCompiler.findBiberLine(src))
    }

    @Test
    fun tabsInOptionen_istOk() {
        // \s im backend=bibtex-Muster deckt auch Tabs ab.
        val src = "\\usepackage[\tbackend=bibtex\t]{biblatex}"
        assertNull(LatexCompiler.findBiberLine(src))
    }

    @Test
    fun leereQuelle_istOk() {
        // Guard gegen Off-by-one bei leerer Zeilen-Sequence.
        assertNull(LatexCompiler.findBiberLine(""))
    }

    // --- B. Dokumentierte Grenzen: aktuelles Falsch-Negativ festhalten ---
    // Erwünschtes Endverhalten wäre „erkannt"; aktuell bewusst null. Schlägt ein
    // Test hier um (liefert eine Zeile), hat sich das Produktionsverhalten
    // geändert — Signal für einen Detektor-Umbau, kein Fehler.

    @Test
    fun bekannteGrenze_gebuendeltePakete_werdenNichtErkannt() {
        // Regex verlangt {biblatex} allein; Bündelung wird (noch) übersehen.
        val src = """\usepackage{csquotes,biblatex}"""
        assertNull(LatexCompiler.findBiberLine(src))
    }

    @Test
    fun bekannteGrenze_mehrzeiligeOptionen_werdenNichtErkannt() {
        // Per-Zeile-Regex greift nicht über den Zeilenumbruch in den Optionen.
        val src = """
            \usepackage[backend=biber,
                        style=authoryear]{biblatex}
        """.trimIndent()
        assertNull(LatexCompiler.findBiberLine(src))
    }

    @Test
    fun bekannteGrenze_requirePackage_wirdNichtErkannt() {
        // Nur \usepackage matcht, \RequirePackage (rar in Nutzerdokumenten) nicht.
        val src = """\RequirePackage{biblatex}"""
        assertNull(LatexCompiler.findBiberLine(src))
    }
}

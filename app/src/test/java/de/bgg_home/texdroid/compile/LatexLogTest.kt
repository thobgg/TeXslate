package de.bgg_home.texdroid.compile

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Unit-Tests für den TeX-Log-Parser. Reine, deterministische Logik – die
 * Grundlage für die Zeilennummern-Zuordnung (QW 3.2, Jump-to-Error).
 *
 * Locale wird auf Englisch gepinnt: Seit QW A6 übersetzt LatexLog die
 * Meldungen nach Systemsprache — ohne Pin schlagen die (englischen)
 * Erwartungen auf jedem deutschen Entwicklungsrechner fehl.
 */
class LatexLogTest {

    private lateinit var originalLocale: Locale

    @Before
    fun pinLocale() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.ENGLISH)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun classicForm_extractsMessageAndLine() {
        val log = """
            This is XeTeX, Version 3.14159265
            ! Undefined control sequence.
            l.12 \foo
                     bar
        """.trimIndent()

        val errors = LatexLog.parseErrors(log)

        assertEquals(1, errors.size)
        assertEquals(12, errors[0].line)
        // Seit QW A6 formuliert LatexErrorGerman die Roh-Meldung nutzerfreundlich um.
        assertEquals("Unknown command (undefined control sequence) – typo or missing package?", errors[0].message)
    }

    @Test
    fun fileLineForm_extractsMessageAndLine() {
        val log = "./document.tex:5: LaTeX Error: Missing \\begin{document}."

        val errors = LatexLog.parseErrors(log)

        assertEquals(1, errors.size)
        assertEquals(5, errors[0].line)
        assertEquals("LaTeX error: Missing \\begin{document}.", errors[0].message)
    }

    @Test
    fun bangWithoutLineNumber_lineIsNull() {
        val errors = LatexLog.parseErrors("! Emergency stop.")

        assertEquals(1, errors.size)
        assertNull(errors[0].line)
        assertEquals("Emergency stop – TeX could not continue (see the previous errors).", errors[0].message)
    }

    @Test
    fun multipleErrors_allCaptured() {
        val log = """
            ! Undefined control sequence.
            l.3 \foo
            Some output here.
            ! Missing $ inserted.
            l.7 a_b
        """.trimIndent()

        val errors = LatexLog.parseErrors(log)

        assertEquals(2, errors.size)
        assertEquals(3, errors[0].line)
        assertEquals(7, errors[1].line)
        assertTrue(errors[1].message.startsWith("Missing $"))
    }

    @Test
    fun cleanLog_noErrors() {
        val errors = LatexLog.parseErrors("This is fine.\nOutput written on document.pdf (1 page).")
        assertTrue(errors.isEmpty())
    }

    @Test
    fun blankLog_noErrors() {
        assertTrue(LatexLog.parseErrors("").isEmpty())
        assertTrue(LatexLog.parseErrors("   \n  ").isEmpty())
    }

    // ── Bug A: fontspec-Font nicht gefunden, l.<n> versetzt (echter Log) ──────

    /** Quelltext wie caseA.tex: \setsansfont auf Zeile 16, \setmonofont auf 17. */
    private val sourceA: String = buildString {
        append("\\documentclass{article}\n")            // 1
        append("\\usepackage{fontspec}\n")               // 2
        for (n in 3..15) append("% Zeile $n\n")          // 3..15
        append("\\setsansfont{NonExistentSansXYZ}\n")    // 16
        append("\\setmonofont{NonExistentMonoXYZ}\n")    // 17
        append("\\begin{document}\n")                    // 18
        append("Hallo.\n")                               // 19
        append("\\end{document}\n")                      // 20
    }

    /** Echter xelatex-Ausschnitt: Sans-Fehler (Font auf Zeile 16!) meldet l.17. */
    private val logA = """
        (./caseA.tex
        ! Package fontspec Error:
        (fontspec)                The font "NonExistentSansXYZ" cannot be found;
        (fontspec)                this may be but usually is not a fontspec bug.
        (fontspec)                (XeTeX/luaotfload).

        For immediate help type H <return>.
         ...

        l.17 \setmonofont
                         {NonExistentMonoXYZ}
    """.trimIndent()

    @Test
    fun bugA_fontError_mappedToCommandLine_notLdotN() {
        val e = LatexLog.parseErrors(logA, sourceA).first()
        // Der Font "…Sans…" steht auf Zeile 16, NICHT auf der l.17 aus dem Log.
        assertEquals(16, e.line)
        assertTrue(e.message.contains("NonExistentSansXYZ"))
    }

    @Test
    fun bugA_withoutSource_fallsBackToLdotN() {
        // Ohne Quelltext kein Mapping möglich → l.17 (dokumentiertes Verhalten).
        assertEquals(17, LatexLog.parseErrors(logA, source = null).first().line)
    }

    // ── Bug B: Fehler in geladener .cls, Datei-Stack (echter Log) ────────────

    /** Echter xelatex-Ausschnitt: Fehler in mycls.cls (l.6), verschachtelte (). */
    private val logB = """
        (./example.tex
        LaTeX2e <2025-11-01>
        (./mycls.cls
        Document Class: mycls 2026 Test
        (/usr/share/texlive/texmf-dist/tex/latex/base/article.cls
        Document Class: article 2025/01/22 v1.4n Standard LaTeX document class
        (/usr/share/texlive/texmf-dist/tex/latex/base/size10.clo
        File: size10.clo 2025/01/22 v1.4n Standard LaTeX file (size option)
        )
        \c@part=\count271
        \bibindent=\dimen148
        )
        ! Undefined control sequence.
        <recently read> \thisCommandDoesNotExist

        l.6 \thisCommandDoesNotExist
    """.trimIndent()

    @Test
    fun bugB_errorInCls_attributedToCls_notMainDocument() {
        val e = LatexLog.parseErrors(logB).first()
        assertEquals(6, e.line)
        assertTrue(
            "Fehler gehört zur geladenen mycls.cls, nicht zu example.tex – war: ${e.file}",
            e.file?.endsWith("mycls.cls") == true,
        )
    }

    @Test
    fun error_inMainDocument_keepsMainFile() {
        val log = """
            (./document.tex
            ! Undefined control sequence.
            l.42 \foo
        """.trimIndent()
        val e = LatexLog.parseErrors(log).first()
        assertEquals(42, e.line)
        assertTrue(e.file?.endsWith("document.tex") == true)
    }

    @Test
    fun fileLineForm_reportsFileToo() {
        val e = LatexLog.parseErrors(
            "./document.tex:12: LaTeX Error: There's no line here to end.",
        ).first()
        assertEquals(12, e.line)
        assertTrue(e.file?.endsWith("document.tex") == true)
    }
}

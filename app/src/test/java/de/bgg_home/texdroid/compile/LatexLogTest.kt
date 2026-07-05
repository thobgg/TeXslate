package de.bgg_home.texdroid.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit-Tests für den TeX-Log-Parser. Reine, deterministische Logik – die
 * Grundlage für die Zeilennummern-Zuordnung (QW 3.2, Jump-to-Error).
 */
class LatexLogTest {

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
        assertEquals("Undefined control sequence.", errors[0].message)
    }

    @Test
    fun fileLineForm_extractsMessageAndLine() {
        val log = "./document.tex:5: LaTeX Error: Missing \\begin{document}."

        val errors = LatexLog.parseErrors(log)

        assertEquals(1, errors.size)
        assertEquals(5, errors[0].line)
        assertEquals("LaTeX Error: Missing \\begin{document}.", errors[0].message)
    }

    @Test
    fun bangWithoutLineNumber_lineIsNull() {
        val errors = LatexLog.parseErrors("! Emergency stop.")

        assertEquals(1, errors.size)
        assertNull(errors[0].line)
        assertEquals("Emergency stop.", errors[0].message)
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
}

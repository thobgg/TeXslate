// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.compile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Issue #9: fehlende Bilder bei einer als Einzeldatei geöffneten `.tex`. */
class MissingSiblingFilesTest {

    @Test
    fun bild_ausLogDesMelders() {
        val log = """
            ! Unable to load picture or PDF file 'Ujjain3103BCEFeb18.png'.
            <to be read again>
                               }
            l.146 ...width=\linewidth]{Ujjain3103BCEFeb18.png}
            ! Unable to load picture or PDF file 'Ujjain3102BCEFeb18.png'.
            ! Unable to load picture or PDF file 'Ujjain3103BCEFeb18.png'.
        """.trimIndent()
        assertEquals(
            listOf("Ujjain3103BCEFeb18.png", "Ujjain3102BCEFeb18.png"),
            MissingSiblingFiles.inLog(log),
        )
    }

    @Test
    fun input_datei() {
        val log = "! LaTeX Error: File `kapitel1.tex' not found."
        assertEquals(listOf("kapitel1.tex"), MissingSiblingFiles.inLog(log))
    }

    @Test
    fun sauberesLog_nichts() {
        assertTrue(MissingSiblingFiles.inLog("Output written on document.xdv (1 page).").isEmpty())
    }
}

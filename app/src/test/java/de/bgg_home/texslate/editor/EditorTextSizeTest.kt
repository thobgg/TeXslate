// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/** Issue #10: gespeicherte Editor-Schriftgröße bleibt im sinnvollen Bereich. */
class EditorTextSizeTest {

    @Test
    fun rundetAufHalbeSp() {
        assertEquals(14.5f, EditorTextSize.normalize(14.62f))
        assertEquals(14f, EditorTextSize.normalize(14.2f))
    }

    @Test
    fun begrenztAufMinUndMax() {
        assertEquals(EditorTextSize.MIN_SP, EditorTextSize.normalize(2f))
        assertEquals(EditorTextSize.MAX_SP, EditorTextSize.normalize(120f))
    }

    @Test
    fun nanWirdStandard() {
        assertEquals(EditorTextSize.DEFAULT_SP, EditorTextSize.normalize(Float.NaN))
    }
}

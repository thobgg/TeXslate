// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.editor

import android.view.KeyCharacterMap
import android.view.KeyEvent

/**
 * AltGr auf Hardware-Tastaturen (Issue #11). Android meldet AltGr als rechte Alt-Taste;
 * der Editor (sora 0.23) fügt bei gedrückter Alt-Taste aber kein Zeichen ein. Auf dem
 * deutschen Layout fehlten damit genau die LaTeX-Zeichen: \ { } [ ] | ~ @ €.
 */
object AltGrInput {

    /** Das Zeichen, das die Taste mit AltGr erzeugt, oder null, wenn es kein AltGr-Zeichen ist. */
    fun text(event: KeyEvent): String? {
        if (event.action != KeyEvent.ACTION_DOWN) return null
        if (event.metaState and KeyEvent.META_ALT_RIGHT_ON == 0) return null
        // Strg+Alt oder Meta sind Kürzel, keine Zeichen
        if (event.isCtrlPressed || event.isMetaPressed) return null
        val code = event.getUnicodeChar(event.metaState)
        if (code == 0 || code and KeyCharacterMap.COMBINING_ACCENT != 0) return null
        if (code == KeyCharacterMap.HEX_INPUT.code || code == KeyCharacterMap.PICKER_DIALOG_INPUT.code) return null
        return String(Character.toChars(code))
    }
}

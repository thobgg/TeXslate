// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate

import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * AltGr auf Hardware-Tastaturen (Issue #11): Android meldet AltGr als rechte Alt-Taste,
 * der Editor hat solche Zeichen verworfen. Geprüft über die echte Activity wie die Kürzel.
 * Das Layout des Testgeräts kennt keine AltGr-Ebene, darum liefert die Taste ihr Zeichen
 * selbst – wie AltGr+ß auf dem deutschen Tastatur-Cover.
 */
@RunWith(AndroidJUnit4::class)
class AltGrInputTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun findEditor(v: View): CodeEditor? = when (v) {
        is CodeEditor -> v
        is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findEditor(v.getChildAt(it)) }
        else -> null
    }

    private fun taste(char: Char, meta: Int) = object : KeyEvent(0, 0, ACTION_DOWN, KEYCODE_MINUS, 0, meta) {
        override fun getUnicodeChar(metaState: Int): Int = char.code
    }

    private val altGr = KeyEvent.META_ALT_ON or KeyEvent.META_ALT_RIGHT_ON

    /** Leerer Editor mit Fokus; liefert den Text nach den Tasten und ob die App sie abgefangen hat. */
    private fun tippe(vararg tasten: KeyEvent): Pair<String, List<Boolean>> {
        compose.waitForIdle()
        val ed = findEditor(compose.activity.window.decorView)!!
        compose.waitUntil(120_000) { ed.isEditable }
        val abgefangen = mutableListOf<Boolean>()
        // Mit Hardware-Tastatur ist das Gerät nicht im Touch-Modus; erst dann nimmt der Editor den Fokus.
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        compose.runOnUiThread {
            ed.setText("")
            ed.requestFocus()
            check(ed.hasFocus()) { "Editor ohne Fokus" }
        }
        // Nach jeder Änderung bricht sora die Zeilen neu um und ist kurz gesperrt.
        for (taste in tasten) {
            compose.waitUntil(10_000) { ed.isEditable }
            compose.runOnUiThread { abgefangen += compose.activity.dispatchKeyEvent(taste) }
        }
        compose.waitForIdle()
        return ed.text.toString() to abgefangen
    }

    @Test
    fun altGr_insertsBackslashPipeAt() {
        val (text, abgefangen) = tippe(taste('\\', altGr), taste('|', altGr), taste('@', altGr))
        assertTrue("nicht abgefangen: $abgefangen, Text '$text'", abgefangen.all { it })
        assertEquals("\\|@", text)
    }

    @Test
    fun altGr_braceGetsAutoClosedLikeAnyKey() {
        val (text, _) = tippe(taste('{', altGr))
        assertEquals("{}", text)
    }

    @Test
    fun ctrlAlt_isNoCharacter() {
        val (text, _) = tippe(taste('\\', altGr or KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON))
        assertEquals("", text)
    }

    @Test
    fun leftAlt_isLeftToEditor() {
        val (_, abgefangen) = tippe(taste('x', KeyEvent.META_ALT_ON or KeyEvent.META_ALT_LEFT_ON))
        assertEquals(listOf(false), abgefangen)
    }
}

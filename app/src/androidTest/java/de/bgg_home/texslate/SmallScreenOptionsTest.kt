// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate

import android.content.Context
import android.view.KeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #10: Optionen für kleine Bildschirme, geprüft über die echte Activity.
 * Die Werte liegen in den App-Einstellungen und werden nach jedem Test
 * zurückgesetzt, damit andere Tests das Standardlayout sehen.
 */
@RunWith(AndroidJUnit4::class)
class SmallScreenOptionsTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val prefs
        get() = compose.activity.getSharedPreferences("app", Context.MODE_PRIVATE)

    @After
    fun reset() {
        prefs.edit().remove("focusMode").remove("editorTextSize").remove("showShortcutBar").commit()
    }

    private fun ctrl(code: Int) = KeyEvent(
        0, 0, KeyEvent.ACTION_DOWN, code, 0,
        KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON,
    )

    private fun exitButtonShown(): Boolean {
        val label = compose.activity.getString(R.string.focus_exit)
        return compose.onAllNodes(hasContentDescription(label)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun ctrlM_schaltetFokusmodusUndMerktIhn() {
        compose.waitForIdle()
        assertTrue("Fokusmodus schon aktiv?", !exitButtonShown())

        compose.runOnUiThread { compose.activity.dispatchKeyEvent(ctrl(KeyEvent.KEYCODE_M)) }
        compose.waitForIdle()
        assertTrue("Beenden-Knopf fehlt im Fokusmodus", exitButtonShown())
        assertEquals(true, prefs.getBoolean("focusMode", false))

        compose.runOnUiThread { compose.activity.dispatchKeyEvent(ctrl(KeyEvent.KEYCODE_M)) }
        compose.waitForIdle()
        assertTrue("Fokusmodus nicht beendet", !exitButtonShown())
        assertEquals(false, prefs.getBoolean("focusMode", true))
    }

    @Test
    fun zoomgeste_speichertSchriftgroesse() {
        compose.waitForIdle()
        val before = prefs.getFloat("editorTextSize", 18f)
        // Mitte des Bildschirms liegt im Editor (Phone und Tablet hochkant).
        compose.onRoot().performTouchInput {
            val c = center
            pinch(
                start0 = c + Offset(-60f, 0f), end0 = c + Offset(-300f, 0f),
                start1 = c + Offset(60f, 0f), end1 = c + Offset(300f, 0f),
                durationMillis = 400,
            )
        }
        compose.waitForIdle()
        val after = prefs.getFloat("editorTextSize", -1f)
        assertTrue("Schriftgröße nicht gespeichert: vorher $before, nachher $after", after > before)
    }
}

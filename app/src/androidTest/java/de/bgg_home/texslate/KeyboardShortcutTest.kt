package de.bgg_home.texslate

import android.view.KeyEvent
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tastenkürzel für Hardware-Tastaturen (Issue #8). Geprüft über die echte Activity,
 * weil die Kürzel dort abgefangen werden, bevor der Editor die Taste sieht.
 */
@RunWith(AndroidJUnit4::class)
class KeyboardShortcutTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun key(code: Int, meta: Int) = KeyEvent(0, 0, KeyEvent.ACTION_DOWN, code, 0, meta)

    private fun titleShown(): Boolean {
        val title = compose.activity.getString(R.string.goto_line_title)
        return compose.onAllNodes(hasText(title)).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun ctrlG_opensGoToLine() {
        compose.waitForIdle()
        assertTrue("Dialog schon offen?", !titleShown())
        var consumed = false
        compose.runOnUiThread {
            consumed = compose.activity.dispatchKeyEvent(
                key(KeyEvent.KEYCODE_G, KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON),
            )
        }
        compose.waitForIdle()
        assertTrue("Strg+G nicht abgefangen", consumed)
        assertTrue("Zeilen-Dialog nicht geöffnet", titleShown())
    }

    @Test
    fun plainG_isNotAShortcut() {
        compose.waitForIdle()
        compose.runOnUiThread { compose.activity.dispatchKeyEvent(key(KeyEvent.KEYCODE_G, 0)) }
        compose.waitForIdle()
        assertEquals(false, titleShown())
    }
}

package de.bgg_home.texslate.editor

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rosemoe.sora.widget.CodeEditor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Stellt Issue #8 nach: Zwei-Finger-Wischen auf dem Touchpad (Tastatur-Cover im
 * DeX-Modus) scrollte die Editor-Seite nicht. Die Geste kommt als Touch-Folge mit
 * Quelle MOUSE, Werkzeug FINGER und ohne gedrückte Taste an.
 */
@RunWith(AndroidJUnit4::class)
class TouchpadScrollTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var editor: CodeEditor

    @Before
    fun setUp() {
        val text = (1..400).joinToString("\n") { "Zeile $it: \\textbf{TeXslate}" }
        compose.setContent {
            LatexEditor(
                initialText = text,
                darkTheme = false,
                onEditorCreated = { editor = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        compose.waitForIdle()
    }

    private fun event(
        downTime: Long,
        action: Int,
        y: Float,
        toolType: Int,
        buttonState: Int,
    ): MotionEvent {
        val props = MotionEvent.PointerProperties().apply { id = 0; this.toolType = toolType }
        val coords = MotionEvent.PointerCoords().apply { x = 300f; this.y = y; pressure = 1f; size = 1f }
        return MotionEvent.obtain(
            downTime, SystemClock.uptimeMillis(), action, 1, arrayOf(props), arrayOf(coords),
            0, buttonState, 1f, 1f, 0, 0, InputDevice.SOURCE_MOUSE, 0,
        )
    }

    /** Wischt von y=900 nach y=300 – Finger nach oben, Inhalt soll nach unten laufen. */
    private fun swipeUp(toolType: Int, buttonState: Int) {
        val down = SystemClock.uptimeMillis()
        val ys = listOf(900f) + (1..12).map { 900f - it * 50f }
        compose.runOnUiThread {
            editor.dispatchTouchEvent(event(down, MotionEvent.ACTION_DOWN, ys.first(), toolType, buttonState))
            for (y in ys.drop(1)) {
                editor.dispatchTouchEvent(event(down, MotionEvent.ACTION_MOVE, y, toolType, buttonState))
            }
            editor.dispatchTouchEvent(event(down, MotionEvent.ACTION_UP, ys.last(), toolType, buttonState))
        }
        compose.waitForIdle()
    }

    @Test
    fun touchpadTwoFingerSwipe_scrollsEditor() {
        assertEquals(0, editor.offsetY)
        swipeUp(MotionEvent.TOOL_TYPE_FINGER, buttonState = 0)
        assertTrue("Editor hat nicht gescrollt: offsetY=${editor.offsetY}", editor.offsetY > 0)
    }

    @Test
    fun mouseDragWithButton_stillSelectsInsteadOfScrolling() {
        swipeUp(MotionEvent.TOOL_TYPE_MOUSE, MotionEvent.BUTTON_PRIMARY)
        assertTrue("Maus-Ziehen sollte weiter markieren", editor.isTextSelected)
    }

    @Test
    fun classifier_onlyMatchesTouchpadGestures() {
        val down = SystemClock.uptimeMillis()
        assertTrue(event(down, MotionEvent.ACTION_MOVE, 1f, MotionEvent.TOOL_TYPE_FINGER, 0).isTouchpadSwipe())
        assertFalse(
            event(down, MotionEvent.ACTION_MOVE, 1f, MotionEvent.TOOL_TYPE_MOUSE, MotionEvent.BUTTON_PRIMARY)
                .isTouchpadSwipe(),
        )
        assertFalse(
            event(down, MotionEvent.ACTION_MOVE, 1f, MotionEvent.TOOL_TYPE_FINGER, MotionEvent.BUTTON_PRIMARY)
                .isTouchpadSwipe(),
        )
    }
}

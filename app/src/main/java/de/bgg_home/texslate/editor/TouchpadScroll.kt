// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.editor

import android.annotation.SuppressLint
import android.os.Build
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View

/**
 * Zwei-Finger-Wischen auf dem Touchpad scrollt den Editor (Issue #8).
 *
 * Android liefert diese Geste als Touch-Folge mit der Quelle SOURCE_MOUSE, der
 * Werkzeug-Art FINGER und ohne gedrückte Taste (ab Android 14 zusätzlich mit
 * CLASSIFICATION_TWO_FINGER_SWIPE). sora leitet jedes Maus-Ereignis in seine
 * Mauslogik, und dort bewegt nur eine gedrückte Taste etwas – beim Wischen passiert
 * also gar nichts. Das Mausrad kommt dagegen als ACTION_SCROLL an und ist nicht
 * betroffen; deshalb scrollte die Maus, das Touchpad aber nicht.
 */
internal fun MotionEvent.isTouchpadSwipe(): Boolean {
    if (!isFromSource(InputDevice.SOURCE_MOUSE)) return false
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
        classification == MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE
    ) {
        return true
    }
    return buttonState == 0 && getToolType(0) == MotionEvent.TOOL_TYPE_FINGER
}

/**
 * Reicht eine Touchpad-Wischgeste als Fingerwischen an den Editor weiter – dann
 * greift dessen normales Scrollen samt Schwung. Alle anderen Ereignisse laufen
 * unverändert durch.
 */
@SuppressLint("ClickableViewAccessibility")
internal val TouchpadScrollListener = View.OnTouchListener { view, event ->
    if (!event.isTouchpadSwipe()) return@OnTouchListener false
    val touch = MotionEvent.obtain(event)
    touch.source = InputDevice.SOURCE_TOUCHSCREEN
    val handled = view.onTouchEvent(touch)
    touch.recycle()
    handled
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.editor

import android.os.Build
import android.util.DisplayMetrics
import android.util.TypedValue
import kotlin.math.roundToInt

/**
 * Schriftgröße des Editors in sp – einstellbar und dauerhaft (Issue #10).
 *
 * sora-editor zoomt mit zwei Fingern von Haus aus, vergaß die Größe aber bei
 * jedem Start. Jetzt wird jede Änderung (Regler in den Einstellungen oder
 * Zoomgeste) gespeichert und beim nächsten Start wieder gesetzt.
 */
object EditorTextSize {
    /** sora-Standard (`CodeEditor.DEFAULT_TEXT_SIZE`). */
    const val DEFAULT_SP = 18f
    const val MIN_SP = 8f
    const val MAX_SP = 40f

    /** Auf halbe sp runden und in den erlaubten Bereich holen. */
    fun normalize(sp: Float): Float {
        if (sp.isNaN()) return DEFAULT_SP
        return ((sp * 2f).roundToInt() / 2f).coerceIn(MIN_SP, MAX_SP)
    }

    /**
     * Pixel → sp. Ab Android 14 skaliert das System große Schriften nicht mehr
     * linear; die Umkehrung muss deshalb über [TypedValue.deriveDimension] laufen,
     * sonst wüchse die gespeicherte Größe bei jedem Start ein Stück.
     */
    fun pxToSp(px: Float, metrics: DisplayMetrics): Float =
        if (Build.VERSION.SDK_INT >= 34) {
            TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, px, metrics)
        } else {
            @Suppress("DEPRECATION")
            px / metrics.scaledDensity
        }

    fun spToPx(sp: Float, metrics: DisplayMetrics): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
}

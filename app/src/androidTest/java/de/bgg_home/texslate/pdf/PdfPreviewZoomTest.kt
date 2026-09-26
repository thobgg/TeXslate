// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.pdf

import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.bgg_home.texslate.R
import de.bgg_home.texslate.synctex.PdfPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Stellt Issue #7 nach: In der geteilten Ansicht (Editor links, Vorschau rechts)
 * lief die gezoomte PDF-Seite über den Editor und ließ sich so weit wegschieben,
 * dass sie nicht mehr zurückzuholen war.
 */
@RunWith(AndroidJUnit4::class)
class PdfPreviewZoomTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var pdf: File
    private lateinit var pageDescription: String
    private val taps = mutableListOf<PdfPoint>()

    @Before
    fun setUp() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        pageDescription = ctx.getString(R.string.pdf_page, 1)
        pdf = File(ctx.cacheDir, "zoomtest.pdf")
        // Eine A4-Seite (595 x 842 pt) – die Maße braucht der SyncTeX-Tipp unten.
        val doc = android.graphics.pdf.PdfDocument()
        val info = android.graphics.pdf.PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = doc.startPage(info)
        page.canvas.drawText("TeXslate", 100f, 100f, Paint().apply { textSize = 24f })
        doc.finishPage(page)
        pdf.outputStream().use { doc.writeTo(it) }
        doc.close()

        compose.setContent {
            Row(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f).fillMaxHeight().testTag("editor"))
                PdfPreview(
                    file = pdf,
                    reloadToken = 0,
                    onTapPosition = { taps += it },
                    modifier = Modifier.weight(1f).testTag("preview"),
                )
            }
        }
        // Die Seite wird asynchron gerendert.
        compose.waitUntil(10_000) {
            compose.onAllNodes(
                androidx.compose.ui.test.hasContentDescription(pageDescription),
            ).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun pane(): Rect = compose.onNodeWithTag("preview").fetchSemanticsNode().boundsInRoot
    private fun page(): Rect =
        compose.onNodeWithContentDescription(pageDescription).fetchSemanticsNode().boundsInRoot

    private fun zoomIn() {
        compose.onNodeWithTag("preview").performTouchInput {
            pinch(
                start0 = center - Offset(40f, 0f), end0 = center - Offset(width * 0.45f, 0f),
                start1 = center + Offset(40f, 0f), end1 = center + Offset(width * 0.45f, 0f),
            )
        }
        compose.waitForIdle()
    }

    /** Zwei Finger nebeneinander, beide um [delta] verschoben. */
    private fun twoFingerPan(delta: Offset) {
        compose.onNodeWithTag("preview").performTouchInput {
            down(0, center - Offset(60f, 0f))
            down(1, center + Offset(60f, 0f))
            repeat(20) {
                updatePointerBy(0, delta / 20f)
                updatePointerBy(1, delta / 20f)
                move()
            }
            up(0)
            up(1)
        }
        compose.waitForIdle()
    }

    @Test
    fun zoomedPage_staysInsideItsPane() {
        val before = page()
        zoomIn()
        val pane = pane()
        val zoomed = page()
        assertTrue("Zoom hat nicht gegriffen: $before -> $zoomed", zoomed.width > before.width)
        assertTrue(
            "Seite ragt links in den Editor: page=$zoomed pane=$pane",
            zoomed.left >= pane.left - 1f && zoomed.right <= pane.right + 1f,
        )
    }

    @Test
    fun zoomedPage_cannotBePannedOutOfReach() {
        zoomIn()
        // Weit mehr, als das Fenster breit ist – in beide Richtungen.
        for (delta in listOf(Offset(-3000f, -3000f), Offset(6000f, 6000f))) {
            twoFingerPan(delta)
            val pane = pane()
            val visible = page()
            assertTrue(
                "Seite nach Verschieben um $delta kaum noch sichtbar: page=$visible pane=$pane",
                visible.width >= pane.width * 0.5f,
            )
        }
    }

    @Test
    fun pinchOut_restoresOriginalPosition() {
        val before = page()
        zoomIn()
        twoFingerPan(Offset(-500f, 0f))
        compose.onNodeWithTag("preview").performTouchInput {
            pinch(
                start0 = center - Offset(width * 0.45f, 0f), end0 = center - Offset(10f, 0f),
                start1 = center + Offset(width * 0.45f, 0f), end1 = center + Offset(10f, 0f),
            )
        }
        compose.waitForIdle()
        val after = page()
        assertEquals(before.left, after.left, 1f)
        assertEquals(before.right, after.right, 1f)
    }

    @Test
    fun tap_stillMapsToPdfPoints_zoomedOrNot() {
        // Skaliert wird um die Fenstermitte, die Seite ist waagerecht zentriert:
        // Ein Tipp in die Mitte trifft also mit und ohne Zoom x = 595 / 2.
        compose.onNodeWithTag("preview").performTouchInput { click(center) }
        compose.waitForIdle()
        zoomIn()
        compose.onNodeWithTag("preview").performTouchInput { click(center) }
        compose.waitForIdle()

        assertEquals("zwei Tipps erwartet: $taps", 2, taps.size)
        for (tap in taps) {
            assertEquals(1, tap.page)
            assertEquals(297.5f, tap.x.toFloat(), 3f)
        }
    }
}

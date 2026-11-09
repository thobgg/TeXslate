// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.compile

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Echter Compile über [LatexCompiler]: Ein für pdfLaTeX geschriebener kroatischer
 * Brief mit `\usepackage[T1]{fontenc}` verlor unter XeTeX alle č, ć, š, ž, đ
 * (Issue #13). Nach dem Nachlauf ohne fontenc darf im Log nichts mehr fehlen.
 * Gegenprobe: Ein deutsches Dokument mit T1 bleibt unberührt (kein zweiter Lauf).
 */
@RunWith(AndroidJUnit4::class)
class FontencCompatCompileTest {

    private val tag = "FONTENC_COMPAT"
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun brief(sprache: String, text: String) = """
        \documentclass[11pt]{letter}
        \usepackage[T1]{fontenc}
        \usepackage[utf8]{inputenc}
        \usepackage[$sprache]{babel}
        \begin{document}
        \begin{letter}{Kišpatićeva 12}
        \opening{Poštovana prof.,}
        $text
        \closing{Hvala}
        \end{letter}
        \end{document}
    """.trimIndent()

    @Test
    fun kroatisch_mitT1_alleZeichenImPdf() {
        val r = runBlocking { LatexCompiler.compile(ctx, brief("croatian", "Preporučena pošta, želio, đak, ČĆŠŽĐ.")) }
        val fehlend = EngineCompat.missingBeyondLatin1(r.log)
        Log.i(tag, "kroatisch: ok=${r.ok} fehlend=$fehlend notes=${r.notes}")
        assertTrue("PDF muss entstehen: ${r.engineError.take(160)}", r.ok && File(r.pdfPath).length() > 0)
        assertTrue("Zeichen fehlen noch: $fehlend", fehlend.isEmpty())
        assertTrue("Hinweis auf fontenc fehlt: ${r.notes}", r.notes.any { it.contains("fontenc") })
    }

    @Test
    fun deutsch_mitT1_bleibtUnberuehrt() {
        val r = runBlocking { LatexCompiler.compile(ctx, brief("ngerman", "Grüße aus Köln, Maß und Straße.").replace("Kišpatićeva", "Hauptstraße").replace("Poštovana", "Sehr geehrte")) }
        Log.i(tag, "deutsch: ok=${r.ok} notes=${r.notes}")
        assertTrue("PDF muss entstehen: ${r.engineError.take(160)}", r.ok && File(r.pdfPath).length() > 0)
        assertTrue("Deutsch braucht keinen Eingriff in fontenc: ${r.notes}", r.notes.none { it.contains("fontenc") })
    }
}

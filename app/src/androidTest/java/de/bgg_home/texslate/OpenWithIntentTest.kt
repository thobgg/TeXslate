// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * „Öffnen mit TeXslate“ aus anderen Apps (Issue #15): .tex-Dateien müssen über
 * VIEW und EDIT bei uns ankommen, auch wenn die Quell-App nur einen
 * unbestimmten Typ schickt (dann entscheidet die Endung).
 */
@RunWith(AndroidJUnit4::class)
class OpenWithIntentTest {

    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext

    private fun handlers(action: String, uri: String, mime: String?): List<String> {
        val i = Intent(action).setDataAndType(Uri.parse(uri), mime)
        if (mime == null) i.data = Uri.parse(uri)
        return ctx.packageManager.queryIntentActivities(i, 0).map { it.activityInfo.packageName }
    }

    private fun assertWeHandle(action: String, uri: String, mime: String?) {
        val h = handlers(action, uri, mime)
        assertTrue("$action $mime $uri → $h", ctx.packageName in h)
    }

    @Test fun viewMitTexTyp() = assertWeHandle(Intent.ACTION_VIEW, "content://x.provider/doc/1", "text/x-tex")
    @Test fun editMitTexTyp() = assertWeHandle(Intent.ACTION_EDIT, "content://x.provider/doc/1", "application/x-tex")
    @Test fun viewMitTextPlain() = assertWeHandle(Intent.ACTION_VIEW, "content://x.provider/doc/1", "text/plain")
    @Test fun viewUnbestimmterTypMitEndung() = assertWeHandle(Intent.ACTION_VIEW, "content://x.provider/Downloads/arbeit.tex", "application/octet-stream")
    @Test fun editBibMitEndung() = assertWeHandle(Intent.ACTION_EDIT, "content://x.provider/Downloads/lit.bib", "application/octet-stream")
}

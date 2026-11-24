// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import de.bgg_home.texslate.ui.TeXslateApp
import de.bgg_home.texslate.ui.theme.TeXslateTheme

class MainActivity : ComponentActivity() {
    // Von der Compose-Schicht registrierter Rückruf, der den aktuellen Editor-Inhalt
    // als Entwurf sichert. In onStop() aufgerufen – der zuverlässige Lebenszyklus-
    // Punkt vor einem Hintergrund-Kill durch das System.
    private var saveDraft: (() -> Unit)? = null

    // Tastenkürzel (Strg+S usw.), ebenfalls von der Compose-Schicht registriert.
    private var shortcutHandler: ((KeyEvent) -> Boolean)? = null

    // Per „Öffnen mit“ übergebene Datei (VIEW/EDIT). Die Compose-Schicht öffnet sie
    // und meldet sie danach als erledigt zurück.
    private var openRequest by mutableStateOf<Uri?>(null)

    private fun takeOpenRequest(intent: Intent?) {
        if (intent == null) return
        if (intent.action != Intent.ACTION_VIEW && intent.action != Intent.ACTION_EDIT) return
        openRequest = intent.data
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        takeOpenRequest(intent)
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Nach Neuerstellung (Drehen, Prozess-Tod) nicht dieselbe Datei erneut öffnen.
        if (savedInstanceState == null) takeOpenRequest(intent)
        setContent {
            TeXslateTheme {
                // WindowSizeClass steuert Split-View (Tablet) vs. Tab-Ansicht (Phone).
                val windowSizeClass = calculateWindowSizeClass(this)
                TeXslateApp(
                    windowSizeClass = windowSizeClass,
                    onRegisterDraftSaver = { saveDraft = it },
                    onRegisterShortcutHandler = { shortcutHandler = it },
                    openRequest = openRequest,
                    onOpenRequestConsumed = { openRequest = null },
                )
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (shortcutHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStop() {
        saveDraft?.invoke()
        super.onStop()
    }
}

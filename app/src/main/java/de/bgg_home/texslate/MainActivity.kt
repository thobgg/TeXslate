package de.bgg_home.texslate

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
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

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TeXslateTheme {
                // WindowSizeClass steuert Split-View (Tablet) vs. Tab-Ansicht (Phone).
                val windowSizeClass = calculateWindowSizeClass(this)
                TeXslateApp(
                    windowSizeClass = windowSizeClass,
                    onRegisterDraftSaver = { saveDraft = it },
                    onRegisterShortcutHandler = { shortcutHandler = it },
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

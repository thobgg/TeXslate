package de.bgg_home.texdroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import de.bgg_home.texdroid.ui.TexDroidApp
import de.bgg_home.texdroid.ui.theme.TexDroidTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TexDroidTheme {
                // WindowSizeClass steuert Split-View (Tablet) vs. Tab-Ansicht (Phone).
                val windowSizeClass = calculateWindowSizeClass(this)
                TexDroidApp(windowSizeClass)
            }
        }
    }
}

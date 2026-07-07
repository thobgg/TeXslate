package de.bgg_home.texdroid.editor

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import io.github.rosemoe.sora.event.ContentChangeEvent
import io.github.rosemoe.sora.langs.textmate.TextMateColorScheme
import io.github.rosemoe.sora.langs.textmate.TextMateLanguage
import io.github.rosemoe.sora.langs.textmate.registry.FileProviderRegistry
import io.github.rosemoe.sora.langs.textmate.registry.GrammarRegistry
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.langs.textmate.registry.model.ThemeModel
import io.github.rosemoe.sora.langs.textmate.registry.provider.AssetsFileResolver
import io.github.rosemoe.sora.widget.CodeEditor
import org.eclipse.tm4e.core.registry.IThemeSource

/** Scope-Name der LaTeX-Grammatik (aus LaTeX.tmLanguage.json). */
private const val LATEX_SCOPE = "text.tex.latex"

private const val THEME_LIGHT = "texdroid-light"
private const val THEME_DARK = "texdroid-dark"

/**
 * Registriert einmalig (prozessweit) TextMate-Grammatiken und Themes aus den
 * App-Assets. sora-editor liest Assets über eine [FileProviderRegistry], die
 * TextMate-Includes (die LaTeX-Grammatik zieht `text.tex`) über die in
 * `textmate/languages.json` gelisteten Grammatiken auflöst.
 *
 * Wird NUR gelesen/registriert – sora-editor selbst bleibt unverändert (LGPL-konform).
 */
private object TextMateAssets {
    @Volatile private var initialized = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (initialized) return
        val assets = context.applicationContext.assets

        // Assets als Dateiquelle registrieren (Pfad = relativ zu app/src/main/assets).
        FileProviderRegistry.getInstance().addFileProvider(AssetsFileResolver(assets))

        // Beide Themes laden (hell/dunkel), damit zur Laufzeit umgeschaltet werden kann.
        val themeRegistry = ThemeRegistry.getInstance()
        loadTheme(themeRegistry, "textmate/themes/texdroid-light.json", THEME_LIGHT, dark = false)
        loadTheme(themeRegistry, "textmate/themes/texdroid-dark.json", THEME_DARK, dark = true)

        // Alle in languages.json aufgeführten Grammatiken registrieren
        // (LaTeX + Basis-TeX, damit die Includes auflösen).
        GrammarRegistry.getInstance().loadGrammars("textmate/languages.json")

        initialized = true
    }

    private fun loadTheme(registry: ThemeRegistry, assetPath: String, name: String, dark: Boolean) {
        val stream = FileProviderRegistry.getInstance().tryGetInputStream(assetPath)
            ?: error("Theme-Asset nicht gefunden: $assetPath")
        val source = IThemeSource.fromInputStream(stream, assetPath, null)
        registry.loadTheme(ThemeModel(source, name).apply { isDark = dark })
    }
}

/**
 * LaTeX-Editor als Compose-Composable. Wrappt sora-editors [CodeEditor] in einer
 * [AndroidView]. Über [onEditorCreated] reicht er die konkrete Editor-Instanz nach
 * oben (damit der Compile-Button den aktuellen Text lesen kann).
 *
 * @param initialText   Startinhalt (wird nur beim ersten Erzeugen gesetzt).
 * @param darkTheme     hell/dunkel – schaltet das TextMate-Theme um.
 * @param onTextChanged wird bei jeder Inhaltsänderung gerufen (für Auto-Compile, QW 3.1).
 */
@Composable
fun LatexEditor(
    initialText: String,
    darkTheme: Boolean,
    onEditorCreated: (CodeEditor) -> Unit,
    onTextChanged: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Setup einmalig anstoßen (Assets registrieren) – idempotent.
    remember(context) { TextMateAssets.ensureLoaded(context); Unit }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextMateAssets.ensureLoaded(ctx)
            CodeEditor(ctx).apply {
                typefaceText = Typeface.MONOSPACE
                // QW 3.3: Schreibkomfort auf dem Tablet.
                // Lange Prosa-Zeilen umbrechen, statt horizontal zu scrollen …
                setWordwrap(true, /* antiWordBreaking = */ true)
                // … und LaTeX-übliche 2er-Einrückung. Auto-Klammern ({}, [], $$),
                // Auto-Einrückung und die Code-Vervollständigung kommen bereits aus
                // der language-configuration bzw. TextMateLanguage.create(..., true).
                setTabWidth(2)
                // Reihenfolge ist wichtig:
                // 1) Farbschema ZUERST – TextMateLanguage färbt seine Spans nur,
                //    wenn beim Setzen der Sprache bereits ein TextMateColorScheme aktiv ist.
                applyTheme(darkTheme)
                // 2) Text setzen.
                setText(initialText)
                // 3) Sprache GENAU EINMAL setzen (löst die Analyse aller Zeilen aus).
                //    Niemals im update-Block erneut – das würde die laufende Analyse
                //    abbrechen und nur die erste Zeile gefärbt zurücklassen.
                setEditorLanguage(TextMateLanguage.create(LATEX_SCOPE, true))
                // Breiterer, gut sichtbarer vertikaler Scroll-Griff: der schmale
                // Standard-Balken ist auf dem Touchscreen schwer zu fassen. Ein
                // abgerundetes, halbtransparentes Thumb-Drawable vergrößert die
                // sichtbare wie die berührbare Fläche.
                val d = resources.displayMetrics.density
                setVerticalScrollbarThumbDrawable(
                    GradientDrawable().apply {
                        setColor(0x99757575.toInt())
                        cornerRadius = 5f * d
                        setSize((10f * d).toInt(), (56f * d).toInt())
                    },
                )
                // 4) Inhaltsänderungen melden (Auto-Compile). Nach dem initialen
                //    setText registriert – die Startbelegung löst also nichts aus.
                subscribeEvent(ContentChangeEvent::class.java) { _, _ -> onTextChanged() }
                onEditorCreated(this)
            }
        },
        update = { editor ->
            // Nur Theme-Wechsel (System hell↔dunkel) behandeln – Sprache NICHT neu setzen.
            editor.applyTheme(darkTheme)
        },
        onRelease = { editor -> editor.release() },
    )
}

/**
 * Setzt/aktualisiert nur das Farbschema (Theme). `text.tex.latex` sorgt später für:
 * \command, \begin/\end-Umgebungen, Mathe ($…$, $$…$$, \[…\]), %-Kommentare;
 * Klammer-Matching kommt aus der language-configuration.
 */
private fun CodeEditor.applyTheme(darkTheme: Boolean) {
    val themeRegistry = ThemeRegistry.getInstance()
    themeRegistry.setTheme(if (darkTheme) THEME_DARK else THEME_LIGHT)
    colorScheme = TextMateColorScheme.create(themeRegistry)
}

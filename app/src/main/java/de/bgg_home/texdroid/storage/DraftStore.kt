package de.bgg_home.texdroid.storage

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Zuletzt bearbeiteter Editor-Zustand. Wird gesichert, damit ungespeicherte Arbeit
 * einen Prozess-Tod überlebt: Android darf die App im Hintergrund jederzeit killen
 * (Speicherdruck) – der Editor-Text lebt aber nur in der [android.view.View], nicht
 * in persistiertem Zustand. Ohne diesen Puffer wäre die Arbeit dann verloren.
 */
data class Draft(
    val text: String,
    val uri: Uri?,
    val name: String?,
    val canWrite: Boolean,
)

/** Persistiert den letzten Editor-Inhalt (App-intern) für die Wiederherstellung. */
object DraftStore {
    private const val PREFS = "draft"
    private const val FILE = "draft.tex"

    private fun file(context: Context) = File(context.filesDir, FILE)

    /**
     * Schreibt den aktuellen Zustand. Läuft **synchron** aus `onStop()` – muss vor
     * einem möglichen Prozess-Tod fertig sein, ein asynchroner Schreibvorgang käme
     * evtl. nicht mehr durch. Für typische Dokumentgrößen unkritisch schnell.
     */
    fun save(context: Context, text: String, uri: Uri?, name: String?, canWrite: Boolean) {
        runCatching { file(context).writeText(text) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("uri", uri?.toString())
            .putString("name", name)
            .putBoolean("canWrite", canWrite)
            .apply()
    }

    /** Lädt den letzten Entwurf, falls vorhanden (sonst `null` → Beispiel-Dokument). */
    fun load(context: Context): Draft? {
        val f = file(context)
        if (!f.exists()) return null
        val text = runCatching { f.readText() }.getOrNull() ?: return null
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uri = p.getString("uri", null)?.let(Uri::parse)
        return Draft(text, uri, p.getString("name", null), p.getBoolean("canWrite", false))
    }
}

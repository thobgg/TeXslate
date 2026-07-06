package de.bgg_home.texdroid.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dünne Hilfsschicht über dem Storage Access Framework (SAF).
 *
 * SAF = Androids Standardweg, damit der Nutzer selbst Dateien/Ordner wählt
 * (kein direkter Pfadzugriff, keine Speicher-Permission nötig). Wir bekommen
 * eine [Uri] auf das Dokument und lesen/schreiben über den [android.content.ContentResolver].
 */
object DocumentStore {

    /** Liest den gesamten Textinhalt der [uri] (UTF-8). Läuft auf [Dispatchers.IO]. */
    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            input.readBytes().toString(Charsets.UTF_8)
        } ?: ""
    }

    /**
     * Schreibt [text] in die [uri] (UTF-8) und kürzt vorhandenen Inhalt.
     * "wt" = write + truncate; einige Provider können nur "w" – dann Fallback.
     */
    suspend fun write(context: Context, uri: Uri, text: String) = withContext(Dispatchers.IO) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val resolver = context.contentResolver
        val mode = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
        val stream = mode ?: resolver.openOutputStream(uri, "w")
        stream?.use { it.write(bytes) }
    }

    /**
     * Kopiert eine App-interne Datei [source] (z.B. das kompilierte PDF) an die
     * vom Nutzer gewählte SAF-[target] (Export „Speichern unter…").
     */
    suspend fun exportFile(context: Context, source: File, target: Uri) = withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(target, "wt")?.use { out ->
            source.inputStream().use { it.copyTo(out) }
        }
    }

    /** Fragt den Anzeigenamen (Dateiname) der [uri] ab, falls verfügbar. */
    fun displayName(context: Context, uri: Uri): String? {
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return null
    }

    /**
     * Sichert dauerhaften Zugriff auf die [uri] (überlebt App-Neustart).
     * @return true, wenn auch Schreibrecht dauerhaft gewährt wurde (dann kann
     *   direkt in dieselbe Datei zurückgespeichert werden).
     */
    fun takePersistablePermission(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            resolver.takePersistableUriPermission(uri, rw)
            true
        } catch (_: SecurityException) {
            // Nur Lesen möglich – wenigstens das dauerhaft sichern.
            runCatching {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            false
        }
    }
}

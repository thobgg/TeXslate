package de.bgg_home.texdroid.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream

/**
 * Dünne Hilfsschicht über dem Storage Access Framework (SAF).
 *
 * SAF = Androids Standardweg, damit der Nutzer selbst Dateien/Ordner wählt
 * (kein direkter Pfadzugriff, keine Speicher-Permission nötig). Wir bekommen
 * eine [Uri] auf das Dokument und lesen/schreiben über den [android.content.ContentResolver].
 */
object DocumentStore {

    /** Liest den gesamten Textinhalt der [uri]. Läuft auf [Dispatchers.IO]. */
    suspend fun read(context: Context, uri: Uri): String = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            decodeText(input.readBytes())
        } ?: ""
    }

    /**
     * Dekodiert [bytes] als UTF-8 und fällt bei ungültigen Bytes auf ISO-8859-1
     * (Latin-1) zurück — ältere Dokumente sind häufig so kodiert, deutsche
     * Umlaute stehen dort als Einzelbyte.
     *
     * Vorher lief das über `toString(Charsets.UTF_8)`: der Decoder ersetzt
     * ungültige Bytes STILL durch „\uFFFD". Wer eine Latin-1-Datei öffnete und
     * speicherte, schrieb diese Ersatzzeichen zurück und hatte seine Umlaute
     * endgültig verloren. Beim Speichern wird weiterhin UTF-8 geschrieben, die
     * Datei wechselt also einmalig die Kodierung — das ist gewollt und besser,
     * als den Text zu beschädigen.
     */
    internal fun decodeText(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (_: java.nio.charset.CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    /**
     * Schreibt [text] in die [uri] (UTF-8) und kürzt vorhandenen Inhalt.
     *
     * @return `true`, wenn wirklich geschrieben wurde. Bisher meldete der Aufrufer
     *   „gespeichert", auch wenn der Stream null war oder das SAF-Recht weg war
     *   (Datei extern verschoben/gelöscht) → stille Datenverlust-Gefahr. Der
     *   Boolean macht den Erfolg für den Aufrufer prüfbar.
     */
    suspend fun write(context: Context, uri: Uri, text: String): Boolean =
        withContext(Dispatchers.IO) {
            writeBytes(text.toByteArray(Charsets.UTF_8)) { mode ->
                context.contentResolver.openOutputStream(uri, mode)
            }
        }

    /**
     * Kern der Schreiblogik, ohne Android-Abhängigkeit (JVM-testbar). Versucht die
     * Modi der Reihe nach: "wt" (write+truncate) bevorzugt, dann "rwt", zuletzt "w".
     * Trunkierende Modi zuerst, damit ein KÜRZERES Dokument keine alten Rest-Bytes
     * am Ende behält. Jeder Öffnungs-/Schreibversuch ist abgesichert; erst wenn alle
     * scheitern, wird `false` zurückgegeben.
     */
    internal fun writeBytes(bytes: ByteArray, open: (mode: String) -> OutputStream?): Boolean {
        for (mode in WRITE_MODES) {
            val ok = runCatching {
                open(mode)?.use { it.write(bytes); it.flush() } != null
            }.getOrDefault(false)
            if (ok) return true
        }
        return false
    }

    private val WRITE_MODES = listOf("wt", "rwt", "w")

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

package de.bgg_home.texdroid.storage

import android.content.Context
import java.io.File

/**
 * Eigene, vom Nutzer gespeicherte Vorlagen. Anders als die mitgelieferten
 * Vorlagen (read-only App-Assets) liegen diese als `.tex`-Dateien im internen
 * App-Speicher (`filesDir/user_templates/`) — voll offline, kein SAF nötig.
 * Der Dateiname (ohne Endung) ist der Anzeigename.
 */
data class UserTemplate(val name: String, val file: File)

object UserTemplateStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "user_templates").apply { mkdirs() }

    /** Alle gespeicherten Vorlagen, alphabetisch. */
    fun list(context: Context): List<UserTemplate> =
        dir(context).listFiles { f -> f.isFile && f.extension.equals("tex", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { UserTemplate(it.nameWithoutExtension, it) }
            ?: emptyList()

    /**
     * Speichert [content] unter [name]. Der Name wird auf dateisystem-sichere
     * Zeichen reduziert. Gibt den gespeicherten Anzeigenamen zurück, oder null,
     * wenn Name oder Inhalt leer sind.
     */
    fun save(context: Context, name: String, content: String): String? {
        val safe = sanitize(name)
        if (safe.isBlank() || content.isBlank()) return null
        File(dir(context), "$safe.tex").writeText(content)
        return safe
    }

    fun read(template: UserTemplate): String = template.file.readText()

    fun delete(template: UserTemplate): Boolean = template.file.delete()

    /** Existiert bereits eine Vorlage mit diesem (bereinigten) Namen? */
    fun exists(context: Context, name: String): Boolean {
        val safe = sanitize(name)
        return safe.isNotBlank() && File(dir(context), "$safe.tex").exists()
    }

    // Pfadtrenner, Steuerzeichen und unter Android/Windows heikle Zeichen raus.
    private fun sanitize(name: String): String =
        name.trim().replace(Regex("[/\\\\:*?\"<>|\\x00-\\x1F]"), "_").take(60)
}

package de.bgg_home.texdroid.compile

import java.io.File

/**
 * Gleicht Groß-/Kleinschreibung von Dateinamen an.
 *
 * Windows und macOS unterscheiden in Dateinamen üblicherweise nicht zwischen groß
 * und klein — Android schon. Ein Dokument, das am PC einwandfrei läuft, scheitert
 * hier deshalb an Kleinigkeiten wie `\includegraphics{Fig3_aAs0-HSE.pdf}`, während
 * die Datei `Fig3_aAs0-hse.pdf` heißt. Beobachtet an einer arXiv-Quelle aus der
 * Chemie (30.07.2026): „Unable to load picture or PDF file".
 *
 * Statt den Nutzer Dateien umbenennen zu lassen, legen wir im **Arbeitsverzeichnis**
 * (nie im Projekt des Nutzers!) eine Kopie unter dem Namen an, den das Dokument
 * verlangt. Betroffen sind Abbildungen, eingebundene Teildateien und Bibliografien.
 *
 * Ohne Endung geschriebene Referenzen (`\includegraphics{fig1}`, `\input{kapitel}`)
 * werden mitgesucht: dann zählt der Namensstamm, die Endung liefert die gefundene
 * Datei.
 */
object FileCaseFix {

    /** Befehle, die auf Dateien zeigen; Gruppe 1 ist der Name (ggf. mit Pfad). */
    private val FILE_REFS = Regex(
        """\\(?:includegraphics\s*\*?(?:\s*\[[^\]]*\])*|input|include|addbibresource|bibliography)\s*\{([^\{\}]*)\}""",
    )

    /** Endungen, die wir bei endungslosen Referenzen ausprobieren. */
    private val EXTENSIONS = listOf(".tex", ".pdf", ".png", ".jpg", ".jpeg", ".bib", ".eps", ".sty", ".cls")

    /**
     * Legt für jede Referenz, die nur in anderer Schreibweise vorliegt, eine Kopie
     * unter dem verlangten Namen an.
     *
     * @return die angeglichenen Namen (leer, wenn alles gepasst hat).
     */
    fun fixCaseMismatches(source: String, jobDir: File): List<String> {
        val fixed = LinkedHashSet<String>()
        FILE_REFS.findAll(source).forEach { m ->
            val ref = m.groupValues[1].trim()
            if (ref.isEmpty() || ref.contains("..")) return@forEach
            val target = File(jobDir, ref)
            if (target.exists()) return@forEach
            // Endungslose Referenz: existiert eine der üblichen Endungen exakt?
            if (!ref.substringAfterLast('/').contains('.') &&
                EXTENSIONS.any { File(jobDir, ref + it).exists() }
            ) {
                return@forEach
            }
            val match = findIgnoringCase(jobDir, ref) ?: return@forEach
            val dest = if (target.name.contains('.')) target else File(jobDir, ref + "." + match.extension)
            runCatching {
                dest.parentFile?.mkdirs()
                match.copyTo(dest, overwrite = true)
                fixed += ref
            }
        }
        return fixed.toList()
    }

    /**
     * Sucht im Ordner von [ref] eine Datei, die sich nur in der Schreibweise
     * unterscheidet — mit passender Endung oder (bei endungsloser Referenz) mit
     * gleichem Namensstamm.
     */
    private fun findIgnoringCase(jobDir: File, ref: String): File? {
        val dir = File(jobDir, ref).parentFile ?: return null
        val wanted = ref.substringAfterLast('/')
        val candidates = dir.listFiles()?.filter { it.isFile } ?: return null
        candidates.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.let { return it }
        if (wanted.contains('.')) return null
        return candidates.firstOrNull { c ->
            c.nameWithoutExtension.equals(wanted, ignoreCase = true) &&
                EXTENSIONS.any { c.name.endsWith(it, ignoreCase = true) }
        }
    }
}

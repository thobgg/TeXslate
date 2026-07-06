package de.bgg_home.texdroid.compile

import android.content.Context
import android.net.Uri
import de.bgg_home.texdroid.RustBridge
import de.bgg_home.texdroid.storage.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Dünne Orchestrierungsschicht über der nativen Compile-Brücke.
 *
 * Verantwortlich für:
 *  • das beschreibbare Arbeits-/Cache-Verzeichnis (`filesDir/job`) bereitstellen,
 *  • den Aufruf auf einen Hintergrund-Thread verlagern (Compile blockiert!),
 *  • das JSON-Ergebnis in ein [CompileResult] parsen,
 *  • sauber abfangen, falls die native Lib die neue Funktion noch nicht kennt.
 */
object LatexCompiler {

    /**
     * Kompiliert [source] und gibt das Ergebnis zurück. Läuft komplett auf
     * [Dispatchers.IO] – vom UI-Thread via `launch { compile(...) }` aufrufbar.
     *
     * Ist [projectTree] gesetzt (offenes Mehrdatei-Projekt, QW 4.2), werden vorher
     * alle Projektdateien ins Arbeitsverzeichnis kopiert, damit `\input{...}` und
     * `\include{...}` auf die Geschwisterdateien auflösen.
     */
    suspend fun compile(
        context: Context,
        source: String,
        projectTree: Uri? = null,
    ): CompileResult =
        withContext(Dispatchers.IO) {
            val jobDir = File(context.filesDir, "job").apply { mkdirs() }
            try {
                cleanAuxArtifacts(jobDir)
                if (projectTree != null) {
                    ProjectStore.syncToDir(context, projectTree, jobDir)
                }
                val json = RustBridge.tectonicCompileToFile(source, jobDir.absolutePath)
                CompileResult.fromJson(json)
            } catch (t: UnsatisfiedLinkError) {
                // Alte .so ohne tectonicCompileToFile → freundlich erklären statt crashen.
                CompileResult.nativeUnavailable(t)
            } catch (t: Throwable) {
                CompileResult.nativeUnavailable(t)
            }
        }

    /**
     * Zwischen-/Hilfsdateien des letzten Compile-Laufs löschen, bevor neu gebaut
     * wird. Nötig, damit eine veraltete `.bbl` (z.B. aus einem anderen Bib-System)
     * den nächsten Lauf nicht bricht: biblatex bricht sonst mit „File 'document.bbl'
     * not created by biblatex" ab. PDF, SyncTeX, das Tectonic-Bundle/den Cache und
     * die Projektquellen lassen wir bewusst stehen (Neuaufbau wäre teuer).
     *
     * Der Hauptinput heißt nativ immer `document.tex`, daher alle Namen `document.*`.
     */
    private fun cleanAuxArtifacts(jobDir: File) {
        AUX_EXTENSIONS.forEach { ext -> File(jobDir, "document.$ext").delete() }
        File(jobDir, "document-blx.bib").delete() // biblatex-Kontrolldatei
    }

    private val AUX_EXTENSIONS = listOf(
        "aux", "bbl", "blg", "bcf", "run.xml",
        "toc", "out", "nav", "snm", "lof", "lot",
        "idx", "ilg", "ind",
    )
}

package de.bgg_home.texdroid.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Eine Datei/ein Unterordner im Projektbaum. */
data class ProjectEntry(
    val uri: Uri,
    val name: String,
    val isDir: Boolean,
) {
    val isTex: Boolean get() = !isDir && name.endsWith(".tex", ignoreCase = true)
}

/**
 * Projektebene über dem SAF: ein vom Nutzer gewählter Ordner (Tree-Uri) mit
 * mehreren Dateien. Damit lassen sich mehrteilige LaTeX-Projekte (Haupt- +
 * `\input`-Dateien) navigieren und – ab QW 4.2 – gemeinsam kompilieren.
 */
object ProjectStore {

    /** Name des gewählten Ordners (für die Sidebar-Überschrift). */
    fun folderName(context: Context, treeUri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return DocumentStore.displayName(context, docUri)
    }

    /**
     * Listet die direkten Kinder von [treeUri] (eine Ebene), alphabetisch:
     * Ordner zuerst, dann Dateien. Läuft auf [Dispatchers.IO].
     */
    suspend fun list(context: Context, treeUri: Uri): List<ProjectEntry> =
        listChildren(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    /** Wie [list], aber für einen Unterordner (dessen Dokument-Uri). */
    suspend fun listSubdir(context: Context, treeUri: Uri, dirDocumentUri: Uri): List<ProjectEntry> =
        listChildren(context, treeUri, DocumentsContract.getDocumentId(dirDocumentUri))

    /**
     * Prüft, ob die (per ACTION_OPEN_DOCUMENT einzeln geöffnete) Datei [fileUri]
     * innerhalb des Projektbaums [treeUri] liegt. Grundlage ist der Dokument-Id:
     * Tree- und Datei-Id kodieren bei den gängigen Providern (v.a.
     * ExternalStorageProvider) den Pfad, z.B. `primary:Documents/proj` bzw.
     * `primary:Documents/proj/kap/x.tex`. „Innerhalb" heißt also: gleicher
     * Provider und die Datei-Id ist die Tree-Id selbst oder beginnt mit
     * `Tree-Id + "/"`.
     *
     * Bei opaken Dokument-Ids (manche Cloud-Provider) schlägt der Vergleich
     * konservativ zu `false` fehl – dann wird lieber gewarnt als stillschweigend
     * das falsche Projekt kopiert.
     */
    fun isWithinTree(fileUri: Uri, treeUri: Uri): Boolean {
        if (fileUri.authority != treeUri.authority) return false
        val treeId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return false
        val fileId = runCatching { DocumentsContract.getDocumentId(fileUri) }.getOrNull()
            ?: return false
        return fileId == treeId || fileId.startsWith("$treeId/")
    }

    /**
     * Kopiert alle Dateien des Projektbaums (rekursiv, mit Unterordner-Struktur)
     * nach [destDir] – nötig für QW 4.2, damit `\input{...}`/`\include{...}` beim
     * Kompilieren im Arbeitsverzeichnis auf die Geschwisterdateien auflösen.
     *
     * Die aktuell im Editor bearbeitete Hauptdatei wird bewusst NICHT von hier
     * überschrieben: Tectonic schreibt seinen eigenen Quelltext (`document.tex`)
     * separat, sodass ungespeicherte Änderungen erhalten bleiben. Große Dateien
     * (> [maxBytes]) werden übersprungen, damit ein PDF/Bild-Ordner den Compile
     * nicht ausbremst. Läuft auf [Dispatchers.IO].
     */
    suspend fun syncToDir(
        context: Context,
        treeUri: Uri,
        destDir: File,
        maxBytes: Long = 20L * 1024 * 1024,
    ): Unit = withContext(Dispatchers.IO) {
        copyLevel(context, treeUri, DocumentsContract.getTreeDocumentId(treeUri), destDir, maxBytes)
    }

    /**
     * Wie [syncToDir], kopiert aber den Teilbaum ab dem **Ordner der Hauptdatei**
     * [mainFileUri] in die Wurzel von [destDir] – nicht ab der Tree-Wurzel.
     *
     * Nötig, weil Tectonic seinen Quelltext immer als `document.tex` in die
     * Job-Wurzel schreibt: liegt das Projekt in einem Unterordner des gewählten
     * Baums (z.B. Tree `Documents/`, Hauptdatei `Documents/texproj/x.tex`), muss
     * die daneben liegende `.cls`/`\input`-Datei ebenfalls in der Wurzel landen –
     * sonst findet der Compiler sie nicht. Liegt die Hauptdatei direkt in der
     * Tree-Wurzel (Elternordner == Tree), ist das Ergebnis identisch mit
     * [syncToDir]. Bei opaken Dokument-Ids (manche Cloud-Provider) fällt es
     * konservativ auf die Tree-Wurzel zurück.
     */
    suspend fun syncProjectOf(
        context: Context,
        treeUri: Uri,
        mainFileUri: Uri,
        destDir: File,
        maxBytes: Long = 20L * 1024 * 1024,
    ): Unit = withContext(Dispatchers.IO) {
        val treeId = DocumentsContract.getTreeDocumentId(treeUri)
        val baseId =
            if (isWithinTree(mainFileUri, treeUri)) {
                val fileId = runCatching { DocumentsContract.getDocumentId(mainFileUri) }.getOrNull()
                if (fileId != null) baseDocIdWithin(treeId, fileId) else treeId
            } else {
                treeId
            }
        copyLevel(context, treeUri, baseId, destDir, maxBytes)
    }

    /**
     * Reine Pfad-Logik (JVM-testbar): Dokument-Id des Ordners, der die Datei mit
     * [fileId] enthält – aber nie oberhalb der Tree-Wurzel [treeId]. Grundlage ist
     * die Pfad-Kodierung der Dokument-Id (`primary:a/b/c` → Elternordner
     * `primary:a/b`) der gängigen Provider (v.a. ExternalStorageProvider). Enthält
     * die Id kein `/` (Datei unerwartet oberste Ebene) oder läge der Elternordner
     * außerhalb des Baums, wird die Tree-Wurzel geliefert.
     */
    internal fun baseDocIdWithin(treeId: String, fileId: String): String {
        val slash = fileId.lastIndexOf('/')
        if (slash < 0) return treeId
        val parentId = fileId.substring(0, slash)
        return if (parentId == treeId || parentId.startsWith("$treeId/")) parentId else treeId
    }

    private fun copyLevel(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
        destDir: File,
        maxBytes: Long,
    ) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    val sub = File(destDir, name).apply { mkdirs() }
                    copyLevel(context, treeUri, docId, sub, maxBytes)
                    continue
                }
                if (!c.isNull(3) && c.getLong(3) > maxBytes) continue
                val src = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                runCatching {
                    context.contentResolver.openInputStream(src)?.use { input ->
                        File(destDir, name).outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
    }

    private suspend fun listChildren(
        context: Context,
        treeUri: Uri,
        parentDocId: String,
    ): List<ProjectEntry> = withContext(Dispatchers.IO) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val out = ArrayList<ProjectEntry>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                out += ProjectEntry(uri, name, isDir)
            }
        }
        out.sortedWith(compareByDescending<ProjectEntry> { it.isDir }.thenBy { it.name.lowercase() })
    }
}

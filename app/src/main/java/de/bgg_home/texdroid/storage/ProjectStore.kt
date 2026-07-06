package de.bgg_home.texdroid.storage

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

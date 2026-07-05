package de.bgg_home.texdroid.synctex

import java.io.File

/**
 * SyncTeX-Grundgerüst (M3-Vorbereitung).
 *
 * Die native Seite erzeugt bereits eine `document.synctex.gz` (siehe
 * `ProcessingSessionBuilder.synctex(true)` in rust/src/lib.rs). Diese Datei bildet
 * Quelltext-Positionen auf PDF-Positionen ab und umgekehrt – die Grundlage für:
 *   • Forward Search:  Editor-Cursor (Datei/Zeile) → Stelle im PDF.
 *   • Inverse Search:  Tap ins PDF (Seite/x/y) → Editor-Zeile (Jump-to-Error/Source).
 *
 * Hier ist NUR die Schnittstelle + Datenstruktur vorbereitet. Das eigentliche
 * Parsen des SyncTeX-Formats (gzip → Records: Input `i`, Sheet `{`, Box `[`, `(`,
 * Kern-Records `x`,`k`,`g` …, mit „Units": 65536 sp = 1 pt) kommt in M3.
 */

/** Ein Punkt in einer PDF-Seite (Ursprung links-oben, Einheit: PDF-Punkte). */
data class PdfPoint(
    val page: Int,   // 1-basierte Seitennummer
    val x: Float,
    val y: Float,
    val width: Float = 0f,
    val height: Float = 0f,
)

/** Eine Stelle im Quelltext. */
data class SourceLocation(
    val file: String,  // Eingabedatei (SyncTeX referenziert per Input-ID)
    val line: Int,     // 1-basierte Zeile
    val column: Int = 0,
)

/**
 * Ein geparster SyncTeX-Datensatz: die Zuordnungen zwischen Quelltext-Boxen und
 * PDF-Boxen. In M3 gefüllt aus `document.synctex.gz`.
 */
data class SyncTexData(
    val unitsPerPoint: Int = 65536,               // SyncTeX rechnet in „sp" (scaled points)
    val records: List<SyncTexRecord> = emptyList(),
)

/** Verknüpft eine Quelltext-Zeile mit einer Box im PDF. */
data class SyncTexRecord(
    val source: SourceLocation,
    val pdf: PdfPoint,
)

/**
 * Schnittstelle für Forward-/Inverse-Search. In M3 von einer echten
 * Implementierung erfüllt; hier ist die API festgezurrt, damit UI-Code
 * (Editor ↔ Preview) schon dagegen gebaut werden kann.
 */
interface SyncTexIndex {
    /** Forward: von einer Quelltext-Stelle zur wahrscheinlichsten PDF-Position. */
    fun forwardSearch(location: SourceLocation): PdfPoint?

    /** Inverse: von einem PDF-Tap zur wahrscheinlichsten Quelltext-Stelle. */
    fun inverseSearch(point: PdfPoint): SourceLocation?
}

/**
 * Stub-Parser. Liest die `.synctex.gz` noch NICHT aus, sondern liefert einen
 * leeren Index. Nur die Signatur ist final. TODO(M3): echtes SyncTeX-Parsing.
 */
object SyncTexParser {

    /**
     * Parst [synctexGz] (die von Tectonic erzeugte `document.synctex.gz`).
     * Aktuell: gibt einen leeren Index zurück, wenn die Datei existiert, sonst null.
     */
    fun parse(synctexGz: File?): SyncTexIndex? {
        if (synctexGz == null || !synctexGz.exists()) return null
        // TODO(M3): GZIPInputStream öffnen, Records parsen, Index aufbauen.
        return EmptySyncTexIndex
    }

    private object EmptySyncTexIndex : SyncTexIndex {
        override fun forwardSearch(location: SourceLocation): PdfPoint? = null
        override fun inverseSearch(point: PdfPoint): SourceLocation? = null
    }
}

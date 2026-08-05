package de.bgg_home.texdroid.synctex

import java.io.File
import java.util.zip.GZIPInputStream
import kotlin.math.abs

/**
 * SyncTeX: die Brücke zwischen Quelltext und gesetztem PDF.
 *
 * Die Engine schreibt bei jedem Compile eine `document.synctex.gz` (siehe
 * `ProcessingSessionBuilder.synctex(true)` in `rust/src/lib.rs`). Darin steht, welche
 * Kiste im PDF aus welcher Quelltext-Zeile entstanden ist. Damit geht:
 *   • Inverse Search: Tipp ins PDF (Seite/x/y) → Editor-Zeile.
 *   • Forward Search: Editor-Cursor (Datei/Zeile) → Stelle im PDF.
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
    val file: String,  // Eingabedatei, wie SyncTeX sie nennt (relativ zum Arbeitsverzeichnis)
    val line: Int,     // 1-basierte Zeile
) {
    /**
     * Ist das die Hauptdatei? Nur für sie entspricht die Zeilennummer dem, was
     * gerade im Editor steht.
     *
     * Zwei Namen, weil die Engine sie unterschiedlich benennt: Auf dem Gerät
     * reicht Tectonic den Editor-Inhalt als **primäre Eingabe** durch, und die
     * heißt in der SyncTeX-Datei `texput` (TeX' Vorgabename, wenn die Quelle
     * nicht aus einer Datei kommt) – am Gerät nachgesehen, 02.08.2026. Wird
     * dagegen eine echte Datei kompiliert, steht dort `document.tex`, wie die
     * native Seite sie ablegt (siehe [de.bgg_home.texdroid.compile.LatexCompiler]).
     */
    val isMainDocument: Boolean
        get() = fileName.substringBeforeLast('.') in MAIN_INPUT_NAMES

    /** Reiner Dateiname ohne Ordner – für Meldungen an den Nutzer. */
    val fileName: String get() = file.substringAfterLast('/')

    companion object {
        private val MAIN_INPUT_NAMES = setOf("texput", "document")
    }
}

/** Schnittstelle für Forward-/Inverse-Search. */
interface SyncTexIndex {
    /** Forward: von einer Quelltext-Stelle zur wahrscheinlichsten PDF-Position. */
    fun forwardSearch(location: SourceLocation): PdfPoint?

    /** Inverse: von einem PDF-Tap zur wahrscheinlichsten Quelltext-Stelle. */
    fun inverseSearch(point: PdfPoint): SourceLocation?
}

/**
 * Liest das SyncTeX-Format (Version 1).
 *
 * Aufbau: ein Vorspann mit `Input:<tag>:<pfad>`-Zeilen und Maßangaben, danach ab
 * `Content:` je eine Zeile pro Datensatz. Das erste Zeichen sagt, worum es sich
 * handelt – `{`/`}` klammern eine Seite, `[`/`(` öffnen eine vertikale bzw.
 * horizontale Kiste, `v`/`h`/`r` sind Kisten ohne Inhalt, `x`/`k`/`g`/`$` einzelne
 * Positionen. Der Rest der Zeile ist immer `tag,zeile:x,y`, optional gefolgt von
 * `:breite,höhe,tiefe`.
 */
object SyncTexParser {

    /**
     * SyncTeX rechnet in „scaled points": 65536 sp = 1 TeX-Punkt (1/72,27 Zoll).
     * PDF misst dagegen in „big points" (1/72 Zoll) – deshalb nicht durch 65536,
     * sondern durch 65536 · 72,27/72 teilen. Ein Zehntel Prozent klingt egal, wäre
     * am Fuß einer A4-Seite aber schon ein knapper Millimeter Versatz.
     */
    private const val SP_PER_PDF_POINT = 65781.76f

    /**
     * Wie stark der senkrechte Abstand gegenüber dem waagerechten zählt.
     *
     * Innerhalb einer gesetzten Zeile haben alle Datensätze dieselbe Grundlinie –
     * dort entscheidet allein x, welche Quellzeile gemeint ist. Zwischen Zeilen
     * muss dagegen die Nähe in y gewinnen, sonst zieht ein Tipp im Absatzabstand
     * eine weit entfernte Zeile an, die zufällig an derselben x-Position steht.
     * Vier ist grob eine Zeilenhöhe gegen ein Viertel Zeilenbreite.
     */
    private const val VERTICAL_WEIGHT = 4f

    /**
     * Obergrenze für die Zahl der Datensätze. Ein Buch erzeugt leicht ein paar
     * hunderttausend; ohne Deckel könnte eine extreme Datei den Speicher der App
     * füllen. Wird sie erreicht, bleibt der Index unvollständig, statt die App zu
     * killen – Inverse Search wird dann im hinteren Teil ungenau.
     */
    private const val MAX_RECORDS = 400_000

    /** Parst die von der Engine erzeugte `document.synctex.gz`. */
    fun parse(synctexGz: File?): SyncTexIndex? {
        if (synctexGz == null || !synctexGz.exists()) return null
        return runCatching {
            GZIPInputStream(synctexGz.inputStream().buffered()).bufferedReader().use { reader ->
                parse(reader.lineSequence())
            }
        }.getOrNull()
    }

    /**
     * Parst die **entpackten** Zeilen. Zeilenweise statt am Stück, weil die Datei
     * entpackt zweistellige Megabyte erreichen kann.
     */
    fun parse(lines: Sequence<String>): SyncTexIndex? {
        val inputs = HashMap<Int, String>()
        var unit = 1f
        var xOffset = 0f
        var yOffset = 0f
        var inContent = false
        var page = 0
        var pageStart = 0
        val records = RecordBuffer()
        val pageRanges = HashMap<Int, IntRange>()

        fun closePage() {
            if (page != 0 && records.size > pageStart) {
                pageRanges[page] = pageStart until records.size
            }
            page = 0
        }

        for (line in lines) {
            if (line.isEmpty()) continue
            // Input-Zeilen stehen im Vorspann, dürfen aber auch mitten im Inhalt
            // auftauchen (jede \input-Datei meldet sich beim Öffnen an).
            if (line.startsWith(INPUT_PREFIX)) {
                parseInput(line)?.let { (tag, path) -> inputs[tag] = path }
                continue
            }
            if (!inContent) {
                when {
                    line == "Content:" -> inContent = true
                    line.startsWith("Unit:") -> unit = line.numberAfterColon() ?: unit
                    line.startsWith("X Offset:") -> xOffset = line.numberAfterColon() ?: xOffset
                    line.startsWith("Y Offset:") -> yOffset = line.numberAfterColon() ?: yOffset
                }
                continue
            }
            if (line.startsWith("Postamble:")) break

            when (line[0]) {
                // Seitenklammern: `{3` beginnt Seite 3, `}3` beendet sie.
                '{' -> {
                    closePage()
                    page = line.drop(1).trim().toIntOrNull() ?: 0
                    pageStart = records.size
                }
                '}' -> closePage()
                // Alle Datensätze mit Position. Die schließenden `]` und `)` tragen
                // keine, `!` ist ein Zähl-Hinweis, `<`/`>` klammern Formulare.
                in RECORD_TYPES -> {
                    if (page != 0 && records.size < MAX_RECORDS) {
                        addRecord(records, line, unit, xOffset, yOffset)
                    }
                }
                else -> Unit
            }
        }
        closePage()
        if (pageRanges.isEmpty()) return null
        return ParsedIndex(inputs, records, pageRanges)
    }

    private const val INPUT_PREFIX = "Input:"

    /**
     * Datensatz-Typen mit Position. Zwei Gruppen, und der Unterschied entscheidet
     * über die Treffsicherheit:
     *
     * • **Inhalt** ([CONTENT_TYPES]): `g` (Leim), `k` (Kern), `x` (Position),
     *   `$` (Mathe) und `h`/`v` (Kisten ohne aufgezeichneten Inhalt) – hier steht
     *   wirklich etwas, und die Zeilennummer ist die des gesetzten Textes. `h`
     *   gehört dazu, weil eine kurze Zeile („Kurz.") komplett als eine solche
     *   Kiste erscheint: ohne sie hätte diese Zeile gar keinen Inhalt.
     * • **Struktur**: `[`/`(` (Kisten mit Inhalt) und `r` (Linien) – sie
     *   umschließen etwas. Ihre Zeilennummer ist die Stelle, an der die Kiste
     *   *geschlossen* wurde: die Kiste um einen Absatz trägt die Leerzeile danach.
     *   Als Antwort auf einen Tipp taugen sie darum nur im Notfall.
     *
     * ⚠️ Bekannte Grenze: Die leere Kopfzeilen-Kiste ist ebenfalls ein `h` und
     * trägt `\end{document}`. Ein Tipp in den **oberen Seitenrand**, oberhalb der
     * ersten Textzeile, landet deshalb am Dokumentende. Der Streifen ist schmal
     * und enthält nichts, worauf man zielen würde – der Fehlgriff wäre es wert
     * gewesen, wenn dafür jede kurze Zeile ins Leere liefe.
     */
    private const val CONTENT_TYPES = "xkg\$hv"
    private const val STRUCTURE_TYPES = "[(r"
    private const val RECORD_TYPES = STRUCTURE_TYPES + CONTENT_TYPES

    /** `Input:12:./kapitel/eins.tex` → 12 zu `kapitel/eins.tex`. */
    private fun parseInput(line: String): Pair<Int, String>? {
        val rest = line.substring(INPUT_PREFIX.length)
        // Nur am ERSTEN Doppelpunkt trennen: absolute Pfade dürfen selbst welche
        // enthalten, und der Rest der Zeile ist immer komplett der Pfad.
        val colon = rest.indexOf(':')
        if (colon <= 0) return null
        val tag = rest.substring(0, colon).trim().toIntOrNull() ?: return null
        val path = rest.substring(colon + 1).trim().removePrefix("./")
        if (path.isEmpty()) return null
        return tag to path
    }

    private fun String.numberAfterColon(): Float? =
        substringAfter(':').trim().toFloatOrNull()

    /**
     * Zerlegt eine Inhalts-Zeile (`<typ>tag,zeile[,spalte]:x,y[:breite[,höhe,tiefe]]`)
     * und legt sie in [target] ab. Fehlt etwas oder ist es unlesbar, wird der
     * Datensatz verworfen statt geraten – eine falsche Zuordnung wäre schlimmer
     * als eine fehlende.
     */
    private fun addRecord(
        target: RecordBuffer,
        line: String,
        unit: Float,
        xOffset: Float,
        yOffset: Float,
    ) {
        val body = line.substring(1)
        val firstColon = body.indexOf(':')
        if (firstColon <= 0) return
        val ids = body.substring(0, firstColon)
        val comma = ids.indexOf(',')
        if (comma <= 0) return
        val tag = ids.substring(0, comma).trim().toIntOrNull() ?: return
        // Nach der Zeile darf noch eine Spalte folgen – die interessiert hier nicht.
        val lineEnd = ids.indexOf(',', comma + 1).takeIf { it > 0 } ?: ids.length
        val srcLine = ids.substring(comma + 1, lineEnd).trim().toIntOrNull() ?: return

        val rest = body.substring(firstColon + 1)
        val secondColon = rest.indexOf(':')
        val position = if (secondColon < 0) rest else rest.substring(0, secondColon)
        val posComma = position.indexOf(',')
        if (posComma <= 0) return
        val h = position.substring(0, posComma).trim().toIntOrNull() ?: return
        val v = position.substring(posComma + 1).trim().toIntOrNull() ?: return

        // `breite,höhe,tiefe` – von Hand zerlegt wie tag/zeile oben: split()
        // hieße eine Wegwerf-Liste pro Datensatz, hunderttausendfach pro Parse.
        val extents = IntArray(3)
        if (secondColon >= 0) {
            var start = secondColon + 1
            for (slot in extents.indices) {
                if (start > rest.length) break
                val end = rest.indexOf(',', start).let { if (it < 0) rest.length else it }
                extents[slot] = rest.substring(start, end).trim().toIntOrNull() ?: 0
                start = end + 1
            }
        }
        val width = extents[0]
        val height = extents[1]
        val depth = extents[2]

        // `v` ist die Grundlinie; die Kiste reicht um ihre Höhe nach oben und um
        // ihre Tiefe nach unten. Negative Breiten kommen vor (rückwärts gesetzte
        // Kisten) – dann liegt die linke Kante links vom Bezugspunkt.
        val x = (h * unit + xOffset) / SP_PER_PDF_POINT
        val baseline = (v * unit + yOffset) / SP_PER_PDF_POINT
        val w = width * unit / SP_PER_PDF_POINT
        target.add(
            tag = tag,
            line = srcLine,
            content = line[0] in CONTENT_TYPES,
            left = if (w < 0f) x + w else x,
            top = baseline - height * unit / SP_PER_PDF_POINT,
            width = abs(w),
            height = (height + depth) * unit / SP_PER_PDF_POINT,
        )
    }

    /**
     * Datensätze in parallelen Primitiv-Arrays statt als Objekte: ein Buch bringt
     * es auf mehrere hunderttausend Einträge, als Objekte wären das gut und gern
     * 25 MB Heap. So sind es 24 Byte pro Datensatz ohne Objekt-Overhead.
     */
    private class RecordBuffer {
        var size = 0
            private set
        private var tag = IntArray(INITIAL)
        private var line = IntArray(INITIAL)
        private var content = BooleanArray(INITIAL)
        private var left = FloatArray(INITIAL)
        private var top = FloatArray(INITIAL)
        private var width = FloatArray(INITIAL)
        private var height = FloatArray(INITIAL)

        fun add(
            tag: Int,
            line: Int,
            content: Boolean,
            left: Float,
            top: Float,
            width: Float,
            height: Float,
        ) {
            if (size == this.tag.size) grow()
            this.tag[size] = tag
            this.line[size] = line
            this.content[size] = content
            this.left[size] = left
            this.top[size] = top
            this.width[size] = width
            this.height[size] = height
            size++
        }

        fun tag(i: Int) = tag[i]
        fun line(i: Int) = line[i]
        fun isContent(i: Int) = content[i]
        fun left(i: Int) = left[i]
        fun right(i: Int) = left[i] + width[i]
        fun top(i: Int) = top[i]
        fun width(i: Int) = width[i]
        fun height(i: Int) = height[i]

        /** Hat der Datensatz eine Fläche? Nur solche können einen Punkt umschließen. */
        fun isBox(i: Int) = width[i] > 0f && height[i] > 0f

        /** Waagerechter Abstand zum Datensatz (0, wenn x darüber liegt). */
        fun horizontalDistance(i: Int, x: Float): Float = when {
            x < left[i] -> left[i] - x
            x > left[i] + width[i] -> x - (left[i] + width[i])
            else -> 0f
        }

        /** Senkrechter Abstand zum Datensatz (0, wenn y darauf liegt). */
        fun verticalDistance(i: Int, y: Float): Float = when {
            y < top[i] -> top[i] - y
            y > top[i] + height[i] -> y - (top[i] + height[i])
            else -> 0f
        }

        /** Liegt Datensatz [i] innerhalb der Kiste [box]? Ränder zählen dazu. */
        fun liesWithin(i: Int, box: Int): Boolean =
            left[i] >= left[box] && left[i] + width[i] <= left[box] + width[box] &&
                top[i] >= top[box] && top[i] + height[i] <= top[box] + height[box]

        private fun grow() {
            val n = size * 2
            tag = tag.copyOf(n)
            line = line.copyOf(n)
            content = content.copyOf(n)
            left = left.copyOf(n)
            top = top.copyOf(n)
            width = width.copyOf(n)
            height = height.copyOf(n)
        }

        private companion object {
            const val INITIAL = 2048
        }
    }

    private class ParsedIndex(
        private val inputs: Map<Int, String>,
        private val records: RecordBuffer,
        private val pageRanges: Map<Int, IntRange>,
    ) : SyncTexIndex {

        /**
         * Sucht den Datensatz, der dem Tipp am nächsten liegt – zuerst unter den
         * **Positionen** (einzelne Glyphengruppen, Leim, Kerne), erst danach unter
         * den Kisten.
         *
         * Warum nicht einfach die umschließende Kiste nehmen: Die horizontale Kiste
         * eines Absatzes trägt die Zeile, in der der Absatz **endet** – also oft die
         * Leerzeile danach. Der Text darin trägt dagegen die Zeile, aus der er
         * wirklich stammt. Und ein Absatz aus fünf Quellzeilen wird zu gesetzten
         * Zeilen, die mittendrin von der einen zur nächsten Quellzeile wechseln;
         * nur die Positionen lösen das auf.
         */
        override fun inverseSearch(point: PdfPoint): SourceLocation? {
            val range = pageRanges[point.page] ?: return null
            // Die Kiste grenzt die Suche ein: Sie ist immer satzbreit, auch wenn
            // die Zeile darin kurz ist. Ohne sie zöge ein Tipp rechts neben einer
            // kurzen Zeile die längere Zeile darunter an – dort steht ja Text
            // näher am Finger.
            val box = smallestContaining(range, point)
            // Erst ohne die Datensätze, die dieselbe Zeile wie die Kiste tragen:
            // Das ist die Füllglue am Zeilenende, die die Zeile des Absatzendes
            // mitbringt. Bei „Kurz." in einer satzbreiten Zeile säße sie sonst
            // näher am Finger als das Wort selbst.
            var hit = nearest(range, point, within = box, skipBoxLine = true)
            if (hit < 0) hit = nearest(range, point, within = box, skipBoxLine = false)
            // Kein Inhalt in der Kiste (etwa eine ganzseitige Abbildung): dann
            // lieber deren Zeile als gar kein Sprung.
            if (hit < 0) hit = box
            if (hit < 0) return null
            val file = inputs[records.tag(hit)] ?: return null
            return SourceLocation(file = file, line = records.line(hit))
        }

        /** Kleinste Kiste, die den Punkt umschließt – oder -1. */
        private fun smallestContaining(range: IntRange, point: PdfPoint): Int {
            var best = -1
            var bestArea = Float.MAX_VALUE
            for (i in range) {
                if (!records.isBox(i)) continue
                if (records.horizontalDistance(i, point.x) > 0f) continue
                if (records.verticalDistance(i, point.y) > 0f) continue
                val area = records.width(i) * records.height(i)
                if (area < bestArea) {
                    bestArea = area
                    best = i
                }
            }
            return best
        }

        /**
         * Nächstgelegener **Inhalts**-Datensatz. Ist [within] gesetzt, kommen nur
         * Datensätze innerhalb dieser Kiste in Frage.
         */
        private fun nearest(
            range: IntRange,
            point: PdfPoint,
            within: Int,
            skipBoxLine: Boolean,
        ): Int {
            var best = -1
            var bestScore = Float.MAX_VALUE
            for (i in range) {
                if (!records.isContent(i)) continue
                if (within >= 0) {
                    if (i == within) continue // die Kiste ist nicht ihr eigener Inhalt
                    if (!records.liesWithin(i, within)) continue
                    if (skipBoxLine && records.line(i) == records.line(within)) continue
                    // Die Füllglue, die eine kurze Zeile bis zum rechten Rand
                    // auffüllt, sitzt genau auf der Satzkante und trägt schon die
                    // Zeile des Absatz*endes*. Sie darf den letzten echten
                    // Buchstaben der Zeile nicht überstimmen.
                    if (records.left(i) >= records.right(within)) continue
                }
                val dx = records.horizontalDistance(i, point.x)
                val dy = records.verticalDistance(i, point.y) * VERTICAL_WEIGHT
                val score = dx * dx + dy * dy
                if (score < bestScore) {
                    bestScore = score
                    best = i
                }
            }
            return best
        }

        override fun forwardSearch(location: SourceLocation): PdfPoint? {
            val tags = inputs.filterValues { it.matchesFile(location.file) }.keys
            if (tags.isEmpty()) return null
            var exact = -1
            var exactPage = Int.MAX_VALUE
            var nearest = -1
            var nearestPage = 0
            var nearestDistance = Int.MAX_VALUE
            for ((page, range) in pageRanges) {
                for (i in range) {
                    if (records.tag(i) !in tags) continue
                    if (records.line(i) == location.line) {
                        // Erste Fundstelle auf der frühesten Seite gewinnt.
                        if (exact < 0 || page < exactPage) {
                            exact = i
                            exactPage = page
                        }
                    } else if (exact < 0) {
                        val d = abs(records.line(i) - location.line)
                        if (d < nearestDistance) {
                            nearestDistance = d
                            nearest = i
                            nearestPage = page
                        }
                    }
                }
            }
            val hit = if (exact >= 0) exact else nearest
            if (hit < 0) return null
            return PdfPoint(
                page = if (exact >= 0) exactPage else nearestPage,
                x = records.left(hit),
                y = records.top(hit),
                width = records.width(hit),
                height = records.height(hit),
            )
        }

        /** Pfade vergleichen, ohne über führende `./` oder Ordner zu stolpern. */
        private fun String.matchesFile(other: String): Boolean {
            val a = removePrefix("./")
            val b = other.removePrefix("./")
            return a == b || a.endsWith("/$b") || b.endsWith("/$a")
        }
    }
}

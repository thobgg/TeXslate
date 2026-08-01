package de.bgg_home.texdroid.compile

/**
 * Baut das Stichwortverzeichnis — der Ersatz für das Programm `makeindex`, das auf
 * dem Gerät nicht zur Verfügung steht.
 *
 * Tectonic fährt `tex → bibtex/biber → tex`, aber keinen Index-Durchlauf. LaTeX
 * schreibt seine Einträge zwar brav in `document.idx`, doch ohne das daraus
 * erzeugte `document.ind` bleibt `\printindex` **still leer** — der Compile meldet
 * Erfolg, und im PDF fehlt das Verzeichnis. Genau diese lautlose Falschausgabe
 * wollen wir nicht (auf dem Tab S5e nachgewiesen, 31.07.2026).
 *
 * Bewusst in Kotlin statt als weiteres cross-kompiliertes Binary: die Umwandlung
 * ist Sortieren und Gruppieren, kein Hexenwerk — und so funktioniert sie in beiden
 * Editionen, ohne Lizenzfragen und ohne die APK zu vergrößern.
 *
 * Unterstützt die Schreibweisen, die in echten Dokumenten vorkommen:
 *
 * | Eingabe | Bedeutung |
 * |---|---|
 * | `\indexentry{Zebra}{7}` | einfacher Eintrag |
 * | `\indexentry{Tier!Zebra}{7}` | Unterpunkt (bis drei Ebenen) |
 * | `\indexentry{alpha@$\alpha$}{7}` | Sortierschlüssel vor `@`, Anzeige dahinter |
 * | `\indexentry{Zebra\|textbf}{7}` | Seitenzahl wird `\textbf{7}` |
 * | `\indexentry{Zebra\|(}{7}` … `\|)}{9}` | Seitenbereich `7--9` |
 *
 * Nicht unterstützt: eigene Stildateien (`.ist`). Deren Feinheiten (andere
 * Trennzeichen, Überschriften) blieben unberücksichtigt — das Verzeichnis entsteht
 * trotzdem, nur im Standardlayout.
 */
object MakeIndex {

    /** `\indexentry{…}{seite}` — Gruppe 1 = Eintrag, Gruppe 2 = Seite. */
    private val ENTRY = Regex("""\\indexentry\s*\{(.*)\}\s*\{([^\{\}]*)\}""")

    private data class Item(
        val sortKeys: List<String>,
        val displays: List<String>,
        val page: String,
        val encap: String?,
        val rangeStart: Boolean,
        val rangeEnd: Boolean,
    )

    /**
     * Erzeugt den Inhalt der `.ind`-Datei aus dem Inhalt der `.idx`-Datei.
     *
     * @return das fertige `theindex`-Environment, oder null wenn es nichts zu
     *   setzen gibt (keine Einträge) — dann soll der Aufrufer auch nichts schreiben.
     */
    fun build(idx: String): String? {
        val items = idx.lineSequence().mapNotNull { parse(it) }.toList()
        if (items.isEmpty()) return null

        // Nach Ebenen gruppieren: Schlüsselpfad → Seitenliste, Reihenfolge stabil.
        val grouped = LinkedHashMap<List<String>, MutableList<Item>>()
        items.forEach { grouped.getOrPut(it.sortKeys) { mutableListOf() }.add(it) }

        val sorted = grouped.entries.sortedWith(
            compareBy(CompareKeys) { it.key },
        )

        val out = StringBuilder("\\begin{theindex}\n")
        var lastGroup: Char? = null
        var lastPath = emptyList<String>()

        sorted.forEach { (keys, entries) ->
            val group = keys.first().firstOrNull()?.uppercaseChar()
            if (lastGroup != null && group != lastGroup) out.append("\n  \\indexspace\n")
            lastGroup = group

            // Fehlende Zwischenebenen ergänzen, damit \subitem nie ohne \item steht.
            keys.indices.forEach { level ->
                val path = keys.subList(0, level + 1)
                if (level < lastPath.size && lastPath.subList(0, level + 1) == path) return@forEach
                val display = entries.first().displays.getOrElse(level) { path[level] }
                val command = when (level) {
                    0 -> "\\item "
                    1 -> "  \\subitem "
                    else -> "    \\subsubitem "
                }
                out.append(command).append(display)
                if (level == keys.lastIndex) out.append(pages(entries))
                out.append('\n')
            }
            lastPath = keys
        }
        out.append("\n\\end{theindex}\n")
        return out.toString()
    }

    /** Seitenangaben eines Eintrags: sortiert, ohne Dubletten, Bereiche zusammengefasst. */
    private fun pages(entries: List<Item>): String {
        val parts = mutableListOf<String>()
        var openRange: Item? = null
        entries.forEach { e ->
            when {
                e.rangeStart -> openRange = e
                e.rangeEnd -> {
                    val start = openRange
                    openRange = null
                    parts += if (start != null && start.page != e.page) {
                        format("${start.page}--${e.page}", e.encap ?: start.encap)
                    } else {
                        format(e.page, e.encap)
                    }
                }
                else -> parts += format(e.page, e.encap)
            }
        }
        openRange?.let { parts += format(it.page, it.encap) }
        val unique = parts.distinct().sortedWith(PageOrder)
        return if (unique.isEmpty()) "" else ", " + unique.joinToString(", ")
    }

    private fun format(page: String, encap: String?): String =
        if (encap.isNullOrBlank()) page else "\\$encap{$page}"

    private fun parse(line: String): Item? {
        val m = ENTRY.find(line.trim()) ?: return null
        var entry = m.groupValues[1]
        val page = m.groupValues[2].trim()

        var encap: String? = null
        var rangeStart = false
        var rangeEnd = false
        val bar = topLevelIndex(entry, '|')
        if (bar >= 0) {
            val spec = entry.substring(bar + 1).trim()
            entry = entry.substring(0, bar)
            when {
                spec.startsWith("(") -> { rangeStart = true; encap = spec.drop(1).ifBlank { null } }
                spec.startsWith(")") -> { rangeEnd = true; encap = spec.drop(1).ifBlank { null } }
                else -> encap = spec.ifBlank { null }
            }
        }

        val levels = splitTopLevel(entry, '!')
        if (levels.isEmpty()) return null
        val sortKeys = mutableListOf<String>()
        val displays = mutableListOf<String>()
        levels.forEach { level ->
            val at = topLevelIndex(level, '@')
            if (at >= 0) {
                sortKeys += level.substring(0, at)
                displays += level.substring(at + 1)
            } else {
                sortKeys += level
                displays += level
            }
        }
        return Item(sortKeys, displays, page, encap, rangeStart, rangeEnd)
    }

    /**
     * Position von [ch] außerhalb von `{}`, nicht hinter `\` und nicht hinter dem
     * Maskierzeichen `"`.
     *
     * Das `"` ist makeindex' Quote-Zeichen: `"@` meint ein **wörtliches** @, kein
     * Sortier-Trennzeichen. Ohne diese Regel zerfetzt der Parser Einträge wie
     * `"@\texttt{…}` — bei lshort entstand daraus eine `.ind`, die LaTeX mit
     * „\verb ended by end of line" abbrach (auf dem Gerät beobachtet, 01.08.2026).
     */
    private fun topLevelIndex(s: String, ch: Char): Int {
        var depth = 0
        var i = 0
        while (i < s.length) {
            val c = s[i]
            when {
                c == '\\' -> i++
                c == '"' -> i++ // makeindex-Maskierung: nächstes Zeichen ist wörtlich
                c == '{' -> depth++
                c == '}' -> depth--
                c == ch && depth == 0 -> return i
            }
            i++
        }
        return -1
    }

    private fun splitTopLevel(s: String, ch: Char): List<String> {
        val parts = mutableListOf<String>()
        var rest = s
        while (true) {
            val i = topLevelIndex(rest, ch)
            if (i < 0) break
            parts += rest.substring(0, i)
            rest = rest.substring(i + 1)
        }
        parts += rest
        return parts.filter { it.isNotEmpty() }
    }

    /** Einträge alphabetisch, Groß-/Kleinschreibung egal — wie makeindex es tut. */
    private object CompareKeys : Comparator<List<String>> {
        override fun compare(a: List<String>, b: List<String>): Int {
            a.indices.forEach { i ->
                if (i >= b.size) return 1
                val c = a[i].compareTo(b[i], ignoreCase = true)
                if (c != 0) return c
            }
            return if (b.size > a.size) -1 else 0
        }
    }

    /** Seiten numerisch, sonst alphabetisch (römische Ziffern bleiben beieinander). */
    private object PageOrder : Comparator<String> {
        override fun compare(a: String, b: String): Int {
            val na = a.takeWhile { it.isDigit() }.toIntOrNull()
            val nb = b.takeWhile { it.isDigit() }.toIntOrNull()
            return when {
                na != null && nb != null -> na.compareTo(nb)
                na != null -> -1
                nb != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}

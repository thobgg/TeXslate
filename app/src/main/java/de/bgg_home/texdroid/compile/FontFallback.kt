package de.bgg_home.texdroid.compile

import java.io.File

/**
 * Ersatzschriften für `\setmainfont{…}` & Co.
 *
 * Auf einem Tablet steht nur eine Handvoll Schriften bereit: die mitgelieferten
 * TeX-Familien, DejaVu Sans und die Android-Systemschriften. Dokumente von PCs
 * verlangen aber routinemäßig Arial, Times New Roman, Calibri, Consolas … — ohne
 * Ersatz bricht der Compile ab, obwohl das Dokument sonst fehlerfrei ist.
 *
 * Darum: Fehlt eine Schrift, wird ihr Name in der **Compile-Kopie** des Quelltexts
 * (nie in der Datei des Nutzers) durch eine mitgelieferte Familie ersetzt und neu
 * kompiliert – mit Hinweis, welche Schrift ersetzt wurde. Die Zuordnung ist
 * kuratiert (gleiche Klasse, ähnliche Anmutung), für unbekannte Namen greift eine
 * Heuristik über Namen und Befehl.
 *
 * Bewusst als Textersetzung und nicht über fontconfig-Aliase: XeTeX baut seine
 * Namenstabelle selbst aus `FcFontList` auf, und fontconfig wird pro Prozess nur
 * einmal initialisiert (siehe FontStore) – eine nachträglich geänderte `fonts.conf`
 * würde im laufenden Prozess also nicht greifen. Die Textersetzung wirkt sofort.
 *
 * Angenehmer Nebeneffekt: Zeilennummern bleiben gültig, weil nur der Name innerhalb
 * der Klammern getauscht wird.
 */
object FontFallback {

    private const val SERIF = "Latin Modern Roman"
    private const val SANS = "Latin Modern Sans"
    private const val MONO = "Latin Modern Mono"
    private const val MATH = "Latin Modern Math"
    private const val TERMES = "TeX Gyre Termes"
    private const val PAGELLA = "TeX Gyre Pagella"
    private const val HEROS = "TeX Gyre Heros"
    private const val DEJAVU = "DejaVu Sans"

    // CJK: Fandol steckt in Tectonics Bundle und wird über den DATEINAMEN geladen –
    // Bundle-Schriften stehen fontconfig nicht zur Verfügung, ein Familienname würde
    // also ins Leere greifen. Geräteunabhängig, anders als die Noto-CJK des Systems
    // (auf manchen Custom-ROMs nicht vorhanden). Auf dem Gerät geprüft.
    private const val CJK_SONG = "FandolSong-Regular.otf"
    private const val CJK_HEI = "FandolHei-Regular.otf"
    private const val CJK_KAI = "FandolKai-Regular.otf"
    private const val CJK_FANG = "FandolFang-Regular.otf"

    /**
     * Geläufige Schriftnamen → mitgelieferte Familie derselben Klasse. Schlüssel
     * sind kleingeschrieben; Schriften, die wir selbst mitliefern, stehen hier
     * bewusst nicht (die werden ja gefunden).
     */
    private val ALIASES: Map<String, String> = mapOf(
        // --- Serifen: Times-/Palatino-/Schoolbook-Verwandtschaft ---
        "times" to TERMES,
        "times new roman" to TERMES,
        "timesnewroman" to TERMES,
        "liberation serif" to TERMES,
        "nimbus roman" to TERMES,
        "nimbus roman no9 l" to TERMES,
        "tinos" to TERMES,
        "thorndale amt" to TERMES,
        "freeserif" to TERMES,
        "dejavu serif" to TERMES,
        "georgia" to TERMES,
        "cambria" to TERMES,
        "constantia" to TERMES,
        "garamond" to TERMES,
        "eb garamond" to TERMES,
        "minion pro" to TERMES,
        "palatino" to PAGELLA,
        "palatino linotype" to PAGELLA,
        "book antiqua" to PAGELLA,
        "urw palladio l" to PAGELLA,
        "bookman" to PAGELLA,
        "bookman old style" to PAGELLA,
        "century schoolbook" to PAGELLA,
        "new century schoolbook" to PAGELLA,
        // --- Serifenlos: Helvetica-/Arial-Verwandtschaft ---
        "arial" to HEROS,
        "arial narrow" to HEROS,
        "helvetica" to HEROS,
        "helvetica neue" to HEROS,
        "liberation sans" to HEROS,
        "nimbus sans" to HEROS,
        "nimbus sans l" to HEROS,
        "arimo" to HEROS,
        "albany amt" to HEROS,
        "freesans" to HEROS,
        "avant garde" to HEROS,
        "itc avant garde gothic" to HEROS,
        "century gothic" to HEROS,
        "futura" to HEROS,
        // --- Serifenlos mit großer Zeichenabdeckung → DejaVu (liegt bei) ---
        "calibri" to DEJAVU,
        "verdana" to DEJAVU,
        "tahoma" to DEJAVU,
        "segoe ui" to DEJAVU,
        "trebuchet ms" to DEJAVU,
        "lucida sans" to DEJAVU,
        "lucida grande" to DEJAVU,
        "bitstream vera sans" to DEJAVU,
        // --- Nichtproportional ---
        "courier" to MONO,
        "courier new" to MONO,
        "couriernew" to MONO,
        "liberation mono" to MONO,
        "nimbus mono" to MONO,
        "nimbus mono ps" to MONO,
        "cousine" to MONO,
        "freemono" to MONO,
        "consolas" to MONO,
        "menlo" to MONO,
        "monaco" to MONO,
        "lucida console" to MONO,
        "sf mono" to MONO,
        "cascadia code" to MONO,
        "cascadia mono" to MONO,
        "fira code" to MONO,
        "fira mono" to MONO,
        "source code pro" to MONO,
        "jetbrains mono" to MONO,
        "dejavu sans mono" to MONO,
        "andale mono" to MONO,
        // --- Computer-Modern-Schreibweisen, die nicht als Familie existieren ---
        "computer modern" to SERIF,
        "computer modern roman" to SERIF,
        "cmu serif" to SERIF,
        "cm roman" to SERIF,
        "cmu sans serif" to SANS,
        "cmu typewriter text" to MONO,
        "latin modern" to SERIF,
        "latin modern romanic" to SERIF,
        // --- Chinesisch: Windows-/macOS-Schriften, die Vorlagen fest verdrahten ---
        "simsun" to CJK_SONG, "宋体" to CJK_SONG, "nsimsun" to CJK_SONG,
        "simsun.ttc" to CJK_SONG, "simsun.ttf" to CJK_SONG, "stsong" to CJK_SONG,
        "songti sc" to CJK_SONG, "songti" to CJK_SONG,
        "simhei" to CJK_HEI, "黑体" to CJK_HEI, "simhei.ttf" to CJK_HEI,
        "stheiti" to CJK_HEI, "heiti sc" to CJK_HEI, "heiti" to CJK_HEI,
        "microsoft yahei" to CJK_HEI, "微软雅黑" to CJK_HEI, "msyh.ttc" to CJK_HEI,
        "msyh.ttf" to CJK_HEI, "pingfang sc" to CJK_HEI, "source han sans sc" to CJK_HEI,
        "kaiti" to CJK_KAI, "楷体" to CJK_KAI, "simkai.ttf" to CJK_KAI, "simkai" to CJK_KAI,
        "stkaiti" to CJK_KAI, "kaiti sc" to CJK_KAI,
        "fangsong" to CJK_FANG, "仿宋" to CJK_FANG, "simfang.ttf" to CJK_FANG, "simfang" to CJK_FANG,
        "stfangsong" to CJK_FANG,
    )

    /** Rolle der font-setzenden Befehle, falls der Name nichts verrät. */
    private val ROLE_BY_COMMAND = mapOf(
        "setmainfont" to SERIF,
        "setromanfont" to SERIF,
        "setsansfont" to SANS,
        "setmonofont" to MONO,
        "setmathfont" to MATH,
        "setCJKmainfont" to CJK_SONG,
        "setCJKsansfont" to CJK_HEI,
        "setCJKmonofont" to CJK_SONG,
        "setCJKfamilyfont" to CJK_SONG,
        "newCJKfontfamily" to CJK_SONG,
    )

    /**
     * Font-setzende fontspec-Befehle. Gruppe 1 = Befehl, Gruppe 3 = angeforderte
     * Familie. Zwischen Befehl und Familie dürfen ein Makroname
     * (`\newfontfamily\symbfont`) und optionale `[…]`-Argumente stehen.
     *
     * ⚠️ Alle literalen Klammern maskiert – auch `\{`/`\}` INNERHALB der
     * Zeichenklasse: Androids ICU-Regex-Engine lehnt unmaskierte `}`/`]` ab,
     * während Desktop-Java sie toleriert (dieselbe Falle wie bei
     * [LatexCompiler]s biblatex-Regex; Unit-Tests wären grün, die App würde
     * auf dem Gerät fliegen).
     */
    private val FONT_COMMAND = Regex(
        """\\(setmainfont|setromanfont|setsansfont|setmonofont|setmathfont|newfontfamily|newfontface|setboldfont|setitalicfont|setCJKmainfont|setCJKsansfont|setCJKmonofont|newCJKfontfamily)""" +
            """((?:\s*\\[A-Za-z@]+)?(?:\s*\[[^\]]*\])*)\s*\{([^\{\}]*)\}""",
    )

    /**
     * `\setCJKfamilyfont{familie}[…]{Schrift}` — hier steht die Schrift im
     * **zweiten** Argument, der Familienname im ersten. Genau so verdrahtet die
     * Vorlage des chinesischen Mathematik-Wettbewerbs ihre Windows-Schriften:
     * `\setCJKfamilyfont{song}[AutoFakeBold]{SimSun}`. Gruppe 2 = Schrift.
     */
    private val CJK_FAMILY_FONT = Regex(
        """\\setCJKfamilyfont\s*\{[^\{\}]*\}((?:\s*\[[^\]]*\])*)\s*\{([^\{\}]*)\}""",
    )

    /**
     * Vergleicht Schriftangaben tolerant gegenüber Dateiendungen. Die Engine meldet
     * `simkai`, im Dokument steht `simkai.ttf` — ohne diese Normalisierung liefe die
     * Ersetzung ins Leere (auf dem Gerät beobachtet, chinesische Wettbewerbsvorlage).
     */
    private fun sameFont(a: String, b: String): Boolean =
        stripFontExtension(a).equals(stripFontExtension(b), ignoreCase = true)

    private fun stripFontExtension(s: String): String {
        val v = s.trim()
        listOf(".ttf", ".otf", ".ttc", ".otc").forEach {
            if (v.endsWith(it, ignoreCase = true)) return v.dropLast(it.length)
        }
        return v
    }

    /**
     * Tauscht die fehlende Familie [missing] in [source] gegen eine mitgelieferte.
     *
     * @return umgeschriebener Quelltext und die verwendete Ersatzfamilie, oder null,
     *   wenn [missing] in keinem font-setzenden Befehl steht (dann ist nichts zu
     *   retten – etwa wenn eine Klasse die Schrift intern anfordert).
     */
    fun replaceMissingFont(source: String, missing: String): Pair<String, String>? {
        val target = missing.trim()
        if (target.isEmpty()) return null
        var replacement: String? = null
        val out = StringBuilder(source.length)
        var last = 0
        FONT_COMMAND.findAll(source).forEach { m ->
            val family = m.groups[3] ?: return@forEach
            if (!sameFont(family.value, target)) return@forEach
            val repl = replacementFor(target, m.groupValues[1])
            replacement = repl
            out.append(source, last, family.range.first).append(repl)
            last = family.range.last + 1
        }
        out.append(source, last, source.length)
        val afterFirst = out.toString()

        // Zweiter Durchgang: \setCJKfamilyfont hat die Schrift im zweiten Argument.
        val out2 = StringBuilder(afterFirst.length)
        var last2 = 0
        CJK_FAMILY_FONT.findAll(afterFirst).forEach { m ->
            val font = m.groups[2] ?: return@forEach
            if (!sameFont(font.value, target)) return@forEach
            val repl = replacementFor(target, "setCJKfamilyfont")
            replacement = repl
            out2.append(afterFirst, last2, font.range.first).append(repl)
            last2 = font.range.last + 1
        }
        val used = replacement ?: return null
        out2.append(afterFirst, last2, afterFirst.length)
        return (if (last2 == 0) afterFirst else out2.toString()) to used
    }

    /** Eine vorgenommene Ersetzung: angefordert → eingesetzt. */
    data class Substitution(val requested: String, val replacement: String)

    /**
     * Ersetzt **alle bekannten** nicht verfügbaren Schriften auf einmal — vorab,
     * ohne auf einen Fehler der Engine zu warten.
     *
     * Grund: Die reaktive Ersetzung erfährt immer nur die EINE Schrift, an der die
     * Engine gerade scheitert. Eine Vorlage, die vier Windows-Schriften verdrahtet
     * (Times New Roman, SimSun, simkai, Arial — so die Vorlage des chinesischen
     * Mathematik-Wettbewerbs), bräuchte also vier komplette Durchläufe. Hier wird in
     * einem Durchgang alles getauscht, was in der kuratierten Liste steht.
     *
     * Bewusst **nur** die kuratierte Liste, keine Heuristik: Was wir nicht sicher als
     * „gibt es auf Android nicht" kennen, könnte vorhanden sein (Systemschrift oder
     * eine eigene Datei des Nutzers) und wird nicht angefasst. Für den Rest bleibt
     * die reaktive Ersetzung als Netz.
     */
    fun replaceAllKnown(source: String): Pair<String, List<Substitution>> {
        val subs = LinkedHashMap<String, String>()
        var current = source
        knownRequestedFonts(current).forEach { requested ->
            val (rewritten, replacement) = replaceMissingFont(current, requested) ?: return@forEach
            current = rewritten
            subs[requested] = replacement
        }
        return current to subs.map { Substitution(it.key, it.value) }
    }

    /** Wie [replaceAllKnown], aber für Klassen-/Paketdateien im Arbeitsverzeichnis. */
    fun replaceAllKnownInFiles(jobDir: File): List<Substitution> {
        val subs = LinkedHashMap<String, String>()
        jobDir.walkTopDown()
            .filter { f ->
                f.isFile && f.name != "document.tex" &&
                    listOf(".cls", ".sty", ".tex", ".clo", ".def").any {
                        f.name.endsWith(it, ignoreCase = true)
                    }
            }
            .forEach { f ->
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                val (rewritten, list) = replaceAllKnown(text)
                if (list.isEmpty()) return@forEach
                runCatching { f.writeText(rewritten) }
                    .onSuccess { list.forEach { s -> subs[s.requested] = s.replacement } }
            }
        return subs.map { Substitution(it.key, it.value) }
    }

    /** Angeforderte Schriften, die in der kuratierten Liste als nicht verfügbar stehen. */
    private fun knownRequestedFonts(source: String): List<String> {
        val found = LinkedHashSet<String>()
        FONT_COMMAND.findAll(source).forEach { m ->
            m.groups[3]?.value?.trim()?.let { if (isKnownUnavailable(it)) found += it }
        }
        CJK_FAMILY_FONT.findAll(source).forEach { m ->
            m.groups[2]?.value?.trim()?.let { if (isKnownUnavailable(it)) found += it }
        }
        return found.toList()
    }

    private fun isKnownUnavailable(name: String): Boolean =
        ALIASES.containsKey(stripFontExtension(name).lowercase()) ||
            ALIASES.containsKey(name.lowercase().trim())

    /**
     * Wie [replaceMissingFont], aber in den **Projektdateien im Arbeitsverzeichnis**
     * (`.cls`, `.sty`, `.tex`).
     *
     * Nötig, weil Dokumentklassen die Schrift oft fest verdrahten, statt sie dem
     * Autor zu überlassen — die Vorlage des chinesischen Mathematik-Wettbewerbs
     * (`cumcmthesis.cls`) etwa mit `\setmainfont{Times New Roman}` und
     * `\setsansfont{Arial}`. Im Hauptdokument steht davon nichts, die Ersetzung
     * dort läuft also ins Leere und der Compile bricht ab.
     *
     * Angefasst wird ausschließlich die Kopie im Arbeitsverzeichnis; das Projekt
     * des Nutzers bleibt unberührt. `document.tex` lassen wir aus — das ist der
     * Quelltext, den die Engine selbst schreibt.
     *
     * @return die verwendete Ersatzfamilie, oder null wenn nichts zu ersetzen war.
     */
    fun replaceMissingFontInFiles(jobDir: File, missing: String): String? {
        var used: String? = null
        jobDir.walkTopDown()
            .filter { f ->
                f.isFile && f.name != "document.tex" &&
                    listOf(".cls", ".sty", ".tex", ".clo", ".def").any {
                        f.name.endsWith(it, ignoreCase = true)
                    }
            }
            .forEach { f ->
                val text = runCatching { f.readText() }.getOrNull() ?: return@forEach
                val (rewritten, replacement) = replaceMissingFont(text, missing) ?: return@forEach
                runCatching { f.writeText(rewritten) }.onSuccess { used = replacement }
            }
        return used
    }

    /** Ersatz für [name]: kuratierte Zuordnung, sonst Heuristik über Name und Befehl. */
    private fun replacementFor(name: String, command: String): String {
        val key = stripFontExtension(name).lowercase()
        ALIASES[key]?.let { return it }
        ALIASES[name.lowercase().trim()]?.let { return it }
        // Ein CJK-Befehl darf niemals bei einer lateinischen Schrift landen – sonst
        // entstünde ein PDF voller leerer Kästchen statt einer Fehlermeldung.
        if (command.contains("CJK")) {
            return if (key.contains("hei") || key.contains("yahei") || key.contains("gothic")) {
                CJK_HEI
            } else {
                ROLE_BY_COMMAND[command] ?: CJK_SONG
            }
        }
        val byName = when {
            key.contains("mono") || key.contains("courier") || key.contains("consol") ||
                key.contains("typewriter") || key.contains("code") -> MONO
            key.contains("math") -> MATH
            key.contains("sans") || key.contains("grotesk") || key.contains("gothic") -> SANS
            key.contains("serif") || key.contains("roman") -> SERIF
            else -> null
        }
        return byName ?: ROLE_BY_COMMAND[command] ?: SERIF
    }
}

package de.bgg_home.texdroid.compile

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
    )

    /** Rolle der font-setzenden Befehle, falls der Name nichts verrät. */
    private val ROLE_BY_COMMAND = mapOf(
        "setmainfont" to SERIF,
        "setromanfont" to SERIF,
        "setsansfont" to SANS,
        "setmonofont" to MONO,
        "setmathfont" to MATH,
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
        """\\(setmainfont|setromanfont|setsansfont|setmonofont|setmathfont|newfontfamily|newfontface|setboldfont|setitalicfont)""" +
            """((?:\s*\\[A-Za-z@]+)?(?:\s*\[[^\]]*\])*)\s*\{([^\{\}]*)\}""",
    )

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
            if (!family.value.trim().equals(target, ignoreCase = true)) return@forEach
            val repl = replacementFor(target, m.groupValues[1])
            replacement = repl
            out.append(source, last, family.range.first).append(repl)
            last = family.range.last + 1
        }
        val used = replacement ?: return null
        out.append(source, last, source.length)
        return out.toString() to used
    }

    /** Ersatz für [name]: kuratierte Zuordnung, sonst Heuristik über Name und Befehl. */
    private fun replacementFor(name: String, command: String): String {
        val key = name.lowercase().trim()
        ALIASES[key]?.let { return it }
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

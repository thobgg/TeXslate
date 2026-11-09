// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.compile

/**
 * Macht Dokumente kompilierbar, die für **pdfLaTeX** geschrieben wurden.
 *
 * Tectonic ist XeTeX. Zwei Angewohnheiten aus der pdfLaTeX-Welt brechen den
 * Compile sofort, und beide stehen in unzähligen echten Dokumenten:
 *
 *  1. `\usepackage[latin1]{inputenc}` → `! Package inputenc Error: inputenc is not
 *     designed for xetex or luatex.` XeTeX liest ohnehin UTF-8, das Paket ist
 *     überflüssig.
 *  2. Fest verdrahtete Treiberoptionen: `\usepackage[pdftex]{hyperref}` →
 *     `! Package hyperref Error: Wrong driver option 'pdftex'`. Gemeint ist
 *     „der Treiber der aktuellen Engine", unter XeTeX also `xetex`.
 *
 * Beides wird in der **Compile-Kopie** bereinigt, nie in der Datei des Nutzers —
 * niemand soll sein Dokument an die App anpassen müssen. Ersetzt wird jeweils nur
 * innerhalb der Zeile, also bleiben Zeilennummern für das Fehlerpanel gültig.
 *
 * `fontenc` bleibt vorab stehen: `\usepackage[T1]{fontenc}` funktioniert unter
 * XeTeX für alle Zeichen bis U+00FF (ä, ö, ü, é …), weil T1 dort zufällig wie
 * Unicode belegt ist. Zeichen darüber (č, ć, š, ž, đ, ł, ő …) gibt es in den
 * T1-Schriften nicht, XeTeX lässt sie still weg (Issue #13, kroatischer Brief).
 * Erst wenn das Log solche Lücken zeigt, nimmt [dropT1Fontenc] das Paket für
 * einen zweiten Lauf heraus – siehe [LatexCompiler].
 */
object EngineCompat {

    /** Was angepasst wurde — die Meldungstexte baut [LatexCompiler] (i18n). */
    enum class Adaptation { INPUTENC_ENTFERNT, TREIBER_UMGESTELLT }

    /**
     * `\usepackage[…]{inputenc}` bzw. `\RequirePackage[…]{inputenc}`.
     *
     * ⚠️ Klammern durchgängig maskiert, auch in Zeichenklassen: Androids
     * ICU-Regex-Engine lehnt unmaskierte `}`/`]` ab, Desktop-Java toleriert sie
     * (dieselbe Falle wie bei [FontFallback] und der biblatex-Regex).
     */
    private val INPUTENC = Regex(
        """\\(?:usepackage|RequirePackage)\s*(?:\[[^\]]*\])?\s*\{\s*inputenc\s*\}""",
    )

    /** Optionsliste von `\usepackage[…]{…}` / `\documentclass[…]{…}`; Gruppe 1 = Optionen. */
    private val PACKAGE_OPTIONS = Regex(
        """\\(?:usepackage|RequirePackage|documentclass)\s*\[([^\]]*)\]""",
    )

    /** Treiber, die unter XeTeX falsch sind, samt Ersatz. */
    private val WRONG_DRIVERS = setOf("pdftex", "dvips", "dvipdfm", "dvipdfmx", "luatex")
    private const val XETEX = "xetex"

    /**
     * Passt [source] für XeTeX an.
     *
     * @return angepasster Quelltext und die Liste der Eingriffe — oder null, wenn
     *   nichts zu tun war (der Normalfall bei Dokumenten, die für XeTeX geschrieben
     *   sind; dann wird auch nichts kopiert).
     */
    fun adapt(source: String): Pair<String, List<Adaptation>>? {
        val adaptations = mutableListOf<Adaptation>()

        var out = INPUTENC.replace(source) {
            adaptations += Adaptation.INPUTENC_ENTFERNT
            ""
        }

        out = replaceDriverOptions(out) { adaptations += Adaptation.TREIBER_UMGESTELLT }

        if (adaptations.isEmpty()) return null
        return out to adaptations.distinct()
    }

    /** `\usepackage[…]{fontenc}`; Gruppe 1 = Optionen (ohne Klammern auch ohne Gruppe). */
    private val FONTENC = Regex(
        """\\(?:usepackage|RequirePackage)\s*(?:\[([^\]]*)\])?\s*\{\s*fontenc\s*\}""",
    )

    /** 8-Bit-Kodierungen, die unter XeTeX nur Latin-1 können; alles andere (T2A …) bleibt. */
    private val LATIN_ENCODINGS = setOf("T1", "OT1", "LY1", "TS1")

    /** „Missing character: There is no š ("161) in font ec-lmr10!" — Gruppe 1 = Zeichen. */
    private val MISSING_CHAR = Regex("""Missing character: There is no (\S+) \(""")

    /**
     * Zeichen jenseits von U+00FF, die XeTeX laut [log] weggelassen hat, in
     * Log-Reihenfolge ohne Dubletten. Leer, wenn nichts fehlt — oder nur Zeichen,
     * die mit `fontenc` nichts zu tun haben (dann hilft das Herausnehmen nicht).
     */
    fun missingBeyondLatin1(log: String): List<String> =
        MISSING_CHAR.findAll(log)
            .map { it.groupValues[1] }
            .filter { s -> s.codePointAt(0) > 0xFF }
            .distinct()
            .toList()

    /**
     * Nimmt `\usepackage[T1]{fontenc}` aus [source]. Danach gilt die XeTeX-Vorgabe
     * (Unicode, Latin Modern), in der alle Zeichen vorhanden sind. Nur innerhalb der
     * Zeile, Zeilennummern bleiben gültig.
     *
     * @return angepasster Quelltext oder null, wenn kein `fontenc` mit reinen
     *   Latin-Kodierungen dasteht (dann ist es nicht die Ursache).
     */
    fun dropT1Fontenc(source: String): String? {
        var changed = false
        val out = FONTENC.replace(source) { m ->
            val options = m.groups[1]?.value.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }
            if (options.isNotEmpty() && options.all { it in LATIN_ENCODINGS } && "T1" in options) {
                changed = true
                ""
            } else {
                m.value
            }
        }
        return if (changed) out else null
    }

    /**
     * Ersetzt falsche Treiber-Tokens INNERHALB der Optionsklammern. Nur dort, damit
     * ein Paketname wie `pdftexcmds` oder Text im Dokument unberührt bleibt.
     */
    private fun replaceDriverOptions(source: String, onChange: () -> Unit): String {
        val out = StringBuilder(source.length)
        var last = 0
        PACKAGE_OPTIONS.findAll(source).forEach { m ->
            val group = m.groups[1] ?: return@forEach
            val rewritten = group.value.split(',').joinToString(",") { raw ->
                val token = raw.trim()
                if (token.lowercase() in WRONG_DRIVERS) raw.replace(token, XETEX) else raw
            }
            if (rewritten == group.value) return@forEach
            onChange()
            out.append(source, last, group.range.first).append(rewritten)
            last = group.range.last + 1
        }
        if (last == 0) return source
        out.append(source, last, source.length)
        return out.toString()
    }
}

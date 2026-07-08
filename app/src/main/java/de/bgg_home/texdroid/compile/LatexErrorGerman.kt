package de.bgg_home.texdroid.compile

import java.util.Locale

/**
 * Bereitet die (englischen) TeX-/LaTeX-Rohmeldungen als kurze, verständliche
 * Sätze auf – in der UI-Sprache (Deutsch bei deutscher Geräte-Locale, sonst
 * Englisch). Die Engine (Tectonic/XeTeX) meldet stets auf Englisch; hier wird
 * daraus eine freundlichere Fassung. Unbekannte Meldungen bleiben unverändert,
 * damit keine Information verloren geht.
 *
 * Reine Logik ohne Context → Sprache über [Locale.getDefault] (spiegelt die
 * App-/Systemsprache), wie in [de.bgg_home.texdroid.ai.AiPrompt].
 */
object LatexErrorGerman {

    private val german: Boolean get() = Locale.getDefault().language == "de"

    // (Muster über die Roh-Meldung) -> (deutsche Fassung, englische Fassung),
    // jeweils mit $1/$2 aus den Regex-Gruppen.
    private val rules: List<Triple<Regex, String, String>> = listOf(
        Triple(
            Regex("""^Undefined control sequence\.?$"""),
            "Unbekannter Befehl (undefinierte Kontrollsequenz) – Tippfehler oder fehlendes Paket?",
            "Unknown command (undefined control sequence) – typo or missing package?",
        ),
        Triple(
            Regex("""^Too many \}'s\.?$"""),
            "Zu viele schließende Klammern }.",
            "Too many closing braces }.",
        ),
        Triple(
            Regex("""^Missing \$ inserted\.?$"""),
            "Fehlendes \$ – Mathematik-Modus nicht korrekt geöffnet oder geschlossen.",
            "Missing \$ – math mode not opened or closed correctly.",
        ),
        Triple(
            Regex("""^Missing \{ inserted\.?$"""),
            "Fehlende öffnende Klammer {.",
            "Missing opening brace {.",
        ),
        Triple(
            Regex("""^Missing \} inserted\.?$"""),
            "Fehlende schließende Klammer }.",
            "Missing closing brace }.",
        ),
        Triple(
            Regex("""^Double superscript\.?$"""),
            "Doppelte Hochstellung ^ – schreibe ^{...}.",
            "Double superscript ^ – write ^{...}.",
        ),
        Triple(
            Regex("""^Double subscript\.?$"""),
            "Doppelte Tiefstellung _ – schreibe _{...}.",
            "Double subscript _ – write _{...}.",
        ),
        Triple(
            Regex("""^Misplaced alignment tab character &\.?$"""),
            "Falsch platziertes Tabellen-Trennzeichen & (außerhalb einer Tabelle?).",
            "Misplaced table separator & (outside a table?).",
        ),
        Triple(
            Regex("""^Extra alignment tab has been changed to \\cr\.?$"""),
            "Zu viele Spalten in der Tabellenzeile (ein & zu viel).",
            "Too many columns in the table row (one & too many).",
        ),
        Triple(
            Regex("""^Runaway argument\?.*"""),
            "Außer Kontrolle geratenes Argument – wahrscheinlich fehlt eine schließende Klammer }.",
            "Runaway argument – a closing brace } is probably missing.",
        ),
        Triple(
            Regex("""^Emergency stop\.?$"""),
            "Notausstieg – TeX konnte nicht weiter verarbeiten (siehe vorherige Fehler).",
            "Emergency stop – TeX could not continue (see the previous errors).",
        ),
        Triple(
            Regex("""^Paragraph ended before.*"""),
            "Absatz endete, bevor ein Befehl abgeschlossen war – fehlt eine schließende Klammer }?",
            "Paragraph ended before a command was complete – is a closing brace } missing?",
        ),
        Triple(
            Regex("""^File ended while scanning.*"""),
            "Datei endete mitten in einem Befehl – fehlt eine schließende Klammer } oder ein \\end{...}?",
            "File ended in the middle of a command – is a closing brace } or an \\end{...} missing?",
        ),
        Triple(
            Regex("""^Missing \\begin\{document\}\.?$"""),
            "Fehlendes \\begin{document}.",
            "Missing \\begin{document}.",
        ),
        Triple(
            Regex("""^Missing number, treated as zero\.?$"""),
            "Fehlende Zahl – TeX hat sie als 0 angenommen (Einheit oder Wert vergessen?).",
            "Missing number – TeX assumed 0 (forgot a unit or value?).",
        ),
        Triple(
            Regex("""^Illegal unit of measure.*"""),
            "Ungültige Maßeinheit – erwartet wird z. B. pt, cm oder mm.",
            "Invalid unit of measure – expected e.g. pt, cm or mm.",
        ),
        // LaTeX-spezifische Meldungen (file:line-Form)
        Triple(
            Regex("""^LaTeX Error: Environment (.+?) undefined\.?$"""),
            "LaTeX-Fehler: Umgebung „$1“ ist nicht definiert (fehlt ein Paket?).",
            "LaTeX error: environment “$1” is undefined (missing a package?).",
        ),
        Triple(
            Regex("""^LaTeX Error: File [`'](.+?)'? not found\.?$"""),
            "LaTeX-Fehler: Datei „$1“ nicht gefunden.",
            "LaTeX error: file “$1” not found.",
        ),
        Triple(
            Regex("""^LaTeX Error: \\begin\{(.+?)\} on input line \d+ ended by \\end\{(.+?)\}\.?$"""),
            "LaTeX-Fehler: \\begin{$1} wird fälschlich mit \\end{$2} geschlossen.",
            "LaTeX error: \\begin{$1} is wrongly closed with \\end{$2}.",
        ),
        Triple(
            Regex("""^LaTeX Error: Something's wrong--perhaps a missing \\item\.?$"""),
            "LaTeX-Fehler: Vermutlich fehlt ein \\item.",
            "LaTeX error: an \\item is probably missing.",
        ),
        Triple(
            Regex("""^LaTeX Error: There's no line here to end\.?$"""),
            "LaTeX-Fehler: Hier gibt es keine Zeile zum Beenden (überflüssiges \\\\?).",
            "LaTeX error: there is no line here to end (superfluous \\\\?).",
        ),
        Triple(
            Regex("""^Undefined color.*"""),
            "Unbekannte Farbe – zuerst mit \\definecolor definieren.",
            "Unknown color – define it first with \\definecolor.",
        ),
    )

    // Allgemeine Präfixe: Detail (englisch) beibehalten, aber übersetzt etikettieren.
    private val genericLatex = Regex("""^LaTeX Error:\s*(.*)$""")
    private val genericPackage = Regex("""^Package (.+?) Error:\s*(.*)$""")
    private val placeholder = Regex("""\$(\d)""")

    /** Fallback-Bezeichnung, wenn die Roh-Meldung leer ist. */
    fun fallback(): String = if (german) "TeX-Fehler" else "TeX error"

    fun translate(message: String): String {
        val m = message.trim()
        for ((re, germanMsg, englishMsg) in rules) {
            val match = re.find(m) ?: continue
            val template = if (german) germanMsg else englishMsg
            return placeholder.replace(template) { match.groupValues[it.groupValues[1].toInt()] }
        }
        genericLatex.find(m)?.let {
            val label = if (german) "LaTeX-Fehler" else "LaTeX error"
            return "$label: ${it.groupValues[1]}"
        }
        genericPackage.find(m)?.let {
            val label = if (german) "Paket-Fehler" else "Package error"
            return "$label (${it.groupValues[1]}): ${it.groupValues[2]}"
        }
        return message
    }
}

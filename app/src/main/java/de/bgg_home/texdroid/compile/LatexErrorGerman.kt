package de.bgg_home.texdroid.compile

/**
 * Übersetzt die (englischen) TeX-/LaTeX-Fehlermeldungen in kurze deutsche Sätze.
 * Die Engine (Tectonic/XeTeX) gibt Meldungen nur auf Englisch aus; für die
 * Fehlerliste bereiten wir die häufigsten davon auf Deutsch auf. Unbekannte
 * Meldungen bleiben unverändert, damit keine Information verloren geht.
 */
object LatexErrorGerman {

    // Regel: (Muster über die Roh-Meldung) -> deutsche Fassung (mit $1/$2 aus Gruppen).
    private val rules: List<Pair<Regex, String>> = listOf(
        Regex("""^Undefined control sequence\.?$""") to
            "Unbekannter Befehl (undefinierte Kontrollsequenz) – Tippfehler oder fehlendes Paket?",
        Regex("""^Too many \}'s\.?$""") to "Zu viele schließende Klammern }.",
        Regex("""^Missing \$ inserted\.?$""") to
            "Fehlendes \$ – Mathematik-Modus nicht korrekt geöffnet oder geschlossen.",
        Regex("""^Missing \{ inserted\.?$""") to "Fehlende öffnende Klammer {.",
        Regex("""^Missing \} inserted\.?$""") to "Fehlende schließende Klammer }.",
        Regex("""^Double superscript\.?$""") to "Doppelte Hochstellung ^ – schreibe ^{...}.",
        Regex("""^Double subscript\.?$""") to "Doppelte Tiefstellung _ – schreibe _{...}.",
        Regex("""^Misplaced alignment tab character &\.?$""") to
            "Falsch platziertes Tabellen-Trennzeichen & (außerhalb einer Tabelle?).",
        Regex("""^Extra alignment tab has been changed to \\cr\.?$""") to
            "Zu viele Spalten in der Tabellenzeile (ein & zu viel).",
        Regex("""^Runaway argument\?.*""") to
            "Außer Kontrolle geratenes Argument – wahrscheinlich fehlt eine schließende Klammer }.",
        Regex("""^Emergency stop\.?$""") to
            "Notausstieg – TeX konnte nicht weiter verarbeiten (siehe vorherige Fehler).",
        Regex("""^Paragraph ended before.*""") to
            "Absatz endete, bevor ein Befehl abgeschlossen war – fehlt eine schließende Klammer }?",
        Regex("""^File ended while scanning.*""") to
            "Datei endete mitten in einem Befehl – fehlt eine schließende Klammer } oder ein \\end{...}?",
        Regex("""^Missing \\begin\{document\}\.?$""") to "Fehlendes \\begin{document}.",
        Regex("""^Missing number, treated as zero\.?$""") to
            "Fehlende Zahl – TeX hat sie als 0 angenommen (Einheit oder Wert vergessen?).",
        Regex("""^Illegal unit of measure.*""") to
            "Ungültige Maßeinheit – erwartet wird z. B. pt, cm oder mm.",
        // LaTeX-spezifische Meldungen (file:line-Form)
        Regex("""^LaTeX Error: Environment (.+?) undefined\.?$""") to
            "LaTeX-Fehler: Umgebung „$1“ ist nicht definiert (fehlt ein Paket?).",
        Regex("""^LaTeX Error: File [`'](.+?)'? not found\.?$""") to
            "LaTeX-Fehler: Datei „$1“ nicht gefunden.",
        Regex("""^LaTeX Error: \\begin\{(.+?)\} on input line \d+ ended by \\end\{(.+?)\}\.?$""") to
            "LaTeX-Fehler: \\begin{$1} wird fälschlich mit \\end{$2} geschlossen.",
        Regex("""^LaTeX Error: Something's wrong--perhaps a missing \\item\.?$""") to
            "LaTeX-Fehler: Vermutlich fehlt ein \\item.",
        Regex("""^LaTeX Error: There's no line here to end\.?$""") to
            "LaTeX-Fehler: Hier gibt es keine Zeile zum Beenden (überflüssiges \\\\?).",
        Regex("""^Undefined color.*""") to "Unbekannte Farbe – zuerst mit \\definecolor definieren.",
    )

    // Allgemeine Präfixe: Detail (englisch) beibehalten, aber deutsch etikettieren.
    private val genericLatex = Regex("""^LaTeX Error:\s*(.*)$""")
    private val genericPackage = Regex("""^Package (.+?) Error:\s*(.*)$""")
    private val placeholder = Regex("""\$(\d)""")

    fun translate(message: String): String {
        val m = message.trim()
        for ((re, german) in rules) {
            val match = re.find(m) ?: continue
            return placeholder.replace(german) { match.groupValues[it.groupValues[1].toInt()] }
        }
        genericLatex.find(m)?.let { return "LaTeX-Fehler: ${it.groupValues[1]}" }
        genericPackage.find(m)?.let { return "Paket-Fehler (${it.groupValues[1]}): ${it.groupValues[2]}" }
        return message
    }
}

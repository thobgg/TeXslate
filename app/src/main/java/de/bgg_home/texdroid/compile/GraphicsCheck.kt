package de.bgg_home.texdroid.compile

/**
 * Erkennt Abbildungen, die XeTeX nicht einbinden kann — praktisch immer **EPS**.
 *
 * Warum das eine eigene Prüfung wert ist: Der Versuch schlägt nicht nur fehl
 * („image inclusion failed for …"), er hinterlässt in dvipdfmx globalen
 * Font-Cache-Zustand. Der **nächste** Compile im selben Prozess bricht dann an
 * einer C-Assertion ab (`pdf_init_fonts: assertion "font_cache.fonts" failed`) und
 * reißt die ganze App mit — kein Rust-`catch_unwind` kann das abfangen, weil
 * `abort()` aus C kommt. Auf dem Tab S8 Ultra mit dem REVTeX/APS-Beispiel der
 * American Physical Society reproduziert (30.07.2026).
 *
 * Deshalb: vorher erkennen, klar sagen, gar nicht erst kompilieren.
 *
 * EPS wirklich einbinden könnte nur ein externer Konverter (Ghostscript über
 * Shell-Escape) — auf Android nicht vorhanden.
 */
object GraphicsCheck {

    /** `\includegraphics[…]{datei}` — Gruppe 2 ist der Dateiname. */
    private val INCLUDE_GRAPHICS = Regex(
        """\\includegraphics\s*(\*?)\s*(?:\[[^\]]*\])*\s*\{([^\{\}]*)\}""",
    )

    /** Endungen, die XeTeX direkt einbetten kann. */
    private val SUPPORTED = listOf(".pdf", ".png", ".jpg", ".jpeg", ".jp2", ".bmp")

    /**
     * Namen der eingebundenen EPS-Abbildungen, die im Arbeitsverzeichnis liegen.
     *
     * @param source der zu kompilierende Quelltext.
     * @param filesInJob Dateinamen im Arbeitsverzeichnis (klein geschrieben egal).
     *
     * Berücksichtigt beide Schreibweisen: `\includegraphics{bild.eps}` und – wie im
     * REVTeX-Beispiel – `\includegraphics{bild}` ohne Endung, wo nur `bild.eps`
     * existiert. Liegt daneben eine unterstützte Fassung (`bild.pdf`), ist alles gut,
     * denn die nimmt LaTeX von sich aus.
     */
    fun epsFigures(source: String, filesInJob: Collection<String>): List<String> {
        val lower = filesInJob.map { it.lowercase() }.toSet()
        val found = LinkedHashSet<String>()
        INCLUDE_GRAPHICS.findAll(source).forEach { m ->
            val name = m.groupValues[2].trim()
            if (name.isEmpty()) return@forEach
            val n = name.lowercase()
            when {
                n.endsWith(".eps") || n.endsWith(".ps") -> found += name
                // Ohne Endung: nur meckern, wenn ausschließlich EPS vorliegt.
                !n.contains('.') && "$n.eps" in lower && SUPPORTED.none { "$n$it" in lower } ->
                    found += "$name.eps"
            }
        }
        return found.toList()
    }
}

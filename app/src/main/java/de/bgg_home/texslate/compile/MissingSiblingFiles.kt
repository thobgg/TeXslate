// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate.compile

/**
 * Findet im TeX-Log Dateien, die das Dokument einbinden wollte, aber nicht fand —
 * Bilder (`\includegraphics`) wie Textteile (`\input`, Klassen, Pakete aus dem
 * Projekt).
 *
 * Warum: Wird eine `.tex`-Datei über „Öffnen" als **Einzeldatei** geöffnet, gibt
 * Android der App nur diese eine Datei frei. Die Bilder daneben sieht sie nicht,
 * und XeTeX meldet bloß „Unable to load picture or PDF file". Der Hinweis beim
 * Öffnen ist eine Snackbar, die nach Sekunden verschwindet. Issue #9: Nach einer
 * Neuinstallation öffnete der Melder seine Datei auf diesem Weg und hielt das
 * fehlende Bild für einen Fehler der neuen Version. Zweiter Fall aus demselben
 * Issue: Nach der Neuinstallation spielt Androids Auto-Backup den Entwurf samt
 * Dateinamen zurück, das Ordnerrecht aber nicht. Der Editor zeigt dann das
 * gewohnte Dokument, ein Projekt ist trotzdem nicht offen.
 */
object MissingSiblingFiles {

    private val PATTERNS = listOf(
        // XeTeX: ! Unable to load picture or PDF file 'bild.png'.
        Regex("""Unable to load picture or PDF file '([^']+)'"""),
        // LaTeX: ! LaTeX Error: File `teil.tex' not found.
        Regex("""LaTeX Error: File [`']([^`']+)' not found"""),
    )

    /** Namen der fehlenden Dateien in Log-Reihenfolge, ohne Dubletten. */
    fun inLog(log: String): List<String> {
        val found = LinkedHashSet<String>()
        PATTERNS.forEach { p -> p.findAll(log).forEach { found += it.groupValues[1].trim() } }
        return found.filter { it.isNotEmpty() }
    }
}

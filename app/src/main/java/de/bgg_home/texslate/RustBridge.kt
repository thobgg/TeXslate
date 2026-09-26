// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 Thomas Bugge

package de.bgg_home.texslate

/**
 * Brücke zur nativen Rust-Bibliothek (libtexslate_native.so).
 *
 * `object` = Singleton (es gibt genau eine Instanz). Der init-Block lädt die
 * .so beim ersten Zugriff genau einmal.
 *
 * `external fun` = "die Implementierung liegt nicht hier in Kotlin, sondern in
 * der geladenen nativen Bibliothek" — das Gegenstück zur Rust-Funktion
 * Java_de_bgg_1home_texslate_RustBridge_add.
 */
object RustBridge {
    init {
        // Name OHNE "lib"-Präfix und ".so"-Endung:
        // sucht libtexslate_native.so in den jniLibs des APKs.
        System.loadLibrary("texslate_native")
    }

    external fun add(a: Int, b: Int): Int

    /** Liefert einen Nachweis, dass die native Tectonic/XeTeX-Engine eingebettet ist. */
    external fun tectonicVersion(): String

    /**
     * Kompiliert ein fest eingebautes Mini-.tex zu PDF und gibt "PDF erzeugt ✓ N Bytes"
     * bzw. eine Fehlermeldung zurück. [cacheDir] = beschreibbares App-Cache-Verzeichnis
     * für Tectonics Paket-Bundle. Blockiert (Netzwerk!) → nur vom Hintergrund-Thread rufen.
     */
    external fun tectonicCompile(cacheDir: String): String

    /**
     * M1/M2: kompiliert [texSource] (der aktuelle Editor-Inhalt) und legt PDF, Log
     * und SyncTeX als Dateien in [jobDir] ab. [jobDir] muss beschreibbar sein
     * (z.B. ein Unterordner von `context.filesDir`) und dient zugleich als
     * Tectonic-Cache-Verzeichnis.
     *
     * [buildEpochSeconds] ist das Compile-Datum als Unix-Epoch-Sekunden und
     * belegt `\today`/`\time`. Erwartet die LOKALE Wanduhrzeit als „UTC-kodierte"
     * Epoch (currentTimeMillis + Zeitzonen-Offset, in Sekunden) — die native
     * Seite kompiliert dann mit TZ=UTC, sodass das Datum lokal korrekt erscheint.
     * `<= 0` überlässt der nativen Seite den Fallback auf die Geräte-Uhr.
     *
     * [fontconfigFile] ist der absolute Pfad einer `fonts.conf`, die die native
     * Seite via `FONTCONFIG_FILE` an fontconfig durchreicht — so löst XeTeX
     * `\setmainfont{<Name>}` gegen die mitgelieferten/eigenen Fonts +
     * `/system/fonts` auf. Leerer String → fontconfig-Default (nur Systemfonts).
     *
     * [continueOnErrors] = Overleaf-artiges „Trotz Fehlern kompilieren": die
     * Engine hält bei TeX-Fehlern nicht an, sondern läuft durch und schreibt
     * .aux/PDF trotzdem. Da Tectonic automatisch bis zur Stabilität nachläuft,
     * lösen sich .aux-Zweipass-Konstrukte damit in einem einzigen Aufruf auf.
     *
     * Rückgabe: ein JSON-String
     *   {"ok":Boolean,"pdfPath":String,"synctexPath":String,"log":String,"error":String}
     * → auf Kotlin-Seite von [de.bgg_home.texslate.compile.CompileResult.fromJson] geparst.
     *
     * Blockiert (Compile + evtl. Netzwerk) → nur vom Hintergrund-Thread rufen.
     */
    external fun tectonicCompileToFile(
        texSource: String,
        jobDir: String,
        buildEpochSeconds: Long,
        fontconfigFile: String,
        continueOnErrors: Boolean,
    ): String
}

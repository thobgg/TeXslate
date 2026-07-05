package de.bgg_home.texdroid

/**
 * Brücke zur nativen Rust-Bibliothek (libtexdroid_native.so).
 *
 * `object` = Singleton (es gibt genau eine Instanz). Der init-Block lädt die
 * .so beim ersten Zugriff genau einmal.
 *
 * `external fun` = "die Implementierung liegt nicht hier in Kotlin, sondern in
 * der geladenen nativen Bibliothek" — das Gegenstück zur Rust-Funktion
 * Java_de_bgg_1home_texdroid_RustBridge_add.
 */
object RustBridge {
    init {
        // Name OHNE "lib"-Präfix und ".so"-Endung:
        // sucht libtexdroid_native.so in den jniLibs des APKs.
        System.loadLibrary("texdroid_native")
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
}

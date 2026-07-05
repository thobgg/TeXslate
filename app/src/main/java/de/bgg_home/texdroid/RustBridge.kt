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
}

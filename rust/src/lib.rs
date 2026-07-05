// TexDroid – native Rust-Seite der JNI-Brücke.
//
// Für QW 0.2 nur ein Minimalbeweis: add(a, b) in Rust, aufgerufen aus Kotlin.
// Später hängt an genau diesem Mechanismus der Tectonic-Compiler.

use jni::objects::{JClass, JString};
use jni::sys::{jint, jstring};
use jni::JNIEnv;

fn jstr<'a>(env: &mut JNIEnv<'a>, s: &str) -> jstring {
    env.new_string(s)
        .map(|o| o.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

// ─────────────────────────────────────────────────────────────
// Der Funktionsname ist NICHT frei wählbar – JNI verlangt ein festes Schema:
//
//     Java_<paket_mit_unterstrichen>_<Klasse>_<Methode>
//
// Kotlin-Seite:  Paket  de.bgg_home.texdroid   Klasse  RustBridge   Methode  add
//
// ⚠️ Stolperfalle: Ein echter Unterstrich im Paketnamen (bgg_home) wird in
//    JNI-Symbolen zu "_1" maskiert. Aus  bgg_home  wird also  bgg_1home.
//    Deshalb heißt die Funktion exakt:
//        Java_de_bgg_1home_texdroid_RustBridge_add
//    Stimmt das nicht 1:1, findet Kotlin die Funktion zur Laufzeit nicht
//    (UnsatisfiedLinkError).
// ─────────────────────────────────────────────────────────────
#[no_mangle]
pub extern "system" fn Java_de_bgg_1home_texdroid_RustBridge_add(
    _env: JNIEnv,   // JNI-Umgebung (hier ungenutzt, aber Pflicht-Parameter)
    _class: JClass, // die aufrufende Kotlin-Klasse (ebenfalls Pflicht)
    a: jint,        // jint == Kotlin Int (32-bit)
    b: jint,
) -> jint {
    a + b
}

// QW 0.3: beweist, dass die native Tectonic/XeTeX-Engine wirklich eingebettet
// ist. FORMAT_SERIAL ist eine Konstante direkt aus der XeTeX-Engine — sie hier
// auf dem Gerät auszulesen ist der Nachweis, dass Tectonic für Android gebaut
// und gelinkt wurde. Rückgabe: ein Java-String (jstring).
#[no_mangle]
pub extern "system" fn Java_de_bgg_1home_texdroid_RustBridge_tectonicVersion<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
) -> jstring {
    let msg = format!(
        "Tectonic-Engine eingebettet ✓\nXeTeX format serial: {}",
        tectonic::FORMAT_SERIAL
    );
    match env.new_string(msg) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

// QW 0.4-Kern: echter Compile-Test. Kompiliert ein fest eingebautes Mini-.tex
// wirklich zu PDF und gibt die Byte-Größe zurück (oder die Fehlermeldung).
// cacheDir = beschreibbares App-Cache-Verzeichnis (von Kotlin übergeben) — dort
// legt Tectonic sein heruntergeladenes Paket-Bundle ab.
//
// ⚠️ Muss von einem Hintergrund-Thread aufgerufen werden: blockiert mehrere
//    Sekunden und lädt beim ersten Mal übers Netz.
#[no_mangle]
pub extern "system" fn Java_de_bgg_1home_texdroid_RustBridge_tectonicCompile<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    cache_dir: JString<'a>,
) -> jstring {
    let dir: String = match env.get_string(&cache_dir) {
        Ok(s) => s.into(),
        Err(_) => return jstr(&mut env, "FEHLER: cache_dir ungültig"),
    };

    // Tectonic nutzt app_dirs2 (XDG) für seinen Cache — auf das beschreibbare
    // App-Verzeichnis umbiegen, sonst schlägt das Anlegen des Bundle-Caches fehl.
    std::env::set_var("XDG_CACHE_HOME", &dir);
    std::env::set_var("HOME", &dir);

    let tex = r#"\documentclass{article}
\begin{document}
Hallo aus TexDroid! Native XeTeX-Engine auf Android. Formel: $E = mc^2$.
\end{document}
"#;

    let msg = match tectonic::latex_to_pdf(tex) {
        Ok(pdf) => format!("PDF erzeugt ✓  {} Bytes", pdf.len()),
        Err(e) => format!("Compile-Fehler:\n{}", e),
    };
    jstr(&mut env, &msg)
}

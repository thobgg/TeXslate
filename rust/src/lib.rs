// TexDroid – native Rust-Seite der JNI-Brücke.
//
// Für QW 0.2 nur ein Minimalbeweis: add(a, b) in Rust, aufgerufen aus Kotlin.
// Später hängt an genau diesem Mechanismus der Tectonic-Compiler.

use jni::objects::{JClass, JString};
use jni::sys::{jint, jlong, jstring};
use jni::JNIEnv;
use std::path::Path;
use std::time::{Duration, SystemTime};

fn jstr<'a>(env: &mut JNIEnv<'a>, s: &str) -> jstring {
    env.new_string(s)
        .map(|o| o.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

/// Minimaler JSON-String-Escaper (kein serde nötig für die eine Struktur, die wir
/// zurückgeben). Behandelt die von JSON verlangten Fälle: Backslash, Quote,
/// Steuerzeichen und die üblichen Whitespace-Escapes.
fn json_escape(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 16);
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out
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

// ─────────────────────────────────────────────────────────────
// M1/M2-Kern: kompiliert BELIEBIGEN Editor-Inhalt (nicht mehr fest eingebaut)
// und schreibt das Ergebnis als echte Datei in [job_dir] — nötig, damit die
// Kotlin-Seite das PDF mit Androids PdfRenderer öffnen kann.
//
// Anders als die Convenience-Funktion `latex_to_pdf` (die nur Bytes liefert und
// KEIN SyncTeX kann) nutzen wir hier den Low-Level-Treiber
// `ProcessingSessionBuilder`. Der erlaubt:
//   • .output_dir(...)        → PDF/Log/SyncTeX als Dateien ablegen
//   • .synctex(true)          → SyncTeX-Ausgabe aktivieren (M3-Vorbereitung)
//   • .keep_logs(true)        → document.log für die Fehler-Extraktion behalten
//
// Rückgabe: ein JSON-String, den Kotlin (CompileResult) parst:
//   {"ok":bool,"pdfPath":"…","synctexPath":"…","log":"…","error":"…"}
// Die eigentliche Fehler-/Zeilennummer-Extraktion passiert bewusst in Kotlin
// (LatexLog.parse) — dort testbar, ohne die .so neu zu bauen.
//
// ⚠️ Wie tectonicCompile: blockiert + lädt beim ersten Mal das Bundle → nur vom
//    Hintergrund-Thread aufrufen.
// ─────────────────────────────────────────────────────────────
#[no_mangle]
pub extern "system" fn Java_de_bgg_1home_texdroid_RustBridge_tectonicCompileToFile<'a>(
    mut env: JNIEnv<'a>,
    _class: JClass<'a>,
    tex_source: JString<'a>,
    job_dir: JString<'a>,
    // Compile-Datum als Unix-Epoch-Sekunden. Kotlin reicht die LOKALE Wanduhr-
    // zeit (currentTimeMillis + TZ-Offset) durch; wir zwingen die Engine unten
    // per TZ=UTC dazu, genau diese Komponenten für \today/\time zu verwenden.
    // <= 0 (unbekannt) → Fallback auf die System-Uhr des Geräts.
    build_epoch: jlong,
) -> jstring {
    let source: String = match env.get_string(&tex_source) {
        Ok(s) => s.into(),
        Err(_) => return jstr(&mut env, r#"{"ok":false,"error":"tex_source ungültig"}"#),
    };
    let dir: String = match env.get_string(&job_dir) {
        Ok(s) => s.into(),
        Err(_) => return jstr(&mut env, r#"{"ok":false,"error":"job_dir ungültig"}"#),
    };

    let result = compile_to_dir(&source, &dir, build_epoch);
    let json = match result {
        Ok(out) => format!(
            r#"{{"ok":true,"pdfPath":"{}","synctexPath":"{}","log":"{}","error":""}}"#,
            json_escape(&out.pdf_path),
            json_escape(&out.synctex_path),
            json_escape(&out.log),
        ),
        Err(err) => format!(
            r#"{{"ok":false,"pdfPath":"","synctexPath":"","log":"{}","error":"{}"}}"#,
            json_escape(&err.log),
            json_escape(&err.message),
        ),
    };
    jstr(&mut env, &json)
}

struct CompileOk {
    pdf_path: String,
    synctex_path: String,
    log: String,
}

struct CompileErr {
    message: String,
    log: String,
}

/// Der eigentliche Compile über den Tectonic-Treiber. Getrennt von der
/// JNI-Funktion, damit die JNI-Schicht dünn bleibt.
fn compile_to_dir(source: &str, dir: &str, build_epoch: jlong) -> Result<CompileOk, CompileErr> {
    use tectonic::config::PersistentConfig;
    use tectonic::driver::{OutputFormat, ProcessingSessionBuilder};
    use tectonic::status::NoopStatusBackend;

    // Wie in tectonicCompile: Tectonics Cache (app_dirs2/XDG) aufs beschreibbare
    // App-Verzeichnis biegen, sonst schlägt der Bundle-Cache fehl.
    std::env::set_var("XDG_CACHE_HOME", dir);
    std::env::set_var("HOME", dir);

    // XeTeX berechnet \today/\time via localtime(build_date). Kotlin liefert die
    // lokale Wanduhrzeit bereits als „UTC-kodierte" Epoch — mit TZ=UTC gibt
    // localtime exakt diese Komponenten zurück, unabhängig von der (im
    // Sandbox-Prozess oft fehlenden) Geräte-Zeitzone.
    std::env::set_var("TZ", "UTC0");

    let out_dir = Path::new(dir);

    let mut status = NoopStatusBackend::default();

    let config = PersistentConfig::open(false)
        .map_err(|e| err_no_log(format!("Konfiguration öffnen fehlgeschlagen: {}", e)))?;
    let bundle = config
        .default_bundle(false)
        .map_err(|e| err_no_log(format!("Paket-Bundle laden fehlgeschlagen: {}", e)))?;
    let format_cache = config
        .format_cache_path()
        .map_err(|e| err_no_log(format!("Format-Cache-Pfad fehlgeschlagen: {}", e)))?;

    let mut builder = ProcessingSessionBuilder::default();
    builder
        .bundle(bundle)
        .primary_input_buffer(source.as_bytes())
        .tex_input_name("document.tex")
        // Dateisystem-Wurzel = Arbeitsverzeichnis, damit \input{...}/\include{...}
        // auf die (von Kotlin dorthin kopierten) Projektdateien auflösen. Ohne dies
        // sucht Tectonic bei Buffer-Eingabe im Prozess-CWD ("/") und findet nichts.
        .filesystem_root(out_dir)
        .format_name("latex")
        .format_cache_path(format_cache)
        .output_format(OutputFormat::Pdf)
        .output_dir(out_dir)
        .keep_logs(true)
        .keep_intermediates(true) // nötig, damit die .synctex.gz erhalten bleibt
        .synctex(true); // ← SyncTeX-Ausgabe aktivieren (M3-Vorbereitung)

    // Compile-Datum: ohne dies fällt Tectonic auf UNIX_EPOCH zurück → \today
    // ergäbe „1. Januar 1970". Positive Epoch aus Kotlin verwenden; sonst
    // (unbekannt/ungültig) die echte System-Uhr des Geräts.
    let build_date = if build_epoch > 0 {
        SystemTime::UNIX_EPOCH + Duration::from_secs(build_epoch as u64)
    } else {
        SystemTime::now()
    };
    builder.build_date(build_date);

    let mut session = builder
        .create(&mut status)
        .map_err(|e| err_no_log(format!("Session anlegen fehlgeschlagen: {}", e)))?;

    let run_result = session.run(&mut status);

    // Das TeX-Log liegt jetzt (dank keep_logs) als document.log im out_dir.
    let log = std::fs::read_to_string(out_dir.join("document.log")).unwrap_or_default();

    match run_result {
        Ok(()) => {
            let pdf = out_dir.join("document.pdf");
            if !pdf.exists() {
                return Err(CompileErr {
                    message: "Compiler meldete Erfolg, aber es wurde kein PDF geschrieben."
                        .to_string(),
                    log,
                });
            }
            // SyncTeX schreibt Tectonic gzip-komprimiert als document.synctex.gz.
            let synctex = out_dir.join("document.synctex.gz");
            let synctex_path = if synctex.exists() {
                synctex.to_string_lossy().into_owned()
            } else {
                String::new()
            };
            Ok(CompileOk {
                pdf_path: pdf.to_string_lossy().into_owned(),
                synctex_path,
                log,
            })
        }
        Err(e) => Err(CompileErr {
            message: format!("{}", e),
            log,
        }),
    }
}

fn err_no_log(message: String) -> CompileErr {
    CompileErr {
        message,
        log: String::new(),
    }
}

# TexDroid

**Nativer LaTeX/XeTeX-Editor für Android — Tablet-first.**

Schreibe LaTeX direkt auf dem Tablet und sieh live nebenan das PDF — ohne Terminal,
ohne Cloud-Zwang. TexDroid verbindet Editor, lokalen Compiler und PDF-Vorschau in
einer nativen Android-Oberfläche.

## Ziel (v1.0)

Eine Person mit Android-Tablet kann: App aus F-Droid installieren → Projektordner
wählen → `.tex` schreiben → live nebenan das PDF sehen → bei Fehlern zur Zeile
springen.

## Warum

Auf Android/F-Droid gibt es bisher **keine** Open-Source-App, die Editor,
PDF-Vorschau und einen lokalen, XeTeX-fähigen Compiler nahtlos in einer Oberfläche
vereint. Vorhandenes ist entweder reine Terminal-Bedienung (Termux + TeX Live),
proprietär/cloud-abhängig (VerbTeX) oder nur ein Formel-Renderer.

## Tech-Stack

| Komponente   | Technologie |
|--------------|-------------|
| UI           | Jetpack Compose (Kotlin), adaptive Layouts via `WindowSizeClass` |
| Editor       | [`sora-editor`](https://github.com/Rosemoe/sora-editor) — Syntax-Highlighting |
| Compiler     | [Tectonic](https://tectonic-typesetting.github.io/) (Rust, MIT) via `cargo-ndk` als `.so`, JNI-Bindung Rust ↔ Kotlin |
| PDF-Anzeige  | Android `PdfRenderer` (Bordmittel) |
| Dateizugriff | Storage Access Framework (SAF) |

**ABI-Targets:** `arm64-v8a`, `armeabi-v7a`.

## Status

🚧 In früher Entwicklung. Roadmap und Milestones siehe [`PROJECT.md`](./PROJECT.md).

- [ ] **M0** — Proof of Concept (Rust↔Kotlin-Brücke, erstes PDF lokal erzeugt)
- [ ] **M1** — Basis-Editor + Compile-Loop
- [ ] **M2** — PDF-Preview + Tablet-Split-View
- [ ] **M3** — Live/Auto-Compile & UX
- [ ] **M4** — Projektverwaltung (Multi-File)
- [ ] **M5** — F-Droid-Release

## Lizenz

[GNU General Public License v3.0](./LICENSE) (GPLv3). Kompatibel mit Tectonic (MIT).
Der Quellcode bleibt frei; Play-Store-Distribution bleibt erlaubt.

---

_Nicht verwechseln mit der gleichnamigen, inaktiven Render-Library `hansihe/TexDroid` —
TexDroid ist eine eigenständige Editor-App._

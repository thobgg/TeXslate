# Projekt: TexDroid — Nativer LaTeX/XeTeX-Editor für Android (Tablet-first)

> **Dieses Dokument ist der vollständige Projektkontext.** Es entstand aus einer
> Planungs-Session und enthält alles Nötige: Motivation, Architektur, Milestones,
> Risiken und Release-Strategie. Verwende es als Grundlage
> für die Weiterentwicklung — es gibt keinen weiteren Kontext.

---

## 1. Motivation & Marktlücke

Es gibt auf Android/F-Droid **keine** Open-Source-App, die Editor, PDF-Vorschau
und lokalen LaTeX-Compiler (XeTeX-fähig) nahtlos in einer Oberfläche verbindet.

Vorhandene Alternativen und warum sie nicht reichen:
- **Termux + TeX Live / Tectonic**: Voll funktionsfähig, aber reine
  Terminal-Bedienung — keine integrierte UX, hohe Einstiegshürde.
- **VerbTeX u.ä.**: Proprietär bzw. Cloud-abhängig.
- **jlatexmath / AndroidMath**: Nur Formel-Renderer, keine vollständige Engine.
- **Kile-Portierung**: Verworfen — KDE-Frameworks/Qt-Widgets sind auf Android
  schlecht unterstützt und nicht touch-tauglich.
- **Eigene XeTeX-Engine schreiben**: Verworfen — jahrzehntealter C-Codebase
  (web2c, ICU, HarfBuzz, FreeType), reine Doppelarbeit.

**Gewählter Ansatz:** Vorhandene Engine wiederverwenden (Tectonic, Rust, MIT),
nur das Frontend selbst bauen. Zielplattform primär **Tablets** (Split-View,
externe Tastatur, Stift), Phone als sekundäres Layout.

## 1a. Entwicklungsumgebung & Kontext des Entwicklers

- **Arbeitsrechner:** Xubuntu, **Android Studio bereits installiert.**
- **Tech-Stack ist Neuland:** Native Android-App (Kotlin/Compose + Rust/NDK).
  Gradle-, ADB-, JNI- und NDK-Konzepte bitte erklären, nicht als bekannt
  voraussetzen. Web-Parallelen sind willkommen, wo sie helfen
  (z.B. Compose ≈ deklaratives UI wie React).
- **GitHub von Anfang an:** Repo unter dem Account **thobgg** anlegen
  (öffentlich, da Open-Source-Beitrag geplant). Von Commit 1 an dort arbeiten:
  sinnvolle `.gitignore` (Android + Rust), README mit Projektziel,
  Lizenzdatei früh festlegen. Branch-Strategie siehe Abschnitt 4.
- **Motivations-Prinzip (verbindlich):** Keine wochenlange Arbeit ohne
  sichtbares Ergebnis. **Jede Arbeitssession endet mit etwas, das auf
  Emulator oder Tablet läuft und sichtbar mehr kann als vorher.**
  Die Milestones sind darum in kleine "Quick Wins" unterteilt — Aufgaben
  sind immer so zu schneiden, dass am Sessionende ein demonstrierbares
  Zwischenergebnis steht, das die Realisierbarkeit bestätigt.

## 2. Architektur

| Komponente | Technologie | Begründung |
|---|---|---|
| UI | Jetpack Compose, Kotlin | Modern, adaptive Layouts via `WindowSizeClass` |
| Editor | `sora-editor` (Open Source) | Syntax-Highlighting vorhanden/erweiterbar, nicht selbst schreiben |
| Compiler | **Tectonic** (Rust, MIT) via `cargo-ndk` als `.so`, JNI-Bindung Rust↔Kotlin | Schlanker als volles TeX Live, XeTeX-basiert, lädt Pakete selbst nach (Internet nur beim ersten Compile, danach Cache) |
| PDF-Anzeige | Android `PdfRenderer` (Bordmittel) | Keine Zusatz-Lib nötig |
| Dateizugriff | Storage Access Framework (SAF) | Nutzer wählt eigene Projektordner |

**ABI-Targets:** `arm64-v8a`, `armeabi-v7a`.

**Layout-Strategie (Tablet-first):**
- Tablet/breite Screens: echte Split-View — Editor links, PDF-Preview rechts.
- Phone/schmale Screens: Tab-Wechsel Editor ↔ PDF als Fallback.
- Von Anfang an mit `WindowSizeClass` bauen, nicht Phone-first nachrüsten.

## 3. Milestones

**Pro Milestone eine eigene Session/Branch.**

Jeder Milestone ist in kurze **Quick Wins (QW)** zerlegt.
Jeder QW endet mit einem **sichtbaren, demonstrierbaren Ergebnis** ("🎉 Sichtbar:").
Reihenfolge innerhalb eines Milestones einhalten; nach jedem QW committen.

### M0 — Proof of Concept
**Höchstes Risiko des Projekts** — als isolierter Spike, bevor App-Architektur
entsteht. Bewusst so geschnitten, dass es schon nach der ersten Stunde ein
Erfolgserlebnis gibt:

- [x] **QW 0.1:** GitHub-Repo (thobgg, öffentlich) + leere
      Compose-App "TexDroid" in Android Studio anlegen, auf Emulator/Tablet
      starten, erster Push.
      🎉 Sichtbar: eigene App mit Namen und Hello-Screen läuft auf dem Gerät,
      Projekt ist ab Minute 1 auf GitHub.
- [x] **QW 0.2:** Rust-Toolchain + `cargo-ndk` auf Xubuntu
      einrichten; Mini-Rust-Lib ("add(a,b)") als `.so` bauen, per JNI aus
      Kotlin aufrufen, Ergebnis in der App anzeigen.
      🎉 Sichtbar: "2+3=5, berechnet in Rust" auf dem Bildschirm —
      die kritische Rust↔Kotlin-Brücke steht, ganz ohne Tectonic-Komplexität.
- [x] **QW 0.3:** Tectonic cross-compilen und einbinden.
      Statt `arm64-v8a` zuerst **`x86_64`** (der Emulator ist x86_64) — C-Stack via
      vcpkg (`TECTONIC_DEP_BACKEND=vcpkg`), siehe README. `arm64-v8a` inzwischen
      ebenfalls gebaut (via ABI-Splits eigene Tablet-APK), aber noch nicht auf
      echtem Gerät getestet.
      🎉 Sichtbar: App zeigt „Tectonic-Engine eingebettet ✓ / XeTeX format serial: 33"
      auf dem Gerät.
- [x] **QW 0.4:** Fest einprogrammiertes Mini-`.tex` wird beim App-Start
      kompiliert (Auto-Compile statt Button — Button folgt mit dem Editor), Dateigröße
      des PDFs angezeigt. Cache via `XDG_CACHE_HOME` aufs App-Verzeichnis, `INTERNET`-
      Permission fürs Bundle.
      🎉 Sichtbar: **Erstes PDF, lokal auf Android erzeugt** („PDF erzeugt ✓ 8281 Bytes")
      — Realisierbarkeit des Gesamtprojekts damit bewiesen. **M0 abgeschlossen.**
- `armeabi-v7a` erst nach M0 nachziehen — nicht am Anfang doppelt kämpfen.

### M1 — Basis-Editor + Compile-Loop
- [x] **QW 1.1:** `sora-editor` einbinden, Beispieltext editierbar.
      🎉 Sichtbar: Editor mit Syntax-Highlighting in der eigenen App.
- [x] **QW 1.2:** `.tex` über SAF öffnen/speichern.
      🎉 Sichtbar: echte eigene Datei vom Tablet-Speicher laden und ändern.
- [x] **QW 1.3:** Compile-Button → Tectonic → Fehlerausgabe im Log-Fenster.
      🎉 Sichtbar: eigener, live getippter LaTeX-Code wird kompiliert;
      absichtlicher Fehler erzeugt lesbare Fehlermeldung.
- Noch kein PDF-Viewer — nur Erfolg/Fehler sichtbar machen.

### M2 — PDF-Preview + Tablet-Layout
- [x] **QW 2.1:** `PdfRenderer` zeigt das kompilierte PDF (eigener Screen reicht).
      🎉 Sichtbar: Tippen → Kompilieren → eigenes PDF in der eigenen App sehen.
      **Ab hier ist die App bereits täglich nutzbar** — guter Zeitpunkt, sie
      selbst für echte kleine Dokumente einzusetzen (Dogfooding = Motivation).
- [x] **QW 2.2:** Split-View (Tablet) / Tab-Fallback (Phone) via `WindowSizeClass`.
      🎉 Sichtbar: Editor und PDF nebeneinander auf dem Tablet — der
      "Overleaf-Moment".
- [x] **QW 2.3:** PDF nach Compile automatisch neu laden, Scroll-Position halten.
      🎉 Sichtbar: flüssiger Edit→Compile→Preview-Loop ohne manuelles Neuladen.

### M3 — Live/Auto-Compile & UX
- [x] **QW 3.1:** Debounced Auto-Compile bei Tippstopp.
      🎉 Sichtbar: PDF aktualisiert sich "von selbst" beim Schreiben.
- [x] **QW 3.2:** Fehlerzeilen im Editor markieren, Jump-to-Error.
      🎉 Sichtbar: Tipp auf Fehler springt zur Zeile.
- [x] **QW 3.3:** LaTeX-Highlighting verfeinern; externe Tastatur + Stift
      (S Pen) auf dem Tablet testen. (Editor-Komfort umgesetzt: Zeilenumbruch,
      Auto-Klammern, 2er-Einrückung, Completion; S-Pen/HW-Tastatur am echten
      Gerät noch vom Entwickler zu prüfen.)
      🎉 Sichtbar: angenehmes Schreiben mit Hardware-Tastatur.

### M4 — Projektverwaltung
- [ ] **QW 4.1:** Dateibaum-Sidebar (Tablet), Datei-Wechsel per Tap.
      🎉 Sichtbar: Projektordner mit mehreren Dateien navigierbar.
- [ ] **QW 4.2:** Multi-File-Compile: `\input` / `\include` funktionieren.
      🎉 Sichtbar: mehrteiliges Dokument (z.B. Kapitel-Dateien) baut korrekt.
- [ ] **QW 4.3:** Bibliografie prüfen (biber, sofern Tectonic abdeckt —
      sonst als bekanntes Limit dokumentieren und verschieben).

### M5 — F-Droid-Release
- [ ] Reproducible Build, keine proprietären Abhängigkeiten
- [ ] Lizenzcheck: Tectonic = MIT ✓; nachgeladene TeX-Pakete/Fonts (LPPL etc.)
      prüfen, v.a. falls gebündelt statt nachgeladen
- [ ] F-Droid-Metadata, Screenshots (Tablet-Auflösung!), Submission

### M6 — Play-Store-Release (optional)
- [ ] Eigene Build-Variante (F-Droid-Build ohne Google-Services getrennt halten)
- [ ] Entwicklerkonto (25 $ einmalig), Datenschutzerklärung (auch ohne
      Datensammlung erforderlich)
- [ ] Play App Signing sauber aufsetzen
- [ ] Einplanen: Review kann bei nativen `.so`-Libs länger dauern (Malware-Scan)

### M7 — Nice-to-have (bewusst außerhalb des Kernscopes)
- Stift-Input für Formel-Skizzen, später evtl. Handwriting-to-LaTeX.
  **Nicht in M0–M6 ziehen** — Scope-Disziplin.

## 4. Arbeitsweise & Branch-Strategie

- **Oberste Regel:** Aufgaben immer entlang der Quick Wins schneiden. Wenn
  eine Session endet, muss das "🎉 Sichtbar:"-Kriterium des aktuellen QW
  erfüllt sein — lieber einen QW sauber abschließen als zwei anreißen.
  Niemals mehrere Tage "unsichtbare" Infrastruktur bauen.
- Ein Milestone = ein Branch; erst mergen, wenn alle QW-Checkboxen erfüllt
  sind. Ein QW = typischerweise eine Arbeitssession + ein Commit.
- M0 als isolierten Spike behandeln: separates Repo/Verzeichnis ist ok,
  Ergebnis dann ins Hauptprojekt übernehmen.
- Bei M0-Blockern (NDK/Rust-Linking): zuerst prüfen, ob es fertige
  Tectonic-Android-Ports/Issues im Tectonic-Repo gibt, bevor selbst gepatcht wird.
- **Lizenz festgelegt: GPLv3** (`LICENSE`-Datei liegt vor). Play-Store-Verkauf
  erlaubt, Quellcode muss frei bleiben. Kompatibel mit Tectonic (MIT).

## 5. Definition of Done (v1.0)

Eine Person mit Android-Tablet kann: App aus F-Droid installieren → Projektordner
wählen → `.tex` schreiben → live nebenan das PDF sehen → bei Fehlern zur
Zeile springen. Ohne Terminal, ohne Cloud-Zwang.

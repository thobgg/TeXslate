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

**ABI-Targets:** `arm64-v8a` (echte Geräte, produktiv) + `x86_64` (Emulator),
je als eigene APK (ABI-Splits). `armeabi-v7a` (alte 32-bit-Geräte) noch offen.

**Layout-Strategie (Tablet-first):**
- Tablet/breite Screens: echte Split-View — Editor links, PDF-Preview rechts.
- Phone/schmale Screens: Tab-Wechsel Editor ↔ PDF als Fallback.
- Von Anfang an mit `WindowSizeClass` bauen, nicht Phone-first nachrüsten.

## 3. Milestones

> **Stand:** M0–M4, MA (KI), ME (Editor-Komfort), MR (Alpha-Release) ✅ —
> `v1.0-alpha1` als GitHub-Prerelease veröffentlicht, auf 3 echten Geräten
> (Android 11 & 16) verifiziert. Offen: M5 (F-Droid), M6 (Play Store),
> Rest-Testabdeckung (Handy, 32-bit, S-Pen).

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
      ebenfalls gebaut (via ABI-Splits eigene Tablet-APK) und auf echten Geräten
      verifiziert (Galaxy Tab S8 Ultra, S9, S5e).
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
- [x] **QW 4.1:** Dateibaum-Sidebar (Tablet), Datei-Wechsel per Tap.
      🎉 Sichtbar: Projektordner mit mehreren Dateien navigierbar.
      (SAF-Tree-Uri persistent, Sidebar via ModalNavigationDrawer, Auswahl
      lädt die Datei sofort; Projekt wird beim Neustart wiederhergestellt.)
- [x] **QW 4.2:** Multi-File-Compile: `\input` / `\include` funktionieren.
      🎉 Sichtbar: mehrteiliges Dokument (z.B. Kapitel-Dateien) baut korrekt.
      (ProjectStore.syncToDir kopiert den Projektbaum ins Arbeitsverzeichnis;
      nativ `.filesystem_root(out_dir)`, damit Tectonic die Dateien findet.)
- [x] **QW 4.3:** Bibliografie funktioniert.
      🎉 Sichtbar: `\cite{...}` + `\bibliography{...}` erzeugen Zitat [1] und
      Literaturverzeichnis. Tectonics eingebautes bibtex macht die
      Mehrfach-Durchläufe, QW 4.2 kopiert die `.bib` mit.
      • Klassisches `bibtex` (`\bibliography`): funktioniert.
      • `biblatex` mit `backend=bibtex` (`\autocite`, `\printbibliography`,
        biblatex-Stile, korrektes Unicode): funktioniert ebenfalls — am Tablet
        verifiziert.
      • Zwischendateien werden vor jedem Compile aufgeräumt
        (`LatexCompiler.cleanAuxArtifacts`), sonst bricht ein Wechsel des
        Bib-Systems an einer veralteten `.bbl`.
      ⚠️ Bekanntes Limit: `biblatex` mit dem Default-Backend `biber` (externes
      biber-Binary) wird auf Android noch nicht unterstützt — Workaround:
      `backend=bibtex` setzen. Vollständiges biber = separates, größeres Vorhaben.

### MA — KI-Assistent (BYOK, optional)
- [x] **QW A1:** Verschlüsselte API-Key-Ablage (Android Keystore, AES-256-GCM),
      Opt-in in den Einstellungen. Ohne Key bleibt die App voll nutzbar.
      🎉 Sichtbar: eigener Schlüssel wird sicher auf dem Gerät gespeichert.
- [x] **QW A2:** KI-Assistent-Sheet — Frage stellen, Kontext (Auswahl/Dokument),
      Antwort anzeigen. Zentriertes Dialog-Layout, das die Tastatur nicht überdeckt.
      🎉 Sichtbar: Frage → Antwort direkt in der App.
- [x] **QW A3/A4:** Multi-Provider (Anthropic / OpenAI / Gemini) über
      `HttpURLConnection` + `org.json` (keine Netzwerk-Abhängigkeit).
      🎉 Sichtbar: derselbe Dialog funktioniert mit dem Anbieter der Wahl.
- [x] **QW A5:** Gespräch mit Rückfragen (Kontext bleibt über mehrere Runden).
      🎉 Sichtbar: „mach die erste Spalte fett" bezieht sich auf die vorige Antwort.
- [x] **QW A6:** Deutsche Fehlermeldungen (HTTP-Status + TeX-Fehler übersetzt).
      🎉 Sichtbar: „API-Key ungültig" statt kryptischem 401.
- [x] **QW A7:** KI-Inhalt einfügen — Am Cursor / Am Dokumentende / Ersetzen;
      Code-Fences werden entfernt. „Fehler erklären" schickt den Compile-Fehler an die KI.
      🎉 Sichtbar: generierter Beweis landet sauber im Dokument und kompiliert.
      ⚠️ Netzwerk-Nutzung (nur bei aktivem Key) — für F-Droid als „NonFreeNetwork"
      zu kennzeichnen; Opt-in, Kernfunktionen bleiben offline.

### ME — Editor-Komfort & Branding
- [x] **QW E.1:** Suchen & Ersetzen im Editor.
      🎉 Sichtbar: Lupe in der Toolbar → Suchen mit Live-Markierung + Zähler,
      Weiter/Zurück, Ersetzen/Alle, Groß/Klein (Aa) und Regex (.*).
      (Bindet die Such-Engine von sora-editor an; ungültiges Regex → „Muster?".)
- [x] **QW E.2:** Gehe zu Zeile.
      🎉 Sichtbar: Kebab → „Gehe zu Zeile…" springt zur eingegebenen Zeile.
- [x] **QW E.3:** Kommentar ein/aus.
      🎉 Sichtbar: Kebab → „Kommentar ein/aus" (ent)kommentiert Zeile/Auswahl
      mit `%` (ein Undo-Schritt).
- [x] **Branding:** eigenes TeX-Icon (Latin Modern / Computer Modern) als reines
      Vektor-Adaptive-Icon (Vordergrund + blauer Verlauf + monochrom), ohne
      Bugdroid → F-Droid-tauglich; 512×512-Store-PNG; TeX-Badge im App-Header.

### MR — Alpha-Release & Multi-Device-Tests
- [x] **Multi-Device-Tests:** Galaxy Tab S8 Ultra (Android 16), Tab S9 (16),
      Tab S5e (Android 11, Snapdragon 670) — Layout (Split-View) + nativer
      Tectonic-Compile auf jedem verifiziert.
- [x] **First-Compile-Hinweis:** der erste Compile pro Installation lädt einmalig
      das TeX-Bundle (~1–2 Min, netz-gebunden) → Hinweis statt „hängt?".
- [x] **Release-Signierung:** eigener Keystore, `keystore.properties` (gitignored),
      signingConfig nur wenn vorhanden (F-Droid-Build bleibt unsigniert statt zu brechen).
- [x] **GitHub-Prerelease `v1.0-alpha1`** mit arm64-v8a- + x86_64-APK, per
      Obtainium abonnierbar. Erste Tester können loslegen.
      🎉 Sichtbar: signierte APK öffentlich installierbar.
- [ ] Offene Test-Lücken: Handy-Format (Hochformat), `armeabi-v7a` (32-bit), S-Pen.
- [ ] Nächster Alpha-Build: `versionCode` hochzählen (Update-Pflicht).

### M5 — F-Droid-Release
- [ ] Reproducible Build, keine proprietären Abhängigkeiten.
      ⚠️ Großer Brocken: die native `.so` (Tectonic + C-Stack via vcpkg/cargo-ndk)
      muss auf F-Droids Buildservern aus dem Quellcode bauen — entscheidet, ob
      F-Droid praktikabel ist. Play Store (M6) ist technisch einfacher.
- [ ] Lizenzcheck: Tectonic = MIT ✓; nachgeladene TeX-Pakete/Fonts (LPPL etc.)
      prüfen, v.a. falls gebündelt statt nachgeladen. KI = NonFreeNetwork-Opt-in.
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

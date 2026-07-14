# F-Droid-Einreichung (Haupt-Repo) — Anleitung

Stand 14.07.2026. Milestone **M5**. Das Native-Build-Gate ist genommen
(Phase A grün, lokal bewiesen): vcpkg-C-Stack-Kaltbau 6:43 min + Rust/Tectonic
1:49 min, rc=0. Metadata liegt in `de.bgg_home.texslate.yml` (dieser Ordner).

## Vor der Einreichung final bestätigen
- **NDK-Version:** im YAML steht `27.2.12479018` (r27c). Bestätigen, dass F-Droids
  aktueller Buildserver diese Version provisioniert; sonst meckert die CI klar,
  1-Zeilen-Fix (`ndk:`-Feld).
- **Rust im Build-VM:** F-Droid stellt `rustup`/`cargo` i. d. R. bereit. Falls
  die CI „rustup: command not found" zeigt, oben im `prebuild` eine Bootstrap-
  Zeile ergänzen.
- **vcpkg-Tag:** auf `2026.06.24` (neuester Release-Tag) gepinnt. Lokal bewiesen
  wurde Commit `09c52f84` (2026-07-04); bei Port-Abweichung ist das die Baseline.
  ✅ erledigt.
- **Screenshots:** liegen als `tenInchScreenshots` in
  `fastlane/metadata/android/{de-DE,en-US}/` — F-Droid liest das automatisch.

## Schritte (GitLab-Account nötig)
1. https://gitlab.com/fdroid/fdroiddata **forken**.
2. Fork klonen: `git clone git@gitlab.com:<dein-user>/fdroiddata.git`
3. Branch: `git checkout -b texslate`
4. `de.bgg_home.texslate.yml` nach `metadata/` im Fork kopieren.
5. (Optional, lokal) `pip install fdroidserver` und im Fork-Root
   `fdroid lint de.bgg_home.texslate` → sollte fehlerfrei sein.
6. `git add metadata/de.bgg_home.texslate.yml && git commit`
   (Commit-Message-Konvention von fdroiddata: `New App: de.bgg_home.texslate`).
7. Push + **Merge-Request** auf fdroiddata öffnen, mit dem Text unten.

## MR-Beschreibung (fertig zum Einfügen)

> **New App: TeXslate (de.bgg_home.texslate)**
>
> Nativer LaTeX/XeTeX-Editor für Android-Tablets: integrierter Editor,
> On-Device-Compiler (Tectonic) und PDF-Vorschau — ohne Cloud-Zwang oder
> Begleit-PC. GPLv3, Quelle: https://github.com/thobgg/TeXslate
>
> **Native Build:** Die native Lib (Tectonic + C-Stack ICU/HarfBuzz/FreeType/
> fontconfig/graphite2/libpng via vcpkg, alles aus Quellcode via cmake/ninja)
> wurde lokal auf Debian verifiziert: C-Stack-Kaltbau ~7 min, Rust-Teil ~2 min.
> Nur arm64-v8a (alle realen Zielgeräte; hält Build/Größe klein).
>
> **AntiFeature NonFreeNetwork:** Ein *optionaler* KI-Assistent kann Text mit
> einem **vom Nutzer selbst hinterlegten** API-Key an Anthropic/OpenAI/Google
> senden. Standardmäßig aus, reines HTTPS, kein proprietäres SDK.
>
> **Hinweis zum TeX-Bundle:** Tectonic lädt beim ersten Compile einmalig das
> TeX-Bundle (freie `.tex`/`.sty`/Font-Assets, LPPL etc. — **keine**
> ausführbaren Binaries) über das Netz. Das ist Kernfunktion des Compilers.
>
> **Getestet auf echten Geräten** (Compile→PDF, inkl. Multi-File + Bibliografie):
> Qualcomm/Samsung-Flaggschiffe; ein altes Samsung Tab S5e mit **LineageOS /
> Android 15 (ohne Google-Services)**; und ein Lenovo Tab Plus (MediaTek) bei
> einem externen Tester. Keine Abstürze; drei Hardware-Familien.

## Restrisiko
- Erst-Einreichung eines Tectonic-basierten Projekts bei F-Droid (kein
  Präzedenz-Rezept) → mit Nachfragen der Reviewer rechnen, v. a. zum
  vcpkg-Schritt und zum Bundle-Download.

# thesis-Edition — biber-Runtime

Die `thesis`-Edition bündelt eine cross-gebaute **biber**-Runtime (Perl 5.36.3 für
Android/Bionic + Text::BibTeX/btparse, XML::LibXML/XSLT u.a. + biber 2.17), damit
Tectonic beim Kompilieren `biber` aufrufen kann (biblatex mit `backend=biber`).

Die `core`-Edition enthält das **nicht** (schlank, biblatex braucht dort
`backend=bibtex`; der Preflight erklärt es).

## Bereitzustellende Artefakte (NICHT in Git — siehe `.gitignore`)

Vor einem `thesis`-Build müssen hier liegen:

```
app/src/thesis/assets/biber-tree.zip                      # getrimmter Perl-Baum (~13 MB)
app/src/thesis/jniLibs/arm64-v8a/libperl_exe.so           # perl-Binary (exec via nativeLibDir)
app/src/thesis/jniLibs/arm64-v8a/libbiber_launcher.so     # Launcher (exec't perl <biber-skript>)
```

Fehlen sie, baut die Edition trotzdem, aber `BiberRuntime.ensureReady` liefert `false`
→ Verhalten wie `core` (Preflight statt biber).

## Woher

Fertige Artefakte + reproduzierbares Rezept liegen außerhalb des Repos unter
`~/biber-android/`:

- `~/biber-android/pkg/` — fertiges Paket (einfach hierher kopieren):
  ```
  cp ~/biber-android/pkg/biber-tree.zip                 app/src/thesis/assets/
  cp ~/biber-android/pkg/jniLibs/arm64-v8a/libperl_exe.so       app/src/thesis/jniLibs/arm64-v8a/
  cp ~/biber-android/pkg/jniLibs/arm64-v8a/libbiber_launcher.so app/src/thesis/jniLibs/arm64-v8a/
  ```
- `~/biber-android/scripts/` — vollständiges Bau-Rezept (`build-perl-bionic.sh`,
  `xsbuild.sh`, `biber-xs-recipe.md`, `packaging-plan.md`, `biber_launcher.c`),
  falls die Runtime neu gebaut werden muss.

## Architektur (Android W^X)

`useLegacyPackaging = true` extrahiert die jniLibs ins `nativeLibraryDir` (nur dort
ist `exec` erlaubt). Der Launcher wird als `biber` auf den PATH gelinkt und startet
`perl <biber-skript>`; der Perl-Baum wird aus dem Zip nach `filesDir/biber` entpackt,
XS-`.so` werden von dort dlopen't (auf dem Gerät erlaubt). Details: `BiberRuntime.kt`.

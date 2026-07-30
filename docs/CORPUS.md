# Testsammlung echter LaTeX-Dokumente

`CorpusRegressionTest` kompiliert 18 echte Dokumente über den Produktionspfad und
schlägt fehl, sobald eines nicht mehr durchläuft. Die Dokumente liegen **nicht**
im Repo (fremde Lizenzen, ~13 MB) — dieses Dokument beschreibt, wie man sie holt.

## Warum

Fast jeder Fehler, den TeXslate in freier Wildbahn produziert, kommt von
Dokumenten, die für **pdfLaTeX auf einem PC** geschrieben wurden. Aus diesen 18
Dokumenten sind an einem Tag fünf behobene App-Fehler entstanden, darunter zwei
Abstürze:

| Fund | Auslöser im Dokument |
|---|---|
| App-Absturz (dvipdfmx-Assertion nach EPS-Fehlerpfad) | REVTeX/APS mit `.eps`-Abbildungen |
| biber nach jedem App-Update weg (toter Symlink) | biblatex mit `backend=biber` |
| Compile-Abbruch | `\usepackage[latin1]{inputenc}` |
| Compile-Abbruch | `\usepackage[pdftex]{hyperref}` |
| „Unable to load picture" | `Fig3_aAs0-HSE.pdf` vs. `…-hse.pdf` auf der Platte |

## Aufbau

Ein Ordner je Fall, darin die Projektdateien und eine Datei `MAINFILE` mit dem
Namen der Hauptdatei:

```
01-article-sample2e/
  MAINFILE          → "sample2e.tex"
  sample2e.tex
```

## Quellen

| Fall | Bezugsquelle |
|---|---|
| 01/02 article, minimal | CTAN `macros/latex/base/{sample2e,small2e}.tex` |
| 03 ML-Paper | arXiv `1706.03762` (`https://arxiv.org/e-print/…`) |
| 04 Beamer-Vortrag | GitHub `josephwright/beamer`, `doc/examples/a-conference-talk` |
| 05 Lebenslauf | GitHub `moderncv/moderncv`, `manual/moderncv_userguide.tex` |
| 06 Buch | CTAN `info/lshort/english/lshort-6.4.src.tar.gz` |
| 07 Diagramme | CTAN `graphics/pgf/contrib/pgfplots/doc/pgfplotsexample.tex` |
| 08 Brief | CTAN `macros/latex/contrib/lettre/letex1.tex` |
| 09 eigener Beitrag | lokal (nicht öffentlich) |
| 10 IEEE | CTAN `macros/latex/contrib/IEEEtran/bare_jrnl.tex` |
| 11 Elsevier | CTAN `macros/latex/contrib/elsarticle/elsarticle-template-num.tex` |
| 12/13 AMS | CTAN `macros/latex/required/amscls/doc/{amsart-template,thmtest}.tex` |
| 14 Physik | CTAN `macros/latex/contrib/revtex/sample/aps/` (mit `.eps`!) |
| 15 biblatex+biber | CTAN `macros/latex/contrib/biblatex/doc/examples/01-introduction.tex` + `bibtex/bib/biblatex/biblatex-examples.bib` |
| 16 Editionsphilologie | CTAN `macros/latex/contrib/reledmac/examples/1-criticalnotes.tex` |
| 17/18 Chemie, Informatik | arXiv-Quellen (`physics.chem-ph`, `cs.DS`) |

## Ausführen

Die Sammlung als ZIP aufs Gerät legen und den Test über `am instrument` starten —
**nicht** über `connectedAndroidTest`, das deinstalliert die App danach und nimmt
die Sammlung mit:

```bash
cd <sammlung>/ && zip -qr /tmp/corpus.zip .
adb push /tmp/corpus.zip /sdcard/Android/data/de.bgg_home.texslate/files/corpus.zip

./gradlew :app:installThesisDebug :app:installThesisDebugAndroidTest
adb shell am instrument -w \
  -e class de.bgg_home.texdroid.compile.CorpusRegressionTest \
  de.bgg_home.texslate.test/androidx.test.runner.AndroidJUnitRunner

adb logcat -d -s CORPUS
```

Fehlt `corpus.zip`, überspringt sich der Test selbst. Für den biber-Fall (15) muss
die **thesis**-Edition installiert sein; in `core` meldet er erwartungsgemäß den
bibtex-Preflight.

Warum Unterordner per ZIP und nicht direkt per `adb push`: Von `adb` angelegte
Verzeichnisse gehören dem Nutzer `shell`, die App kann sie nicht betreten
(`canRead()` ist false). Einzelne gepushte Dateien sind dagegen lesbar — deshalb
kommt die Sammlung als eine Datei und wird in der App entpackt.

package de.bgg_home.texdroid.compile

import android.system.Os
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import de.bgg_home.texdroid.RustBridge
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * EXPERIMENT 2 (U1) — Integrations-Sonde: Kann die App ein biber-Binary
 * ausführen und findet/ruft Tectonics eingebaute biber-Orchestrierung es auf?
 *
 * Hintergrund (aus driver.rs 0.16.9): Tectonic parst die `.run.xml`, erkennt
 * `<binary>biber</binary>` und ruft `Command::new("biber").output()`. Ohne biber
 * auf PATH → `os error 2` (No such file). Diese Sonde legt ein **Fake-biber**
 * (arm64-ELF, als androidTest-jniLib `libbiber.so` paketiert, im
 * nativeLibraryDir ausführbar) als `biber` auf PATH und prüft:
 *
 *   Verschwindet der `os error 2`, sobald biber auffindbar ist?  → JA = exec +
 *   Discovery + Rerun-Loop der App sind bewiesen; es fehlt NUR das echte Binary.
 *
 * Voll-Grün (aufgelöstes PDF) ist hier NICHT das Ziel: die Desktop-`.bbl` hat
 * bbl-Format 3.3, das Tectonic-Bundle biblatex 3.17 → Versions-Skew. Entscheidend
 * ist allein das Verschwinden des ENOENT.
 *
 * Läuft nur auf dem Gerät. `adb logcat -s BIBER_EXEC`.
 *
 * ⚠️ BENÖTIGT `extractNativeLibs=true`: sonst werden jniLibs NICHT ins
 * nativeLibraryDir extrahiert (moderner Default mappt sie nur aus dem APK, kein
 * exec'bares File auf Platte) und die Sonde meldet sauber „nicht gefunden". Zum
 * Reproduzieren temporär in app/build.gradle.kts:
 *     android { packaging { jniLibs { useLegacyPackaging = true } } }
 * Ergebnis am 14.07.2026 (Tab S8 Ultra): GRÜN++ — ok=true, PDF mit Bibliografie.
 */
@RunWith(AndroidJUnit4::class)
class BiberExecProbe {

    private val tag = "BIBER_EXEC"

    private val BIBER_DOC = """
        \documentclass{article}
        \usepackage[backend=biber]{biblatex}
        \addbibresource{refs.bib}
        \begin{document}
        Test~\cite{knuth1984}.
        \printbibliography
        \end{document}
    """.trimIndent()

    private val REFS_BIB =
        "@book{knuth1984, author={Knuth, Donald E.}, title={The {\\TeX}book}, " +
            "year={1984}, publisher={Addison-Wesley}}"

    /** libbiber.so in einem der (Test-/App-)nativeLibraryDirs finden. */
    private fun findFakeBiber(): File? {
        val inst = InstrumentationRegistry.getInstrumentation()
        val dirs = listOf(
            inst.context.applicationInfo.nativeLibraryDir,          // Test-APK
            inst.targetContext.applicationInfo.nativeLibraryDir,     // App-APK
        )
        for (d in dirs) {
            Log.i(tag, "nativeLibDir=$d inhalt=${File(d).list()?.sorted()}")
            val f = File(d, "libbiber.so")
            if (f.exists()) return f
        }
        return null
    }

    private fun compile(jobDir: File): CompileResult {
        val json = RustBridge.tectonicCompileToFile(
            BIBER_DOC, jobDir.absolutePath, 0L, "", true,
        )
        return CompileResult.fromJson(json)
    }

    private fun freshJob(name: String): File {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        return File(ctx.cacheDir, "biber-exec/$name").apply {
            deleteRecursively(); mkdirs()
        }
        .also { File(it, "refs.bib").writeText(REFS_BIB) }
    }

    private fun hasEnoent(r: CompileResult) =
        r.engineError.contains("os error 2") ||
            r.engineError.contains("No such file")

    @Test
    fun u1_execUndWiringNachweis() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext

        // (A) Kontroll-Lauf OHNE biber auf PATH → erwartet ENOENT (os error 2).
        val before = compile(freshJob("A-ohne-biber"))
        Log.i(tag, "OHNE biber: ok=${before.ok} ENOENT=${hasEnoent(before)} " +
            "engineError=${before.engineError.take(120)}")

        // (B) Fake-biber als `biber` auf PATH legen.
        val fake = findFakeBiber()
        if (fake == null) {
            Log.e(tag, "ABBRUCH: libbiber.so in keinem nativeLibraryDir gefunden " +
                "(Extraktion?). Sonde kann U1 nicht messen.")
            return
        }
        val binDir = File(ctx.filesDir, "bin").apply { mkdirs() }
        val biberLink = File(binDir, "biber")
        biberLink.delete()
        // Symlink `biber` → libbiber.so (im exec-erlaubten nativeLibraryDir).
        Os.symlink(fake.absolutePath, biberLink.absolutePath)
        val oldPath = Os.getenv("PATH") ?: "/system/bin"
        Os.setenv("PATH", "${binDir.absolutePath}:$oldPath", true)
        Log.i(tag, "biber-Link=${biberLink.absolutePath} -> ${fake.absolutePath}")
        Log.i(tag, "PATH=${Os.getenv("PATH")}")

        // (C) Lauf MIT biber auf PATH.
        val after = compile(freshJob("B-mit-biber"))
        val enoentAfter = hasEnoent(after)
        Log.i(tag, "MIT biber: ok=${after.ok} ENOENT=$enoentAfter " +
            "engineError=${after.engineError.take(200)}")

        val verdict = when {
            hasEnoent(before) && !enoentAfter && after.ok ->
                "GRÜN++ — ENOENT weg UND PDF erzeugt: voller biber-Loop läuft."
            hasEnoent(before) && !enoentAfter ->
                "GRÜN — ENOENT weg: Tectonic hat den (Fake-)biber ausgeführt. exec + " +
                    "Discovery + Rerun-Loop der App bewiesen. Neuer Fehler nur noch " +
                    "der erwartete bbl-Versions-Skew (3.3 vs biblatex 3.17)."
            !hasEnoent(before) ->
                "UNKLAR — Kontroll-Lauf zeigte gar kein ENOENT; Annahme prüfen."
            else ->
                "ROT — ENOENT bleibt trotz biber auf PATH: exec/Discovery greift nicht " +
                    "(noexec? PATH? Command-Resolution?)."
        }
        Log.i(tag, "U1 VERDIKT: $verdict")
    }
}

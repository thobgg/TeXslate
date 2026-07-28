package de.bgg_home.texdroid.compile

import android.content.Context
import android.system.Os
import android.util.Log
import de.bgg_home.texdroid.BuildConfig
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Richtet die cross-gebaute biber-Runtime (Perl 5.36.3 Bionic + Text::BibTeX,
 * XML::LibXML/XSLT u.a. + biber 2.17) zur Laufzeit ein, damit Tectonic sie beim
 * Kompilieren als externes `biber` aufrufen kann (tex→biber→tex).
 *
 * NUR in der **thesis**-Edition aktiv (`BuildConfig.HAS_BIBER`); in `core` gibt es
 * die Runtime nicht → [ensureReady] liefert `false`, und der Preflight erklärt dem
 * Nutzer den bibtex-Fallback.
 *
 * Android W^X-Rezept (auf S8 Ultra bewiesen, RealBiberProbe):
 *  - `.so` dürfen aus filesDir dlopen't werden → der ganze Perl-Baum wird aus dem
 *    Asset-Zip nach `filesDir/biber` entpackt.
 *  - `exec()` einer filesDir-Datei ist BLOCKIERT → das perl-Binary und ein winziger
 *    Launcher liegen als `lib*.so` im nativeLibraryDir (exec-erlaubt). `biber` auf
 *    dem PATH ist ein Symlink auf den Launcher; der exec't `perl <biber-skript>`.
 *  - Steuerung über Env: PATH (Launcher als `biber`), BIBER_PERL, BIBER_SCRIPT,
 *    PERL5LIB, LD_LIBRARY_PATH (für libbtparse.so). Kein LD_PRELOAD (libm-Fix drin).
 *
 * Die Runtime gibt es nur für arm64-v8a; auf anderen ABIs fehlen die jniLibs →
 * [ensureReady] liefert sauber `false`.
 */
object BiberRuntime {

    private const val TAG = "BiberRuntime"

    /** Bei jeder Runtime-Änderung erhöhen → erzwingt Neu-Entpacken. */
    private const val RUNTIME_VERSION = "biber-2.17_perl-5.36.3_1"

    private const val ASSET_ZIP = "biber-tree.zip"
    private const val PERL_LIB = "libperl_exe.so"
    private const val LAUNCHER_LIB = "libbiber_launcher.so"

    @Volatile private var envApplied = false

    /**
     * Stellt die Runtime bereit (einmalig entpacken) und setzt die Prozess-Env, die
     * Tectonic an den `biber`-Kindprozess vererbt. Idempotent und thread-safe genug
     * für den IO-Dispatcher (wird aus [LatexCompiler.compile] aufgerufen).
     *
     * @return `true`, wenn biber danach aufrufbar ist; `false` in `core`, auf
     *   fremden ABIs oder bei fehlenden Artefakten (Aufrufer nutzt dann den Preflight).
     */
    @Synchronized
    fun ensureReady(context: Context): Boolean {
        if (!BuildConfig.HAS_BIBER) return false

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val perlExe = File(nativeDir, PERL_LIB)
        val launcher = File(nativeDir, LAUNCHER_LIB)
        if (!perlExe.exists() || !launcher.exists()) {
            Log.w(TAG, "biber-Runtime-Binaries fehlen in $nativeDir " +
                "(ABI ohne biber? useLegacyPackaging?) – Preflight übernimmt.")
            return false
        }

        val root = File(context.filesDir, "biber")
        try {
            ensureExtracted(context, root)

            // `biber` auf PATH = Symlink → Launcher (nativeLibDir, exec-erlaubt).
            val launchDir = File(root, "launch").apply { mkdirs() }
            val biberOnPath = File(launchDir, "biber")
            if (!biberOnPath.exists()) {
                Os.symlink(launcher.absolutePath, biberOnPath.absolutePath)
            }

            applyEnv(root, launchDir, perlExe)
            return true
        } catch (t: Throwable) {
            Log.e(TAG, "biber-Setup fehlgeschlagen – Preflight übernimmt.", t)
            return false
        }
    }

    /** Zip nach [root] entpacken, sofern noch nicht (in aktueller Version) geschehen. */
    private fun ensureExtracted(context: Context, root: File) {
        val marker = File(root, ".runtime-version")
        if (marker.isFile && marker.readText() == RUNTIME_VERSION) return

        Log.i(TAG, "entpacke biber-Runtime ($RUNTIME_VERSION) …")
        root.deleteRecursively(); root.mkdirs()
        var files = 0
        context.assets.open(ASSET_ZIP).use { raw ->
            ZipInputStream(raw).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val out = File(root, e.name)
                    // Zip-Slip-Schutz
                    if (!out.canonicalPath.startsWith(root.canonicalPath + File.separator)) {
                        throw SecurityException("Zip-Eintrag außerhalb des Ziels: ${e.name}")
                    }
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { zin.copyTo(it) }
                        files++
                    }
                    zin.closeEntry(); e = zin.nextEntry
                }
            }
        }
        marker.writeText(RUNTIME_VERSION)
        Log.i(TAG, "biber-Runtime entpackt: $files Dateien nach ${root.absolutePath}")
    }

    /** Prozess-Env für den (von Tectonic geerbten) biber-Lauf setzen. Idempotent. */
    private fun applyEnv(root: File, launchDir: File, perlExe: File) {
        val p5 = File(root, "lib/perl5/5.36.3")
        val arch = File(p5, "aarch64-android")
        val libDir = File(root, "lib")

        // PATH nur einmal voranstellen (sonst wächst es pro Compile).
        val curPath = Os.getenv("PATH") ?: "/system/bin"
        if (!curPath.split(':').contains(launchDir.absolutePath)) {
            Os.setenv("PATH", "${launchDir.absolutePath}:$curPath", true)
        }
        Os.setenv("BIBER_PERL", perlExe.absolutePath, true)
        Os.setenv("BIBER_SCRIPT", File(root, "bin/biber").absolutePath, true)
        Os.setenv("PERL5LIB", "${p5.absolutePath}:${arch.absolutePath}", true)
        Os.setenv("LD_LIBRARY_PATH", libDir.absolutePath, true)
        if (!envApplied) {
            Log.i(TAG, "biber-Env gesetzt: PATH+launch, BIBER_PERL=${perlExe.absolutePath}")
            envApplied = true
        }
    }
}

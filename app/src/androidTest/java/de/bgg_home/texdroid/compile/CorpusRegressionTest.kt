package de.bgg_home.texdroid.compile

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Kompiliert eine Sammlung **echter** LaTeX-Dokumente über den Produktionspfad
 * [LatexCompiler] und schlägt fehl, sobald eines davon nicht mehr durchläuft.
 *
 * Hintergrund: Fast jeder Fehler, den TeXslate in freier Wildbahn produziert, kommt
 * von Dokumenten, die für pdfLaTeX auf einem PC geschrieben wurden — Latin-1,
 * `inputenc`, Treiberoptionen, Groß-/Kleinschreibung von Dateinamen, EPS-Bilder.
 * Solche Fälle findet man nicht durch Nachdenken, sondern nur, indem man echte
 * Dokumente durchlaufen lässt: aus 18 Stück (CTAN, GitHub, arXiv — Artikel, Paper,
 * Präsentation, Buch, Lebenslauf, Brief, Journal-Klassen von IEEE/Elsevier/APS,
 * AMS-Mathematik, biblatex+biber, Editionsphilologie) sind an einem Tag fünf
 * behobene App-Fehler entstanden, darunter zwei Abstürze.
 *
 * Ablage der Fälle (per adb push, ohne SAF-Rechte erreichbar):
 * ```
 * <externalFilesDir>/texcheck/<fall>/…      Projektdateien (auch Unterordner)
 * <externalFilesDir>/texcheck/<fall>/MAINFILE   enthält den Namen der Hauptdatei
 * ```
 * Jeder Fall wird zweimal kompiliert: erster Lauf kalt (Hilfsdateien fehlen),
 * zweiter warm — das zeigt, was das Behalten der Hilfsdateien bringt.
 */
@RunWith(AndroidJUnit4::class)
class CorpusRegressionTest {

    private val tag = "CORPUS"
    private val ctx = InstrumentationRegistry.getInstrumentation().targetContext
    private val jobDir = File(ctx.filesDir, "job")

    /** Entpackt [zip] nach [dest] (nur reguläre Einträge, keine Pfad-Ausbrüche). */
    private fun unzipInto(zip: File, dest: File) {
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { zin ->
            while (true) {
                val e = zin.nextEntry ?: break
                val target = File(dest, e.name).canonicalFile
                if (!target.path.startsWith(dest.canonicalFile.path)) continue
                if (e.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { zin.copyTo(it) }
                }
                zin.closeEntry()
            }
        }
    }

    /** Job-Ordner leeren, aber den Tectonic-Cache (Bundle/Format) stehen lassen. */
    private fun resetJobDir() {
        jobDir.listFiles()?.forEach { f ->
            if (f.name != "Tectonic") f.deleteRecursively()
        }
    }

    private fun copyInto(src: File, dest: File) {
        src.listFiles()?.forEach { f ->
            if (f.name == "MAINFILE") return@forEach
            if (f.isDirectory) {
                val sub = File(dest, f.name).apply { mkdirs() }
                copyInto(f, sub)
            } else {
                f.copyTo(File(dest, f.name), overwrite = true)
            }
        }
    }

    @Test
    fun alleDokumenteDerSammlungKompilieren() {
        // Von adb angelegte UNTERORDNER gehören dem Nutzer „shell" und sind für die App
        // nicht betretbar (canRead=false) – gepushte Dateien dagegen schon (Modus 666).
        // Darum kommt die Sammlung als corpus.zip und wird hier nach filesDir entpackt.
        val zip = File(ctx.getExternalFilesDir(null), "corpus.zip")
        // Die Sammlung wird nicht mitgeliefert (Lizenzen, ~13 MB) – siehe README.
        // Fehlt sie, überspringt der Test, statt rot zu werden.
        org.junit.Assume.assumeTrue(
            "corpus.zip fehlt – siehe docs/CORPUS.md (adb push nach ${zip.absolutePath})",
            zip.exists(),
        )
        val root = File(ctx.filesDir, "corpus").apply { deleteRecursively(); mkdirs() }
        unzipInto(zip, root)
        val cases = root.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        Log.i(tag, "Fälle gefunden: ${cases.size} in ${root.absolutePath}")
        assertTrue("Keine Testfälle unter ${root.absolutePath}", cases.isNotEmpty())

        val failed = mutableListOf<String>()
        cases.forEach { case ->
            val mainName = File(case, "MAINFILE").takeIf { it.exists() }?.readText()?.trim()
                ?: case.listFiles()?.firstOrNull { it.name.endsWith(".tex") }?.name
                ?: run {
                    Log.w(tag, "${case.name}: keine Hauptdatei gefunden")
                    failed += "${case.name} (keine Hauptdatei)"
                    return@forEach
                }
            val mainFile = File(case, mainName)
            if (!mainFile.exists()) {
                Log.w(tag, "${case.name}: $mainName fehlt")
                failed += "${case.name} ($mainName fehlt)"
                return@forEach
            }

            resetJobDir()
            copyInto(case, jobDir)
            // Wie die App: UTF-8 mit Latin-1-Rückfall (ältere Dokumente).
            val source = de.bgg_home.texdroid.storage.DocumentStore.decodeText(mainFile.readBytes())

            val times = mutableListOf<Long>()
            var last: CompileResult? = null
            repeat(2) {
                val t0 = System.currentTimeMillis()
                last = runBlocking { LatexCompiler.compile(ctx, source) }
                times += System.currentTimeMillis() - t0
            }
            val r = last!!
            val pdf = File(r.pdfPath)
            val size = if (pdf.exists()) pdf.length() else -1
            Log.i(
                tag,
                "${case.name}: ok=${r.ok} pdf=${size}B laeufe=${times.joinToString("/")}ms " +
                    "quelle=${source.length}B notes=${r.notes.size}",
            )
            r.notes.forEach { Log.i(tag, "   Hinweis: $it") }
            r.errors.take(3).forEach { Log.i(tag, "   Fehler Z${it.line}: ${it.message.take(220)}") }
            if (!r.ok || size <= 0) failed += case.name
        }

        Log.i(tag, "FERTIG. Fehlgeschlagen: ${if (failed.isEmpty()) "keine" else failed.joinToString(", ")}")
        assertTrue("Diese Fälle kompilierten nicht: $failed", failed.isEmpty())
    }
}

package de.bgg_home.texdroid.storage

import android.content.Context
import java.io.File

/**
 * Macht `\setmainfont{<Name>}` (fontspec/XeTeX) auf Android nutzbar.
 *
 * Hintergrund: XeTeX löst Schriften per NAME über fontconfig auf. In die native
 * Lib ist fontconfig zwar einkompiliert, findet auf Android aber nur
 * `/system/fonts` und hat keinen beschreibbaren Cache — TeX-Klassiker wie „Latin
 * Modern Roman" oder „TeX Gyre Termes" liegen dort nicht und scheitern deshalb
 * per Name (der fontspec-*Default* funktioniert, weil er die Fonts direkt aus dem
 * Tectonic-Bundle per Datei zieht — an fontconfig vorbei).
 *
 * Fix: ein kuratiertes Font-Set (OTFs unter `assets/fonts`) einmalig nach
 * `filesDir/fonts/` auspacken und fontconfig per eigener `fonts.conf` darauf,
 * auf einen frei wählbaren Nutzer-Ordner sowie auf `/system/fonts` zeigen lassen —
 * mit beschreibbarem Cache. Der Pfad der `fonts.conf` geht an die native Seite,
 * die ihn via `FONTCONFIG_FILE` an fontconfig durchreicht.
 */
object FontStore {

    /** Verzeichnis mit den mitgelieferten, ausgepackten Fonts. */
    fun bundledDir(context: Context): File =
        File(context.filesDir, "fonts").apply { mkdirs() }

    /**
     * Ordner, in den Nutzer eigene `.otf`/`.ttf` legen können; danach per Name
     * verwendbar. Liegt im app-eigenen externen Speicher (per Dateimanager
     * erreichbar), ohne SAF-Rechte. Kann null sein, wenn kein externer Speicher
     * verfügbar ist.
     */
    fun userDir(context: Context): File? =
        context.getExternalFilesDir("fonts")

    private fun cacheDir(context: Context): File =
        File(context.filesDir, "fontconfig-cache").apply { mkdirs() }

    private fun configFile(context: Context): File =
        File(context.filesDir, "fonts.conf")

    /**
     * Stellt sicher, dass Fonts ausgepackt und die `fonts.conf` geschrieben ist,
     * und gibt den absoluten Pfad der `fonts.conf` zurück (leerer String bei
     * Fehler — der Compile läuft dann ohne Namens-Fonts weiter statt zu brechen).
     *
     * Neu-Auspacken passiert nur, wenn sich die App-Version geändert hat
     * (Marker = `lastUpdateTime`), damit der Aufruf pro Compile praktisch gratis
     * ist. Läuft schnell; kann vom Aufruferthread erfolgen.
     */
    fun ensureReady(context: Context): String = runCatching {
        val bundled = bundledDir(context)
        val stamp = context.packageManager
            .getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        val marker = File(bundled, ".stamp")

        if (marker.takeIf { it.exists() }?.readText() != stamp) {
            extractFonts(context, bundled)
            writeConfig(context)
            marker.writeText(stamp)
        } else if (!configFile(context).exists()) {
            writeConfig(context)
        }

        // Font-Set festhalten, wie es beim ERSTEN Compile dieses Prozesses aussah –
        // siehe [fontSetChangedSinceStart]. Muss nach dem Auspacken passieren, sonst
        // meldet die frisch geschriebene mtime eine Änderung, die keine ist.
        if (fingerprintAtInit == null) fingerprintAtInit = fontsFingerprint(context)
        configFile(context).absolutePath
    }.getOrDefault("")

    /**
     * Font-Set beim ersten Compile im laufenden Prozess. Bewusst nur im Speicher:
     * er beschreibt den Zustand von fontconfig in DIESEM Prozess.
     */
    @Volatile
    private var fingerprintAtInit: String? = null

    /**
     * True, sobald seit dem ersten Compile dieses Prozesses eine Schrift dazukam,
     * ersetzt oder entfernt wurde.
     *
     * Hintergrund (auf dem Gerät gemessen): fontconfig baut sein Fontset **einmal
     * pro Prozess** auf. Was danach in den Font-Ordner kommt, bleibt bis zum
     * App-Neustart unsichtbar — `\setmainfont{<Name>}` scheitert dann mit „font
     * cannot be found". Der On-Disk-Cache ist unschuldig: ein frischer Prozess
     * findet die Schrift auch mit altem Cache und aktualisiert ihn selbst.
     * Deshalb hier nur erkennen und den Nutzer zum Neustart bitten.
     */
    fun fontSetChangedSinceStart(context: Context): Boolean {
        val atInit = fingerprintAtInit ?: return false
        return runCatching { atInit != fontsFingerprint(context) }.getOrDefault(false)
    }

    /** Nur für Tests: Prozess-Zustand zurücksetzen (siehe [fontSetChangedSinceStart]). */
    internal fun resetProcessStateForTest() {
        fingerprintAtInit = null
    }

    /**
     * Kompakter Fingerabdruck aller Font-Dateien in gebündeltem + Nutzer-Ordner
     * (Name/Größe/mtime, sortiert). Ändert er sich, wurde ein Font hinzugefügt,
     * ersetzt oder entfernt.
     */
    private fun fontsFingerprint(context: Context): String =
        listOfNotNull(bundledDir(context), userDir(context))
            .flatMap { dir -> dir.listFiles()?.asList() ?: emptyList() }
            .filter { it.isFile && (it.name.endsWith(".otf", true) || it.name.endsWith(".ttf", true)) }
            .map { "${it.name}:${it.length()}:${it.lastModified()}" }
            .sorted()
            .joinToString("|")

    private fun extractFonts(context: Context, destDir: File) {
        val assets = context.assets
        val names = assets.list("fonts")?.filter {
            it.endsWith(".otf", true) || it.endsWith(".ttf", true) || it.endsWith(".txt", true)
        } ?: emptyList()
        names.forEach { name ->
            assets.open("fonts/$name").use { input ->
                File(destDir, name).outputStream().use { input.copyTo(it) }
            }
        }
    }

    private fun writeConfig(context: Context) {
        val dirs = buildList {
            add("/system/fonts")               // Android-Systemfonts (Roboto, Noto …)
            add(bundledDir(context).absolutePath)
            userDir(context)?.let { add(it.apply { mkdirs() }.absolutePath) }
        }
        // WICHTIG: Die <?xml?>-Deklaration MUSS an Byte 0 stehen — führende
        // Leerzeichen lassen den fontconfig-Parser (expat) die Datei ablehnen,
        // fontconfig fällt dann still auf den Default (nur /system/fonts) zurück.
        // Darum bewusst per buildString ohne trimIndent zusammensetzen.
        val config = buildString {
            append("<?xml version=\"1.0\"?>\n")
            append("<fontconfig>\n")
            dirs.forEach { append("  <dir>${xmlEscape(it)}</dir>\n") }
            append("  <cachedir>${xmlEscape(cacheDir(context).absolutePath)}</cachedir>\n")
            append("</fontconfig>\n")
        }
        configFile(context).writeText(config)
    }

    private fun xmlEscape(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

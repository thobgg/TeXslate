package de.bgg_home.texdroid.compile

import org.json.JSONObject

/**
 * Ein einzelner geparster Fehler aus dem TeX-Log.
 *
 * @param line   Quelltext-Zeile (1-basiert), sofern das Log eine nennt – sonst null.
 * @param message kurze, für Menschen lesbare Fehlermeldung.
 */
data class CompileError(
    val line: Int?,
    val message: String,
)

/**
 * Ergebnis eines Compile-Laufs. Entsteht aus dem JSON, das die native Seite
 * ([de.bgg_home.texdroid.RustBridge.tectonicCompileToFile]) zurückgibt.
 *
 * @param ok           true = PDF wurde erzeugt.
 * @param pdfPath      absoluter Pfad zum erzeugten PDF (leer bei Fehler).
 * @param synctexPath  absoluter Pfad zur SyncTeX-Datei (`.synctex.gz`), falls vorhanden.
 * @param log          das vollständige TeX-Log (für Detailansicht).
 * @param engineError  Fehlermeldung der Engine (leer bei Erfolg).
 * @param errors       aus dem Log extrahierte Einzelfehler (Zeile + Meldung).
 */
data class CompileResult(
    val ok: Boolean,
    val pdfPath: String,
    val synctexPath: String,
    val log: String,
    val engineError: String,
    val errors: List<CompileError>,
) {
    /** Kurze Zusammenfassung für die Snackbar (UI-Sprache via Locale). */
    fun summary(): String {
        val de = java.util.Locale.getDefault().language == "de"
        return when {
            ok && errors.isEmpty() -> if (de) "Kompiliert ✓" else "Compiled ✓"
            ok -> if (de) {
                "Kompiliert ✓ (mit ${errors.size} Warnung/Hinweis)"
            } else {
                "Compiled ✓ (${errors.size} warning/note)"
            }
            errors.isNotEmpty() ->
                (if (de) "Fehler: " else "Error: ") + errors.first().message
            engineError.isNotEmpty() ->
                (if (de) "Fehler: " else "Error: ") +
                    engineError.lineSequence().firstOrNull().orEmpty()
            else -> if (de) "Compile fehlgeschlagen" else "Compile failed"
        }
    }

    companion object {
        /** Baut ein [CompileResult] aus dem JSON der nativen Brücke. */
        fun fromJson(json: String): CompileResult {
            val obj = JSONObject(json)
            val log = obj.optString("log", "")
            val engineError = obj.optString("error", "")
            // Fehler primär aus dem Log ziehen; wenn das leer ist, die Engine-Meldung nutzen.
            val parsed = LatexLog.parseErrors(log)
            val errors = when {
                parsed.isNotEmpty() -> parsed
                engineError.isNotEmpty() -> listOf(CompileError(null, engineError.trim()))
                else -> emptyList()
            }
            return CompileResult(
                ok = obj.optBoolean("ok", false),
                pdfPath = obj.optString("pdfPath", ""),
                synctexPath = obj.optString("synctexPath", ""),
                log = log,
                engineError = engineError,
                errors = errors,
            )
        }

        /** Ergebnis, wenn die native Lib (noch) nicht geladen/rebuildet ist. */
        fun nativeUnavailable(cause: Throwable): CompileResult {
            val de = java.util.Locale.getDefault().language == "de"
            return CompileResult(
                ok = false,
                pdfPath = "",
                synctexPath = "",
                log = cause.stackTraceToString(),
                engineError = if (de) {
                    "Native Compile-Funktion nicht verfügbar – libtexdroid_native.so muss neu " +
                        "gebaut werden (./build-native.sh). Details: ${cause.message}"
                } else {
                    "Native compile function unavailable – libtexdroid_native.so must be rebuilt " +
                        "(./build-native.sh). Details: ${cause.message}"
                },
                errors = listOf(
                    CompileError(
                        null,
                        if (de) "Native Lib nicht auf dem neuesten Stand (siehe ./build-native.sh)"
                        else "Native lib out of date (see ./build-native.sh)",
                    ),
                ),
            )
        }
    }
}

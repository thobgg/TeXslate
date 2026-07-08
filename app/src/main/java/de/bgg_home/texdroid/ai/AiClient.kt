package de.bgg_home.texdroid.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Ergebnis eines KI-Aufrufs: Antworttext oder eine menschenlesbare Fehlermeldung. */
sealed interface AiResult {
    data class Success(val text: String) : AiResult
    data class Failure(val message: String) : AiResult
}

/** Eine Nachricht im Gesprächsverlauf. [role] ist "user" oder "assistant". */
data class AiMessage(val role: String, val content: String)

/**
 * Provider-agnostischer KI-Aufruf (QW A2). Kapselt die drei REST-Chat-APIs
 * (Anthropic Messages · OpenAI Chat Completions · Gemini generateContent) hinter
 * einer einzigen [complete]-Funktion. Bewusst **ohne** Netzwerk-Dependency – nur
 * [HttpURLConnection] + `org.json` (Android-Bordmittel), F-Droid-freundlich.
 *
 * Es wird erst gesendet, nachdem der Nutzer im Vorschau-Dialog bestätigt hat.
 */
object AiClient {

    // UI-Sprache für Fehlermeldungen (reine Logik, kein Context) – wie AiPrompt.
    private val german: Boolean get() = Locale.getDefault().language == "de"

    suspend fun complete(
        provider: AiProvider,
        model: String,
        apiKey: String,
        systemPrompt: String,
        messages: List<AiMessage>,
    ): AiResult = withContext(Dispatchers.IO) {
        try {
            when (provider) {
                AiProvider.ANTHROPIC -> anthropic(model, apiKey, systemPrompt, messages)
                AiProvider.OPENAI -> openai(model, apiKey, systemPrompt, messages)
                AiProvider.GEMINI -> gemini(model, apiKey, systemPrompt, messages)
            }
        } catch (e: IOException) {
            AiResult.Failure(
                if (german) "Keine Verbindung – bitte Internet prüfen."
                else "No connection – please check your internet.",
            )
        } catch (e: Exception) {
            val what = e.message ?: e.javaClass.simpleName
            AiResult.Failure(if (german) "Unerwarteter Fehler: $what" else "Unexpected error: $what")
        }
    }

    // --- Provider-Adapter -----------------------------------------------------

    private fun anthropic(model: String, key: String, system: String, messages: List<AiMessage>): AiResult {
        val arr = JSONArray()
        messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 1500)
            .put("messages", arr)
        if (system.isNotBlank()) body.put("system", system)
        val res = post(
            "https://api.anthropic.com/v1/messages",
            mapOf(
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01",
                "content-type" to "application/json",
            ),
            body,
        )
        if (!res.ok) return httpError(res.code, res.body)
        val parts = JSONObject(res.body).getJSONArray("content")
        val text = (0 until parts.length()).joinToString("") { i ->
            val o = parts.getJSONObject(i)
            if (o.optString("type") == "text") o.optString("text") else ""
        }
        return AiResult.Success(text.trim())
    }

    private fun openai(model: String, key: String, system: String, messages: List<AiMessage>): AiResult {
        val arr = JSONArray()
        if (system.isNotBlank()) {
            arr.put(JSONObject().put("role", "system").put("content", system))
        }
        messages.forEach { arr.put(JSONObject().put("role", it.role).put("content", it.content)) }
        val body = JSONObject().put("model", model).put("messages", arr)
        val res = post(
            "https://api.openai.com/v1/chat/completions",
            mapOf("Authorization" to "Bearer $key", "content-type" to "application/json"),
            body,
        )
        if (!res.ok) return httpError(res.code, res.body)
        val text = JSONObject(res.body)
            .getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").getString("content")
        return AiResult.Success(text.trim())
    }

    private fun gemini(model: String, key: String, system: String, messages: List<AiMessage>): AiResult {
        val contents = JSONArray()
        messages.forEach {
            // Gemini nennt die Assistenten-Rolle "model".
            val role = if (it.role == "assistant") "model" else "user"
            contents.put(
                JSONObject()
                    .put("role", role)
                    .put("parts", JSONArray().put(JSONObject().put("text", it.content))),
            )
        }
        val body = JSONObject().put("contents", contents)
        if (system.isNotBlank()) {
            body.put(
                "systemInstruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))),
            )
        }
        // Key als Header (nicht in der URL) – landet so nicht in Logs/Proxies.
        val res = post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to key, "content-type" to "application/json"),
            body,
        )
        if (!res.ok) return httpError(res.code, res.body)
        val parts = JSONObject(res.body)
            .getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        val text = (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
        return AiResult.Success(text.trim())
    }

    // --- HTTP-Grundlage -------------------------------------------------------

    private class HttpResponse(val code: Int, val body: String) {
        val ok get() = code in 200..299
    }

    private fun post(urlStr: String, headers: Map<String, String>, body: JSONObject): HttpResponse {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 30_000
            readTimeout = 90_000
            doOutput = true
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            return HttpResponse(code, text)
        } finally {
            conn.disconnect()
        }
    }

    /** Übersetzt HTTP-Status + Fehler-JSON in eine kurze Meldung (UI-Sprache). */
    private fun httpError(code: Int, body: String): AiResult.Failure {
        val detail = runCatching {
            JSONObject(body).getJSONObject("error").getString("message")
        }.getOrNull()
        val base = if (german) {
            when (code) {
                400 -> "Anfrage abgelehnt (400) – evtl. falsche Modell-ID?"
                401, 403 -> "API-Key ungültig oder ohne Berechtigung."
                404 -> "Nicht gefunden (404) – evtl. falsche Modell-ID?"
                429 -> "Rate-Limit oder Kontingent erreicht – kurz warten und erneut versuchen."
                in 500..599 -> "Der Anbieter meldet einen Serverfehler ($code)."
                else -> "Anfrage fehlgeschlagen ($code)."
            }
        } else {
            when (code) {
                400 -> "Request rejected (400) – maybe a wrong model ID?"
                401, 403 -> "API key invalid or not authorized."
                404 -> "Not found (404) – maybe a wrong model ID?"
                429 -> "Rate limit or quota reached – wait a moment and try again."
                in 500..599 -> "The provider reports a server error ($code)."
                else -> "Request failed ($code)."
            }
        }
        return AiResult.Failure(if (detail.isNullOrBlank()) base else "$base\n\n$detail")
    }
}

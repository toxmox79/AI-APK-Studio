package de.aiapk.studio.ai

import de.aiapk.studio.data.ProviderEntity
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OpenAiCompatibleClient {
    data class Result(val ok: Boolean, val text: String, val latencyMs: Long)

    fun chat(provider: ProviderEntity, apiKey: String, messages: List<Pair<String, String>>): Result {
        val started = System.currentTimeMillis()
        return try {
            val endpoint = provider.baseUrl.trimEnd('/') + "/chat/completions"
            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 90_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                if (apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer $apiKey")
            }
            val jsonMessages = JSONArray().apply {
                messages.forEach { (role, content) ->
                    put(JSONObject().put("role", role).put("content", content))
                }
            }
            val body = JSONObject()
                .put("model", provider.model)
                .put("messages", jsonMessages)
                .put("temperature", 0.2)
                .put("max_tokens", 8192)
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) Result(false, "HTTP $code: ${raw.take(1000)}", System.currentTimeMillis() - started)
            else {
                val content = JSONObject(raw)
                    .getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").optString("content", "")
                Result(true, content.ifBlank { "Leere Antwort vom Modell." }, System.currentTimeMillis() - started)
            }
        } catch (t: Throwable) {
            Result(false, t.message ?: t.javaClass.simpleName, System.currentTimeMillis() - started)
        }
    }
}

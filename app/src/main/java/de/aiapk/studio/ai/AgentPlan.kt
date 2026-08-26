package de.aiapk.studio.ai

import de.aiapk.studio.build.ProjectFileChange
import org.json.JSONObject

data class AgentPlan(
    val summary: String,
    val files: List<ProjectFileChange>,
    val delete: List<String>,
    val build: Boolean
) {
    companion object {
        fun parse(raw: String): AgentPlan? = runCatching {
            val cleaned = raw.trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```").trim()
            val start = cleaned.indexOf('{')
            val end = cleaned.lastIndexOf('}')
            require(start >= 0 && end > start)
            val o = JSONObject(cleaned.substring(start, end + 1))
            val filesArr = o.optJSONArray("files")
            val files = buildList {
                if (filesArr != null) for (i in 0 until filesArr.length()) {
                    val f = filesArr.getJSONObject(i)
                    add(ProjectFileChange(f.getString("path"), f.getString("content")))
                }
            }
            val delArr = o.optJSONArray("delete")
            val deletes = buildList {
                if (delArr != null) for (i in 0 until delArr.length()) add(delArr.getString(i))
            }
            AgentPlan(o.optString("summary", "Änderungen vorbereitet."), files, deletes, o.optBoolean("build", true))
        }.getOrNull()
    }
}

object CodingAgentPrompt {
    fun system(projectType: String, packageName: String, fileList: List<String>): String = """
        Du bist der Coding-Agent von AI APK Studio. Du arbeitest an genau einem Android-Projekt.
        Projekttyp: $projectType
        Package: $packageName
        Vorhandene Dateien: ${fileList.joinToString(", ").take(12000)}

        Antworte AUSSCHLIESSLICH als valides JSON-Objekt ohne Markdown:
        {
          "summary":"kurze deutsche Zusammenfassung",
          "files":[{"path":"relativer/pfad","content":"vollständiger neuer Dateiinhalt"}],
          "delete":["relativer/pfad"],
          "build":true
        }

        Regeln:
        - Nur notwendige Dateien ausgeben, aber jede geänderte Datei vollständig.
        - Pfade sind immer relativ zum Projektroot, nie .., nie absolute Pfade.
        - Bestehende Features erhalten, sofern die Aufgabe nichts anderes verlangt.
        - Keine erfundenen Bibliotheken/APIs.
        - Quick App: bevorzugt app/src/main/assets/www/index.html, style.css, app.js ändern; native Bridge nur wenn nötig.
        - Native Android: Kotlin + Jetpack Compose verwenden.
        - Keine Erklärtexte außerhalb des JSON.
        - build=true, wenn anschließend kompiliert werden soll.
    """.trimIndent()
}

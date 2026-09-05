package de.aiapk.studio.build

import android.content.Context
import android.util.Base64

data class ProjectFileChange(val path: String, val content: String)

class TermuxProjectStore(private val context: Context) {
    private val bash = "/data/data/com.termux/files/usr/bin/bash"

    suspend fun ensureDirectory(projectPath: String): TermuxCommandResult {
        val rel = projectPath.removePrefix("~/").trimStart('/')
        require(!rel.split('/').contains(".."))
        val clean = rel.replace("\"", "")
        val script = "mkdir -p -- \"${'$'}HOME/$clean\" && cd -- \"${'$'}HOME/$clean\" && pwd"
        return TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), "~/")
    }

    suspend fun writeFile(projectPath: String, relativePath: String, content: String): TermuxCommandResult {
        val safe = validatePath(relativePath)
        val encoded = Base64.encodeToString(content.toByteArray(Charsets.UTF_8), Base64.NO_WRAP)
        val script = "mkdir -p \"\$(dirname ${shellQuote(safe)})\" && printf '%s' ${shellQuote(encoded)} | base64 -d > ${shellQuote(safe)}"
        return TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), projectPath, timeoutMs = 60_000)
    }

    suspend fun deleteFile(projectPath: String, relativePath: String): TermuxCommandResult {
        val safe = validatePath(relativePath)
        return TermuxBridge.runAwait(context, bash, arrayOf("-lc", "rm -f -- ${shellQuote(safe)}"), projectPath)
    }

    suspend fun listFiles(projectPath: String): List<String> {
        val script = "find . -type f -not -path './.gradle/*' -not -path '*/build/*' | sed 's#^./##' | sort | head -n 500"
        val r = TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), projectPath, timeoutMs = 60_000)
        return if (r.success) r.stdout.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList() else emptyList()
    }

    suspend fun readFile(projectPath: String, relativePath: String): String {
        val safe = validatePath(relativePath)
        val r = TermuxBridge.runAwait(context, bash, arrayOf("-lc", "cat -- ${shellQuote(safe)}"), projectPath, timeoutMs = 60_000)
        return if (r.success) r.stdout else ""
    }

    suspend fun gitInit(projectPath: String): TermuxCommandResult {
        val script = "git init -q && git config user.name 'AI APK Studio' && git config user.email 'local@aiapk.studio'"
        return TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), projectPath, timeoutMs = 60_000)
    }

    suspend fun gitCommit(projectPath: String, message: String): TermuxCommandResult {
        val script = "git add -A && (git diff --cached --quiet || git commit -q -m ${shellQuote(message.take(120))})"
        return TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), projectPath, timeoutMs = 60_000)
    }

    suspend fun gitHistory(projectPath: String): List<String> {
        val r = TermuxBridge.runAwait(context, bash, arrayOf("-lc", "git log --pretty=format:'%h|%s' -n 30 2>/dev/null || true"), projectPath)
        return r.stdout.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
    }

    suspend fun undoLastCommit(projectPath: String): TermuxCommandResult {
        val script = "if [ $(git rev-list --count HEAD 2>/dev/null || echo 0) -gt 1 ]; then git reset --hard -q HEAD~1; else echo 'Kein älterer Snapshot'; exit 2; fi"
        return TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), projectPath, timeoutMs = 60_000)
    }

    private fun validatePath(path: String): String {
        val p = path.replace('\\', '/').trimStart('/')
        require(p.isNotBlank() && !p.split('/').contains("..")) { "Ungültiger Projektpfad" }
        require(!p.startsWith("~") && !p.contains('\u0000')) { "Ungültiger Projektpfad" }
        return p
    }

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}

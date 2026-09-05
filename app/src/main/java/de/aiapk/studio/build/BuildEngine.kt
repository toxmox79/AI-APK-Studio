package de.aiapk.studio.build

import android.content.Context

data class BuildResult(
    val success: Boolean,
    val output: String,
    val apkPath: String? = null
)

interface BuildEngine {
    suspend fun build(projectPath: String): BuildResult
    suspend fun clean(projectPath: String): BuildResult
    suspend fun install(apkPath: String): BuildResult
}

class TermuxBuildEngine(private val context: Context) : BuildEngine {
    private val bash = "/data/data/com.termux/files/usr/bin/bash"

    override suspend fun build(projectPath: String): BuildResult {
        val script = """
            set -o pipefail
            if command -v studio >/dev/null 2>&1; then
              studio build .
            elif [ -x ./gradlew ]; then
              ./gradlew assembleDebug
            else
              gradle assembleDebug
            fi
            code=${'$'}?
            if [ ${'$'}code -eq 0 ]; then
              apk=${'$'}(find . -type f \( -path '*/build/outputs/apk/debug/*.apk' -o -path '*/build/outputs/apk/*/*.apk' \) | head -n 1)
              if [ -n "${'$'}apk" ]; then apk=${'$'}(realpath "${'$'}apk"); fi
              printf '\n__AIAPK_APK__=%s\n' "${'$'}apk"
            fi
            exit ${'$'}code
        """.trimIndent()
        val r = TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), projectPath, timeoutMs = 900_000)
        val combined = listOf(r.stdout, r.stderr, r.errorMessage).filter { it.isNotBlank() }.joinToString("\n")
        val apk = Regex("__AIAPK_APK__=(.+)").find(r.stdout)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
        return BuildResult(r.success, combined.ifBlank { if (r.success) "Build erfolgreich" else "Build fehlgeschlagen" }, apk)
    }

    override suspend fun clean(projectPath: String): BuildResult {
        val script = "if command -v studio >/dev/null 2>&1; then studio build . clean; elif [ -x ./gradlew ]; then ./gradlew clean; else gradle clean; fi"
        val r = TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), projectPath, timeoutMs = 300_000)
        return BuildResult(r.success, listOf(r.stdout, r.stderr, r.errorMessage).filter { it.isNotBlank() }.joinToString("\n"))
    }

    override suspend fun install(apkPath: String): BuildResult {
        if (apkPath.isBlank()) return BuildResult(false, "Kein APK-Pfad vorhanden")
        val script = "termux-open --view " + shellQuote(apkPath)
        val r = TermuxBridge.runAwait(context, bash, arrayOf("-lc", script), "~/", timeoutMs = 60_000)
        return BuildResult(r.success, listOf(r.stdout, r.stderr, r.errorMessage).filter { it.isNotBlank() }.joinToString("\n"))
    }

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
}

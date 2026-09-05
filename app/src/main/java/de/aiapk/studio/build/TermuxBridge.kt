package de.aiapk.studio.build

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

data class TermuxCommandResult(
    val requestId: Int,
    val stdout: String = "",
    val stderr: String = "",
    val exitCode: Int = -1,
    val errorCode: Int = 0,
    val errorMessage: String = ""
) {
    val success: Boolean get() = exitCode == 0 && errorCode <= 0
}

object TermuxCommandRegistry {
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<TermuxCommandResult>>()
    fun register(id: Int): CompletableDeferred<TermuxCommandResult> = CompletableDeferred<TermuxCommandResult>().also { pending[id] = it }
    fun complete(result: TermuxCommandResult) { pending.remove(result.requestId)?.complete(result) }
    fun cancel(id: Int) { pending.remove(id)?.cancel() }
}

object TermuxBridge {
    private val ids = AtomicInteger(1000)
    const val TERMUX_PACKAGE = "com.termux"
    const val ACTION_RUN = "com.termux.RUN_COMMAND"
    const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_STDIN = "com.termux.RUN_COMMAND_STDIN"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    suspend fun runAwait(
        context: Context,
        path: String,
        args: Array<String> = emptyArray(),
        workDir: String = "~/",
        stdin: String? = null,
        timeoutMs: Long = 180_000
    ): TermuxCommandResult {
        if (!isInstalled(context)) return TermuxCommandResult(-1, errorCode = 1, errorMessage = "Termux ist nicht installiert")
        val requestId = ids.incrementAndGet()
        val deferred = TermuxCommandRegistry.register(requestId)
        val resultIntent = Intent(context, TermuxResultService::class.java).putExtra("request_id", requestId)
        val flags = PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val pending = PendingIntent.getService(context, requestId, resultIntent, flags)
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
            action = ACTION_RUN
            putExtra(EXTRA_PATH, path)
            putExtra(EXTRA_ARGS, args)
            stdin?.let { putExtra(EXTRA_STDIN, it) }
            putExtra(EXTRA_WORKDIR, workDir)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_PENDING_INTENT, pending)
        }
        val started = runCatching { context.startService(intent) }.isSuccess
        if (!started) {
            TermuxCommandRegistry.cancel(requestId)
            return TermuxCommandResult(requestId, errorCode = 1, errorMessage = "RUN_COMMAND konnte nicht gestartet werden. Berechtigung/allow-external-apps prüfen.")
        }
        return withTimeoutOrNull(timeoutMs) { deferred.await() } ?: run {
            TermuxCommandRegistry.cancel(requestId)
            TermuxCommandResult(requestId, errorCode = 1, errorMessage = "Zeitüberschreitung bei Termux-Befehl")
        }
    }
}

package de.aiapk.studio.build

import android.app.Service
import android.content.Intent
import android.os.IBinder

class TermuxResultService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val requestId = intent.getIntExtra("request_id", -1)
            val bundle = intent.getBundleExtra("result")
            val result = TermuxCommandResult(
                requestId = requestId,
                stdout = bundle?.getString("stdout", "").orEmpty(),
                stderr = bundle?.getString("stderr", "").orEmpty(),
                exitCode = bundle?.getInt("exitCode", -1) ?: -1,
                errorCode = bundle?.getInt("err", 0) ?: 0,
                errorMessage = bundle?.getString("errmsg", "").orEmpty()
            )
            TermuxCommandRegistry.complete(result)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}

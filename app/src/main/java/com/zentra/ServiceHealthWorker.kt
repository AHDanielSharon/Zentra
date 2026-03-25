package com.zentra

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class ServiceHealthWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!isServiceRunning(applicationContext, ZentraVoiceService::class.java)) {
            ContextCompat.startForegroundService(
                applicationContext,
                Intent(applicationContext, ZentraVoiceService::class.java)
            )
        }
        return Result.success()
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        @Suppress("DEPRECATION")
        val services = manager.getRunningServices(Int.MAX_VALUE)
        return services.any { it.service.className == serviceClass.name }
    }
}

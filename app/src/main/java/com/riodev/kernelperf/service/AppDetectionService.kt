package com.riodev.kernelperf.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.riodev.kernelperf.data.repository.AppRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

class AppDetectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppRepository
    private var lastPackage = ""
    private var monitorJob: Job? = null

    companion object {
        var isRunning = false
        var currentForegroundApp: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(applicationContext)
        isRunning = true
        startMonitoring()
    }

    private fun startMonitoring() {
        monitorJob = scope.launch {
            while (isActive) {
                try {
                    val pkg = getForegroundApp()
                    if (pkg.isNotBlank() && pkg != lastPackage &&
                        pkg != "com.riodev.kernelperf" &&
                        pkg != "com.android.systemui") {
                        lastPackage = pkg
                        currentForegroundApp = pkg
                        repository.applyProfile(pkg)
                    }
                } catch (e: Exception) {
                    // ignore
                }
                delay(1000)
            }
        }
    }

    private fun getForegroundApp(): String {
        // Method 1: dumpsys activity via root (paling reliable)
        val result = Shell.cmd(
            "dumpsys activity activities 2>/dev/null | grep mResumedActivity | head -1 | grep -oP '(?<= )[a-z][a-z0-9.]+(?=/)'"
        ).exec()
        if (result.isSuccess && result.out.isNotEmpty()) {
            val pkg = result.out.first().trim()
            if (pkg.contains(".")) return pkg
        }

        // Method 2: dumpsys window (fallback)
        val result2 = Shell.cmd(
            "dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -1 | grep -oP '(?<= )[a-z][a-z0-9.]+(?=/)'"
        ).exec()
        if (result2.isSuccess && result2.out.isNotEmpty()) {
            val pkg = result2.out.first().trim()
            if (pkg.contains(".")) return pkg
        }

        return ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        monitorJob?.cancel()
        scope.cancel()
    }
}

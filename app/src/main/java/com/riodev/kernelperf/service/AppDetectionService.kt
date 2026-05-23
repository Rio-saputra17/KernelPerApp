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
                    if (pkg.isNotBlank() &&
                        pkg != lastPackage &&
                        pkg != "com.riodev.kernelperf" &&
                        pkg != "com.android.systemui" &&
                        pkg != "com.termux") {
                        lastPackage = pkg
                        currentForegroundApp = pkg
                        repository.applyProfile(pkg)
                    }
                } catch (e: Exception) { /* ignore */ }
                delay(1500)
            }
        }
    }

    private fun getForegroundApp(): String {
        // Method 1: dumpsys activity - parse di Kotlin, tidak pakai grep -P
        val result1 = Shell.cmd("dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' | head -3").exec()
        if (result1.isSuccess) {
            for (line in result1.out) {
                val pkg = extractPackageFromLine(line)
                if (pkg.isNotBlank()) return pkg
            }
        }

        // Method 2: dumpsys window
        val result2 = Shell.cmd("dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -2").exec()
        if (result2.isSuccess) {
            for (line in result2.out) {
                val pkg = extractPackageFromLine(line)
                if (pkg.isNotBlank()) return pkg
            }
        }

        // Method 3: top app via ps
        val result3 = Shell.cmd("dumpsys activity top 2>/dev/null | grep 'ACTIVITY' | head -1").exec()
        if (result3.isSuccess && result3.out.isNotEmpty()) {
            val pkg = extractPackageFromLine(result3.out.first())
            if (pkg.isNotBlank()) return pkg
        }

        return ""
    }

    /**
     * Extract package name dari baris dumpsys
     * Contoh input: "  mResumedActivity: ActivityRecord{abc com.mobile.legends/.MainActivty}"
     * Output: "com.mobile.legends"
     */
    private fun extractPackageFromLine(line: String): String {
        // Cari pattern "com.xxx.xxx/" atau "com.xxx.xxx}" 
        val regex = Regex("""([a-z][a-z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*){1,10})[/}]""")
        val matches = regex.findAll(line)
        for (match in matches) {
            val pkg = match.groupValues[1]
            // Filter system packages
            if (pkg.startsWith("com.android") ||
                pkg.startsWith("android") ||
                pkg.startsWith("com.miui") ||
                pkg.startsWith("com.xiaomi") ||
                pkg == "com.termux" ||
                pkg == "com.riodev.kernelperf") continue
            if (pkg.contains(".") && pkg.length > 5) return pkg
        }
        return ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        monitorJob?.cancel()
        scope.cancel()
    }
}

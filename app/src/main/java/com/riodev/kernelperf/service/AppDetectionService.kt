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
    private var lastWasProfileApp = false
    private var defaultGovernor = ""
    private var defaultMinFreq = ""
    private var defaultMaxFreq = ""
    private var defaultGpuGovernor = ""
    private var defaultScheduler = ""

    companion object {
        var isRunning = false
        var currentForegroundApp: String = ""
    }

    override fun onCreate() {
        super.onCreate()
        repository = AppRepository(applicationContext)
        isRunning = true
        scope.launch { saveDefaults(); startMonitoring() }
    }

    private suspend fun saveDefaults() {
        try {
            defaultGovernor = readNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor") ?: readNode("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: "schedutil"
            defaultMinFreq = readNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq") ?: ""
            defaultMaxFreq = readNode("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq") ?: ""
            defaultGpuGovernor = readNode("/sys/class/kgsl/kgsl-3d0/devfreq/governor") ?: "msm-adreno-tz"
            defaultScheduler = getCurrentScheduler()
        } catch (e: Exception) { }
    }

    private suspend fun startMonitoring() {
        while (scope.isActive) {
            try {
                val pkg = getForegroundApp()
                if (pkg.isNotBlank() && pkg != lastPackage) {
                    val hasProfile = repository.getProfile(pkg)?.isEnabled == true
                    if (hasProfile) {
                        lastPackage = pkg; currentForegroundApp = pkg; lastWasProfileApp = true
                        repository.applyProfile(pkg)
                    } else if (lastWasProfileApp) {
                        lastPackage = pkg; currentForegroundApp = pkg; lastWasProfileApp = false
                        restoreDefaults()
                    } else {
                        lastPackage = pkg; currentForegroundApp = pkg
                    }
                }
            } catch (e: Exception) { }
            delay(1500)
        }
    }

    private suspend fun restoreDefaults() {
        try {
            val n = getCpuCount()
            if (defaultGovernor.isNotBlank()) for (i in 0 until n) Shell.cmd("echo '$defaultGovernor' > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor").exec()
            if (defaultMinFreq.isNotBlank()) for (i in 0 until n) Shell.cmd("echo '$defaultMinFreq' > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_min_freq").exec()
            if (defaultMaxFreq.isNotBlank()) for (i in 0 until n) Shell.cmd("echo '$defaultMaxFreq' > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq").exec()
            if (defaultGpuGovernor.isNotBlank()) Shell.cmd("echo '$defaultGpuGovernor' > /sys/class/kgsl/kgsl-3d0/devfreq/governor").exec()
            if (defaultScheduler.isNotBlank()) for (b in listOf("sda","sdb","mmcblk0")) Shell.cmd("echo '$defaultScheduler' > /sys/block/$b/queue/scheduler").exec()
        } catch (e: Exception) { }
    }

    private fun getForegroundApp(): String {
        for (cmd in listOf("dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' | head -3","dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -2")) {
            val r = Shell.cmd(cmd).exec()
            if (r.isSuccess) for (line in r.out) { val p = extractPkg(line); if (p.isNotBlank()) return p }
        }
        return ""
    }

    private fun extractPkg(line: String): String {
        val rx = Regex("""([a-z][a-z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*){1,10})[/}]""")
        for (m in rx.findAll(line)) {
            val p = m.groupValues[1]
            if (p.startsWith("com.android")||p.startsWith("android")||p.startsWith("com.miui")||p.startsWith("com.xiaomi")||p=="com.termux"||p=="com.riodev.kernelperf") continue
            if (p.contains(".")&&p.length>5) return p
        }
        return ""
    }

    private fun readNode(path: String): String? {
        val r = Shell.cmd("cat $path 2>/dev/null").exec()
        return if (r.isSuccess && r.out.isNotEmpty()) r.out.first().trim() else null
    }

    private fun getCurrentScheduler(): String {
        for (b in listOf("sda","mmcblk0")) {
            val raw = readNode("/sys/block/$b/queue/scheduler") ?: continue
            val m = Regex("\\[([\\w-]+)\\]").find(raw)
            if (m != null) return m.groupValues[1]
        }
        return ""
    }

    private fun getCpuCount(): Int = Shell.cmd("ls /sys/devices/system/cpu/ | grep -c 'cpu[0-9]'").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 8

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); isRunning = false; scope.cancel() }
}

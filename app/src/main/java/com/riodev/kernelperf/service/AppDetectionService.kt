package com.riodev.kernelperf.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.riodev.kernelperf.data.repository.AppRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

class AppDetectionService : Service() {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: AppRepository
    private var lastPkg = ""
    private var lastHadProfile = false

    companion object {
        var isRunning = false
        var currentForegroundApp = ""
        var defaultGovernor = ""
        var defaultMinFreq = ""
        var defaultMaxFreq = ""
        var defaultGpuGovernor = ""
        var defaultScheduler = ""

        fun updateDefaultProfile(gov: String, minFreq: Int, maxFreq: Int, gpuGov: String, io: String) {
            if (gov.isNotBlank()) defaultGovernor = gov
            defaultMinFreq = if (minFreq > 0) minFreq.toString() else ""
            defaultMaxFreq = if (maxFreq > 0) maxFreq.toString() else ""
            defaultGpuGovernor = if (gpuGov != "default") gpuGov else ""
            defaultScheduler = if (io != "default") io else ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(applicationContext)
        isRunning = true
        scope.launch { captureDefaults(); monitor() }
    }

    private suspend fun captureDefaults() {
        fun node(p: String): String? {
            val r = Shell.cmd("cat $p 2>/dev/null").exec()
            return if (r.isSuccess && r.out.isNotEmpty()) r.out.first().trim() else null
        }
        if (defaultGovernor.isBlank())
            defaultGovernor = node("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor")
                ?: node("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: "schedutil"
        if (defaultMinFreq.isBlank())
            defaultMinFreq = node("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq")
                ?: node("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq") ?: ""
        if (defaultMaxFreq.isBlank())
            defaultMaxFreq = node("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq")
                ?: node("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq") ?: ""
        if (defaultGpuGovernor.isBlank())
            defaultGpuGovernor = node("/sys/class/kgsl/kgsl-3d0/devfreq/governor") ?: ""
        if (defaultScheduler.isBlank()) {
            for (b in listOf("sda", "mmcblk0")) {
                val raw = node("/sys/block/$b/queue/scheduler") ?: continue
                val m = Regex("\\[([\\w-]+)\\]").find(raw)
                if (m != null) { defaultScheduler = m.groupValues[1]; break }
            }
        }
    }

    private suspend fun monitor() {
        while (scope.isActive) {
            try {
                val pkg = foreground()
                if (pkg.isNotBlank() && pkg != lastPkg) {
                    val hasProfile = try { repo.getProfile(pkg)?.isEnabled == true } catch (e: Exception) { false }
                    when {
                        hasProfile -> { lastHadProfile = true; lastPkg = pkg; currentForegroundApp = pkg; try { repo.applyProfile(pkg) } catch (e: Exception) { } }
                        lastHadProfile -> { lastHadProfile = false; lastPkg = pkg; currentForegroundApp = pkg; restore() }
                        else -> { lastPkg = pkg; currentForegroundApp = pkg }
                    }
                }
            } catch (e: Exception) { }
            delay(1500)
        }
    }

    private suspend fun restore() = withContext(Dispatchers.IO) {
        try {
            val n = Shell.cmd("ls /sys/devices/system/cpu/ | grep -c 'cpu[0-9]'").exec().out.firstOrNull()?.trim()?.toIntOrNull() ?: 8
            if (defaultGovernor.isNotBlank()) for (i in 0 until n) Shell.cmd("echo '$defaultGovernor' > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor").exec()
            if (defaultMinFreq.isNotBlank()) for (i in 0 until n) Shell.cmd("echo '$defaultMinFreq' > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_min_freq").exec()
            if (defaultMaxFreq.isNotBlank()) for (i in 0 until n) Shell.cmd("echo '$defaultMaxFreq' > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq").exec()
            if (defaultGpuGovernor.isNotBlank()) Shell.cmd("echo '$defaultGpuGovernor' > /sys/class/kgsl/kgsl-3d0/devfreq/governor").exec()
            if (defaultScheduler.isNotBlank()) for (b in listOf("sda", "sdb", "mmcblk0")) Shell.cmd("echo '$defaultScheduler' > /sys/block/$b/queue/scheduler").exec()
        } catch (e: Exception) { }
    }

    private fun foreground(): String {
        val cmds = listOf(
            "dumpsys activity activities 2>/dev/null | grep -E 'mResumedActivity|topResumedActivity' | head -3",
            "dumpsys window windows 2>/dev/null | grep -E 'mCurrentFocus|mFocusedApp' | head -2"
        )
        for (cmd in cmds) {
            val r = Shell.cmd(cmd).exec()
            if (r.isSuccess) for (line in r.out) { val p = extractPkg(line); if (p.isNotBlank()) return p }
        }
        return ""
    }

    private fun extractPkg(line: String): String {
        val rx = Regex("""([a-z][a-z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*){1,10})[/}]""")
        for (m in rx.findAll(line)) {
            val p = m.groupValues[1]
            if (p.startsWith("com.android") || p.startsWith("android") || p.startsWith("com.miui") ||
                p.startsWith("com.xiaomi") || p.startsWith("com.mi.") || p == "com.termux" ||
                p == "com.riodev.kernelperf") continue
            if (p.contains(".") && p.length > 5) return p
        }
        return ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); isRunning = false; scope.cancel() }
}

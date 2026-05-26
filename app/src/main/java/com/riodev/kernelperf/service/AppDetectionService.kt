package com.riodev.kernelperf.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.riodev.kernelperf.data.repository.AppRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*

class AppDetectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: AppRepository
    private lateinit var usageStats: UsageStatsManager
    private var lastPkg = ""
    private var isGameActive = false
    private var restoreJob: Job? = null

    companion object {
        var isRunning = false
        var currentForegroundApp = ""
        var idleGovernor = "schedutil"
        var idleMinFreq = ""
        var idleMaxFreq = ""
        var idleGpuGovernor = ""
        var idleScheduler = ""

        fun updateDefaultProfile(gov: String, minFreq: Int, maxFreq: Int, gpuGov: String, io: String) {
            if (gov.isNotBlank()) idleGovernor = gov
            idleMinFreq = if (minFreq > 0) minFreq.toString() else ""
            idleMaxFreq = if (maxFreq > 0) maxFreq.toString() else ""
            idleGpuGovernor = if (gpuGov != "default") gpuGov else ""
            idleScheduler = if (io != "default") io else ""
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(applicationContext)
        usageStats = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        isRunning = true
        scope.launch {
            delay(2000)
            applyIdle()
            monitor()
        }
    }

    // ── Monitor via UsageEvents ───────────────────────────────────
    // Baca event ACTIVITY_RESUMED dari sistem — event-driven, bukan polling dumpsys
    private suspend fun monitor() {
        while (scope.isActive) {
            try {
                val pkg = getForegroundViaUsageStats()
                if (pkg.isNotBlank() && pkg != lastPkg) {
                    lastPkg = pkg
                    currentForegroundApp = pkg

                    val hasProfile = try {
                        repo.getProfile(pkg)?.isEnabled == true
                    } catch (e: Exception) { false }

                    when {
                        hasProfile -> {
                            // Game dibuka → cancel restore timer, apply game
                            restoreJob?.cancel()
                            restoreJob = null
                            isGameActive = true
                            try { repo.applyProfile(pkg) } catch (e: Exception) { }
                        }
                        isGameActive -> {
                            // Keluar dari game → tunggu 15 detik lalu restore idle
                            isGameActive = false
                            restoreJob?.cancel()
                            restoreJob = scope.launch {
                                delay(15_000)
                                applyIdle()
                            }
                        }
                    }
                }
            } catch (e: Exception) { }

            // Polling UsageStats tiap 2 detik — jauh lebih ringan dari dumpsys
            // UsageStats tidak spawn proses baru, hanya baca dari cache sistem
            delay(2000)
        }
    }

    // ── Baca foreground app via UsageStatsManager ─────────────────
    // Ini cara Android resmi — tidak butuh root, tidak spawn shell
    private fun getForegroundViaUsageStats(): String {
        try {
            val now = System.currentTimeMillis()
            // Ambil events 3 detik terakhir saja
            val events = usageStats.queryEvents(now - 3000, now)
            val event = UsageEvents.Event()
            var lastResumed = ""
            var lastTime = 0L

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                // ACTIVITY_RESUMED = app masuk foreground
                if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                    if (event.timeStamp > lastTime) {
                        lastTime = event.timeStamp
                        lastResumed = event.packageName ?: ""
                    }
                }
            }

            if (lastResumed.isValidPkg()) return lastResumed
        } catch (e: Exception) { }

        // Fallback: pakai UsageStats biasa kalau queryEvents tidak ada event baru
        try {
            val now = System.currentTimeMillis()
            val stats = usageStats.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                now - 10_000,
                now
            )
            val recent = stats?.maxByOrNull { it.lastTimeUsed }
            val pkg = recent?.packageName ?: ""
            if (pkg.isValidPkg()) return pkg
        } catch (e: Exception) { }

        return ""
    }

    private fun String.isValidPkg(): Boolean {
        if (length < 5 || !contains(".")) return false
        return !startsWith("com.android") && !startsWith("android") &&
               !startsWith("com.miui") && !startsWith("com.xiaomi") &&
               !startsWith("com.mi.") && this != "com.termux" &&
               this != "com.riodev.kernelperf"
    }

    // ── Apply Idle profile ────────────────────────────────────────
    private fun applyIdle() {
        try {
            w("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", idleGovernor)
            Thread.sleep(30)
            w("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor", idleGovernor)
            Thread.sleep(30)
            if (idleMinFreq.isNotBlank()) {
                w("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq", idleMinFreq)
                w("/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq", idleMinFreq)
            }
            if (idleMaxFreq.isNotBlank()) {
                w("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq", idleMaxFreq)
                w("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq", idleMaxFreq)
            }
            if (idleGpuGovernor.isNotBlank())
                w("/sys/class/kgsl/kgsl-3d0/devfreq/governor", idleGpuGovernor)
            if (idleScheduler.isNotBlank())
                for (b in listOf("sda", "mmcblk0"))
                    w("/sys/block/$b/queue/scheduler", idleScheduler)
        } catch (e: Exception) { }
    }

    private fun w(path: String, value: String) {
        try { Shell.cmd("echo '$value' > $path 2>/dev/null").exec() } catch (e: Exception) { }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        restoreJob?.cancel()
        scope.cancel()
    }
}

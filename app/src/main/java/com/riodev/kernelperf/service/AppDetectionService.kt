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
    private var isGameActive = false
    private var restoreJob: Job? = null

    companion object {
        var isRunning = false
        var currentForegroundApp = ""

        // Idle profile (dari menu Profil Default)
        var idleGovernor = "schedutil"
        var idleMinFreq = ""
        var idleMaxFreq = ""
        var idleGpuGovernor = ""
        var idleScheduler = ""

        fun updateIdleProfile(gov: String, minFreq: Int, maxFreq: Int, gpuGov: String, io: String) {
            if (gov.isNotBlank()) idleGovernor = gov
            idleMinFreq = if (minFreq > 0) minFreq.toString() else ""
            idleMaxFreq = if (maxFreq > 0) maxFreq.toString() else ""
            idleGpuGovernor = if (gpuGov != "default") gpuGov else ""
            idleScheduler = if (io != "default") io else ""
        }

        // Alias untuk kompatibilitas dengan ViewModel lama
        fun updateDefaultProfile(gov: String, minFreq: Int, maxFreq: Int, gpuGov: String, io: String) {
            updateIdleProfile(gov, minFreq, maxFreq, gpuGov, io)
        }
    }

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(applicationContext)
        isRunning = true
        scope.launch {
            // Terapkan idle saat service pertama start
            applyIdle()
            monitor()
        }
    }

    // ── PERINTAH 1: Apply Idle ───────────────────────────────────
    // Dijalankan saat: start, tidak ada game aktif, atau 15 detik setelah game ditutup
    private fun applyIdle() {
        try {
            w("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", idleGovernor)
            w("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor", idleGovernor)
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
                for (b in listOf("sda", "sdb", "mmcblk0"))
                    w("/sys/block/$b/queue/scheduler", idleScheduler)
        } catch (e: Exception) { }
    }

    // ── PERINTAH 2: Apply Game Profile ──────────────────────────
    // Dijalankan saat: game terdeteksi di foreground
    private suspend fun applyGame(pkg: String) {
        try { repo.applyProfile(pkg) } catch (e: Exception) { }
    }

    // ── Monitor loop ─────────────────────────────────────────────
    private suspend fun monitor() {
        while (scope.isActive) {
            try {
                val pkg = getForeground()

                if (pkg != lastPkg) {
                    lastPkg = pkg
                    currentForegroundApp = pkg

                    val hasProfile = if (pkg.isNotBlank()) {
                        try { repo.getProfile(pkg)?.isEnabled == true } catch (e: Exception) { false }
                    } else false

                    when {
                        hasProfile -> {
                            // Game terdeteksi → batalkan restore timer, apply game
                            restoreJob?.cancel()
                            restoreJob = null
                            isGameActive = true
                            applyGame(pkg)
                        }
                        isGameActive -> {
                            // Game baru saja ditutup → mulai countdown 15 detik
                            isGameActive = false
                            restoreJob?.cancel()
                            restoreJob = scope.launch {
                                delay(15_000) // 15 detik
                                applyIdle()
                            }
                        }
                        // App biasa, tidak ada game sebelumnya → tidak perlu apa-apa
                    }
                }
            } catch (e: Exception) { }
            delay(1500)
        }
    }

    // ── Deteksi foreground app ────────────────────────────────────
    private fun getForeground(): String {
        val cmds = listOf(
            "dumpsys activity 2>/dev/null | grep -m1 'mCurrentFocus'",
            "dumpsys activity activities 2>/dev/null | grep -m1 'mResumedActivity'"
        )
        for (cmd in cmds) {
            val r = Shell.cmd(cmd).exec()
            if (r.isSuccess && r.out.isNotEmpty()) {
                val p = extractPkg(r.out.first())
                if (p.isNotBlank()) return p
            }
        }
        return ""
    }

    private fun extractPkg(line: String): String {
        val rx = Regex("""([a-zA-Z][a-zA-Z0-9_]*(?:\.[a-zA-Z][a-zA-Z0-9_]*){1,10})[/}]""")
        for (m in rx.findAll(line)) {
            val p = m.groupValues[1]
            if (p.startsWith("com.android") || p.startsWith("android") ||
                p.startsWith("com.miui") || p.startsWith("com.xiaomi") ||
                p.startsWith("com.mi.") || p == "com.termux" ||
                p == "com.riodev.kernelperf") continue
            if (p.contains(".") && p.length > 5) return p
        }
        return ""
    }

    private fun w(path: String, value: String) {
        Shell.cmd("echo '$value' > $path 2>/dev/null").exec()
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

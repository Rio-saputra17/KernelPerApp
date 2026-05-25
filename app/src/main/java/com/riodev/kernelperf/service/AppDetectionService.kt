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
    private var mode = "none" // "none" | "game" | "idle"

    // Default profile dari ViewModel (bisa diupdate)
    companion object {
        var isRunning = false
        var currentForegroundApp = ""
        var defaultGovernor = ""
        var defaultMinFreq = ""
        var defaultMaxFreq = ""
        var defaultGpuGovernor = ""
        var defaultScheduler = ""

        // File snapshot — sama persis dengan cara module GameGovernor
        private const val SNAPSHOT = "/data/local/tmp/kernelperf_snapshot.conf"

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
        scope.launch {
            // Snapshot state sistem saat pertama start (sebelum profil apapun diterapkan)
            snapshot()
            monitor()
        }
    }

    // ── Snapshot: simpan state kernel saat ini ke file ──────────
    private fun snapshot() {
        try {
            val lines = StringBuilder()
            // Policy 0 (Little cluster)
            lines.append("S_CPU0_GOV=${node("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor")}\n")
            lines.append("S_CPU0_MIN=${node("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq")}\n")
            lines.append("S_CPU0_MAX=${node("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq")}\n")
            // Policy 4 (Big cluster)
            lines.append("S_CPU4_GOV=${node("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor") ?: node("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor")}\n")
            lines.append("S_CPU4_MIN=${node("/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq") ?: node("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq")}\n")
            lines.append("S_CPU4_MAX=${node("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq") ?: node("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq")}\n")
            // GPU
            lines.append("S_GPU_GOV=${node("/sys/class/kgsl/kgsl-3d0/devfreq/governor")}\n")
            lines.append("S_GPU_MIN=${node("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq")}\n")
            lines.append("S_GPU_MAX=${node("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq")}\n")
            // IO
            lines.append("S_IO=${getScheduler()}\n")

            Shell.cmd("cat > $SNAPSHOT << 'SNAP'\n${lines}SNAP").exec()
        } catch (e: Exception) { }
    }

    // ── Restore: baca snapshot file dan terapkan ─────────────────
    private fun restore() {
        try {
            val r = Shell.cmd("cat $SNAPSHOT 2>/dev/null").exec()
            if (!r.isSuccess || r.out.isEmpty()) {
                // Fallback ke default profile dari user
                applyDefaultProfile()
                return
            }

            // Parse snapshot
            val map = mutableMapOf<String, String>()
            r.out.forEach { line ->
                val parts = line.split("=", limit = 2)
                if (parts.size == 2) map[parts[0].trim()] = parts[1].trim()
            }

            // Apply CPU policy 0
            map["S_CPU0_GOV"]?.let { w("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", it) }
            map["S_CPU0_MIN"]?.let { w("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq", it) }
            map["S_CPU0_MAX"]?.let { w("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq", it) }
            // Apply CPU policy 4
            map["S_CPU4_GOV"]?.let { w("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor", it) }
            map["S_CPU4_MIN"]?.let { w("/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq", it) }
            map["S_CPU4_MAX"]?.let { w("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq", it) }
            // Apply GPU
            map["S_GPU_GOV"]?.let { w("/sys/class/kgsl/kgsl-3d0/devfreq/governor", it) }
            map["S_GPU_MIN"]?.let { w("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", it) }
            map["S_GPU_MAX"]?.let { w("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", it) }
            // Apply IO
            map["S_IO"]?.let { sched ->
                for (b in listOf("sda", "sdb", "mmcblk0")) w("/sys/block/$b/queue/scheduler", sched)
            }

            // Setelah restore, apply default profile user jika ada
            if (defaultGovernor.isNotBlank()) applyDefaultProfile()

            // Hapus snapshot setelah restore
            Shell.cmd("rm -f $SNAPSHOT").exec()
        } catch (e: Exception) {
            applyDefaultProfile()
        }
    }

    private fun applyDefaultProfile() {
        try {
            if (defaultGovernor.isNotBlank()) {
                w("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor", defaultGovernor)
                w("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor", defaultGovernor)
            }
            if (defaultMinFreq.isNotBlank()) {
                w("/sys/devices/system/cpu/cpufreq/policy0/scaling_min_freq", defaultMinFreq)
                w("/sys/devices/system/cpu/cpufreq/policy4/scaling_min_freq", defaultMinFreq)
            }
            if (defaultMaxFreq.isNotBlank()) {
                w("/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq", defaultMaxFreq)
                w("/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq", defaultMaxFreq)
            }
            if (defaultGpuGovernor.isNotBlank()) w("/sys/class/kgsl/kgsl-3d0/devfreq/governor", defaultGpuGovernor)
            if (defaultScheduler.isNotBlank()) for (b in listOf("sda", "sdb", "mmcblk0")) w("/sys/block/$b/queue/scheduler", defaultScheduler)
        } catch (e: Exception) { }
    }

    // ── Monitor loop ─────────────────────────────────────────────
    private suspend fun monitor() {
        while (scope.isActive) {
            try {
                val pkg = getForeground()
                val hasProfile = if (pkg.isNotBlank()) {
                    try { repo.getProfile(pkg)?.isEnabled == true } catch (e: Exception) { false }
                } else false

                when {
                    pkg.isNotBlank() && pkg != lastPkg -> {
                        currentForegroundApp = pkg
                        lastPkg = pkg
                        when {
                            hasProfile && mode != "game" -> {
                                // Masuk game — snapshot dulu, lalu apply
                                if (mode == "none") snapshot()
                                mode = "game"
                                try { repo.applyProfile(pkg) } catch (e: Exception) { }
                            }
                            !hasProfile && mode == "game" -> {
                                // Keluar game — restore
                                mode = "idle"
                                restore()
                            }
                            mode == "none" -> {
                                // Pertama kali — apply idle/default
                                mode = "idle"
                                applyDefaultProfile()
                            }
                        }
                    }
                    pkg.isBlank() && mode == "game" -> {
                        // App hilang dari foreground (di-close)
                        mode = "idle"
                        lastPkg = ""
                        currentForegroundApp = ""
                        restore()
                    }
                }
            } catch (e: Exception) { }
            delay(1500)
        }
    }

    // ── Deteksi foreground — pakai mCurrentFocus (lebih reliable) ─
    private fun getForeground(): String {
        // Method 1: mCurrentFocus (sama dengan module GameGovernor)
        val r1 = Shell.cmd("dumpsys activity 2>/dev/null | grep -m1 'mCurrentFocus'").exec()
        if (r1.isSuccess && r1.out.isNotEmpty()) {
            val p = extractPkg(r1.out.first())
            if (p.isNotBlank()) return p
        }
        // Method 2: mResumedActivity fallback
        val r2 = Shell.cmd("dumpsys activity activities 2>/dev/null | grep -m1 'mResumedActivity'").exec()
        if (r2.isSuccess && r2.out.isNotEmpty()) {
            val p = extractPkg(r2.out.first())
            if (p.isNotBlank()) return p
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

    private fun node(path: String): String? {
        val r = Shell.cmd("cat $path 2>/dev/null").exec()
        return if (r.isSuccess && r.out.isNotEmpty()) r.out.first().trim() else null
    }

    private fun w(path: String, value: String) {
        Shell.cmd("echo '$value' > $path 2>/dev/null").exec()
    }

    private fun getScheduler(): String {
        for (b in listOf("sda", "mmcblk0")) {
            val raw = node("/sys/block/$b/queue/scheduler") ?: continue
            val m = Regex("\\[([\\w-]+)\\]").find(raw)
            if (m != null) return m.groupValues[1]
        }
        return ""
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { super.onDestroy(); isRunning = false; scope.cancel() }
}

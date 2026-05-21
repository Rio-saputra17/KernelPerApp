package com.riodev.kernelperf.root

import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootUtils {

    // ─── Init Shell ───────────────────────────────────────────────
    fun initShell() {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }

    val isRooted: Boolean
        get() = Shell.getShell().isRoot

    // ─── Generic Read/Write ───────────────────────────────────────
    suspend fun readFile(path: String): String? = withContext(Dispatchers.IO) {
        val result = Shell.cmd("cat $path").exec()
        if (result.isSuccess) result.out.firstOrNull()?.trim() else null
    }

    suspend fun writeFile(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd("echo '$value' > $path").exec().isSuccess
    }

    suspend fun runCmd(cmd: String): Boolean = withContext(Dispatchers.IO) {
        Shell.cmd(cmd).exec().isSuccess
    }

    suspend fun runCmdOutput(cmd: String): List<String> = withContext(Dispatchers.IO) {
        Shell.cmd(cmd).exec().out
    }

    // ─── CPU Governor ─────────────────────────────────────────────
    suspend fun getAvailableGovernors(): List<String> {
        val raw = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
        return raw?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun getCurrentGovernor(): String {
        return readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor") ?: "unknown"
    }

    suspend fun setGovernor(governor: String): Boolean {
        val cpuCount = getCpuCount()
        var success = true
        for (i in 0 until cpuCount) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor"
            if (!writeFile(path, governor)) success = false
        }
        return success
    }

    // ─── CPU Frequency ────────────────────────────────────────────
    suspend fun getAvailableFrequencies(): List<Int> {
        val raw = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies")
        return raw?.split(" ")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { it.trim().toIntOrNull() }
            ?.sorted()
            ?: emptyList()
    }

    suspend fun getCurrentMinFreq(): String {
        val khz = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq")?.toLongOrNull()
        return if (khz != null) formatFreq(khz) else "-"
    }

    suspend fun getCurrentMaxFreq(): String {
        val khz = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq")?.toLongOrNull()
        return if (khz != null) formatFreq(khz) else "-"
    }

    suspend fun getCurrentFreq(): String {
        val khz = readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq")?.toLongOrNull()
        return if (khz != null) formatFreq(khz) else "-"
    }

    suspend fun setMinFreq(freqKhz: Int): Boolean {
        val cpuCount = getCpuCount()
        var success = true
        for (i in 0 until cpuCount) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_min_freq"
            if (!writeFile(path, freqKhz.toString())) success = false
        }
        return success
    }

    suspend fun setMaxFreq(freqKhz: Int): Boolean {
        val cpuCount = getCpuCount()
        var success = true
        for (i in 0 until cpuCount) {
            val path = "/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq"
            if (!writeFile(path, freqKhz.toString())) success = false
        }
        return success
    }

    // ─── GPU ──────────────────────────────────────────────────────
    private val gpuGovernorPaths = listOf(
        "/sys/class/kgsl/kgsl-3d0/devfreq/governor",         // Qualcomm Adreno
        "/sys/class/devfreq/gpufreq/governor",                 // MediaTek
        "/sys/kernel/gpu/gpu_governor"
    )

    private val gpuFreqPaths = mapOf(
        "min" to listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq",
            "/sys/class/devfreq/gpufreq/min_freq"
        ),
        "max" to listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
            "/sys/class/devfreq/gpufreq/max_freq"
        )
    )

    suspend fun getGpuGovernor(): String {
        for (path in gpuGovernorPaths) {
            val v = readFile(path)
            if (v != null) return v
        }
        return "unknown"
    }

    suspend fun setGpuGovernor(governor: String): Boolean {
        for (path in gpuGovernorPaths) {
            if (writeFile(path, governor)) return true
        }
        return false
    }

    suspend fun getAvailableGpuGovernors(): List<String> {
        val paths = listOf(
            "/sys/class/kgsl/kgsl-3d0/devfreq/available_governors",
            "/sys/class/devfreq/gpufreq/available_governors"
        )
        for (path in paths) {
            val raw = readFile(path)
            if (raw != null) return raw.split(" ").filter { it.isNotBlank() }
        }
        return listOf("msm-adreno-tz", "performance", "powersave", "simple_ondemand")
    }

    // ─── I/O Scheduler ───────────────────────────────────────────
    suspend fun getAvailableSchedulers(): List<String> {
        val raw = readFile("/sys/block/sda/queue/scheduler")
            ?: readFile("/sys/block/mmcblk0/queue/scheduler")
        if (raw != null) {
            return Regex("\\[?(\\w+)\\]?").findAll(raw)
                .map { it.groupValues[1] }
                .filter { it.isNotBlank() }
                .toList()
        }
        return listOf("noop", "cfq", "deadline", "bfq", "mq-deadline")
    }

    suspend fun getCurrentScheduler(): String {
        val raw = readFile("/sys/block/sda/queue/scheduler")
            ?: readFile("/sys/block/mmcblk0/queue/scheduler")
        val match = Regex("\\[([\\w-]+)\\]").find(raw ?: "")
        return match?.groupValues?.get(1) ?: "unknown"
    }

    suspend fun setScheduler(scheduler: String): Boolean {
        val blocks = listOf("sda", "sdb", "mmcblk0", "mmcblk1")
        var success = false
        for (block in blocks) {
            val path = "/sys/block/$block/queue/scheduler"
            if (writeFile(path, scheduler)) success = true
        }
        return success
    }

    // ─── CPU Temperature ─────────────────────────────────────────
    suspend fun getCpuTemp(): String {
        val paths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone5/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp"
        )
        for (path in paths) {
            val raw = readFile(path)?.toLongOrNull()
            if (raw != null) {
                val celsius = if (raw > 1000) raw / 1000 else raw
                return "${celsius}°C"
            }
        }
        return "-"
    }

    // ─── Apply Full Profile ───────────────────────────────────────
    suspend fun applyProfile(profile: com.riodev.kernelperf.data.model.AppProfile) {
        // Set governor sesuai power mode dulu
        val governor = when (profile.powerMode) {
            com.riodev.kernelperf.data.model.PowerMode.POWERSAVE -> "powersave"
            com.riodev.kernelperf.data.model.PowerMode.PERFORMANCE -> "performance"
            com.riodev.kernelperf.data.model.PowerMode.GAMING -> "performance"
            com.riodev.kernelperf.data.model.PowerMode.BALANCED -> "schedutil"
            com.riodev.kernelperf.data.model.PowerMode.CUSTOM -> profile.cpuGovernor
        }

        setGovernor(governor)

        // Frekuensi custom
        if (profile.cpuMinFreq > 0) setMinFreq(profile.cpuMinFreq)
        if (profile.cpuMaxFreq > 0) setMaxFreq(profile.cpuMaxFreq)

        // GPU
        if (profile.gpuGovernor != "default") setGpuGovernor(profile.gpuGovernor)

        // I/O
        if (profile.ioScheduler != "default") setScheduler(profile.ioScheduler)

        // Custom tweaks
        if (profile.customTweaks.isNotBlank()) {
            profile.customTweaks.split(";").forEach { pair ->
                val parts = pair.trim().split("=")
                if (parts.size == 2) writeFile(parts[0].trim(), parts[1].trim())
            }
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────
    private suspend fun getCpuCount(): Int {
        val out = runCmdOutput("ls /sys/devices/system/cpu/ | grep -c 'cpu[0-9]'")
        return out.firstOrNull()?.trim()?.toIntOrNull() ?: 8
    }

    fun formatFreq(khz: Long): String {
        return if (khz >= 1_000_000) {
            String.format("%.1f GHz", khz / 1_000_000.0)
        } else {
            String.format("%d MHz", khz / 1000)
        }
    }

    fun formatFreqInt(khz: Int): String = formatFreq(khz.toLong())
}

package com.riodev.kernelperf.root

import android.os.Build
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object RootUtils {

    fun initShell() {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR).setTimeout(10))
    }

    val isRooted: Boolean get() = try { Shell.getShell().isRoot } catch (e: Exception) { false }

    suspend fun node(path: String): String? = withContext(Dispatchers.IO) {
        try {
            val r = Shell.cmd("cat $path 2>/dev/null").exec()
            if (r.isSuccess && r.out.isNotEmpty()) r.out.first().trim() else null
        } catch (e: Exception) { null }
    }

    suspend fun write(path: String, value: String): Boolean = withContext(Dispatchers.IO) {
        try { Shell.cmd("echo '$value' > $path").exec().isSuccess } catch (e: Exception) { false }
    }

    suspend fun cmd(cmd: String): List<String> = withContext(Dispatchers.IO) {
        try { Shell.cmd(cmd).exec().out } catch (e: Exception) { emptyList() }
    }

    // ── CPU clusters ─────────────────────────────────────────────
    suspend fun getCpuCount(): Int = cmd("ls /sys/devices/system/cpu/ | grep -c 'cpu[0-9]'")
        .firstOrNull()?.trim()?.toIntOrNull() ?: 8

    // Poco X6 5G (SM7550) = cpu0-3 Little, cpu4-6 Big, cpu7 Prime
    suspend fun getLittlePolicy(): String = if (node("/sys/devices/system/cpu/cpufreq/policy0/scaling_governor") != null) "policy0" else "cpu0"
    suspend fun getBigPolicy(): String = if (node("/sys/devices/system/cpu/cpufreq/policy4/scaling_governor") != null) "policy4" else "policy0"

    suspend fun getGovernor(policy: String) = node("/sys/devices/system/cpu/cpufreq/$policy/scaling_governor") ?: "-"
    suspend fun getMinFreq(policy: String): String { val k = node("/sys/devices/system/cpu/cpufreq/$policy/scaling_min_freq")?.toLongOrNull() ?: return "-"; return fmtFreq(k) }
    suspend fun getMaxFreq(policy: String): String { val k = node("/sys/devices/system/cpu/cpufreq/$policy/scaling_max_freq")?.toLongOrNull() ?: return "-"; return fmtFreq(k) }
    suspend fun getCurFreq(policy: String): String { val k = node("/sys/devices/system/cpu/cpufreq/$policy/scaling_cur_freq")?.toLongOrNull() ?: return "-"; return fmtFreq(k) }

    suspend fun getAvailableGovernors(policy: String = "policy0"): List<String> {
        val raw = node("/sys/devices/system/cpu/cpufreq/$policy/scaling_available_governors") ?: return emptyList()
        return raw.split(" ").filter { it.isNotBlank() }.sorted()
    }

    suspend fun getAvailableFrequencies(policy: String = "policy0"): List<Int> {
        val raw = node("/sys/devices/system/cpu/cpufreq/$policy/scaling_available_frequencies") ?: return emptyList()
        return raw.split(" ").mapNotNull { it.trim().toIntOrNull() }.sorted()
    }

    suspend fun setGovernor(governor: String): Boolean {
        val n = getCpuCount()
        var ok = true
        for (i in 0 until n) if (!write("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor", governor)) ok = false
        return ok
    }

    suspend fun setMinFreq(freqKhz: Int): Boolean {
        val n = getCpuCount()
        var ok = true
        for (i in 0 until n) if (!write("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_min_freq", freqKhz.toString())) ok = false
        return ok
    }

    suspend fun setMaxFreq(freqKhz: Int): Boolean {
        val n = getCpuCount()
        var ok = true
        for (i in 0 until n) if (!write("/sys/devices/system/cpu/cpu$i/cpufreq/scaling_max_freq", freqKhz.toString())) ok = false
        return ok
    }

    // ── GPU ───────────────────────────────────────────────────────
    private val GPU_GOV_PATHS = listOf("/sys/class/kgsl/kgsl-3d0/devfreq/governor", "/sys/class/devfreq/gpufreq/governor")
    private val GPU_CUR_PATHS = listOf("/sys/class/kgsl/kgsl-3d0/gpuclk", "/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
    private val GPU_MIN_PATHS = listOf("/sys/class/kgsl/kgsl-3d0/devfreq/min_freq", "/sys/class/devfreq/gpufreq/min_freq")
    private val GPU_MAX_PATHS = listOf("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", "/sys/class/devfreq/gpufreq/max_freq")

    suspend fun getGpuGovernor(): String { for (p in GPU_GOV_PATHS) { val v = node(p); if (v != null) return v }; return "-" }
    suspend fun getGpuCurFreq(): String { for (p in GPU_CUR_PATHS) { val v = node(p)?.toLongOrNull(); if (v != null) return fmtFreq(v) }; return "-" }
    suspend fun getGpuMinFreq(): String { for (p in GPU_MIN_PATHS) { val v = node(p)?.toLongOrNull(); if (v != null) return fmtFreq(v) }; return "-" }
    suspend fun getGpuMaxFreq(): String { for (p in GPU_MAX_PATHS) { val v = node(p)?.toLongOrNull(); if (v != null) return fmtFreq(v) }; return "-" }

    suspend fun getAvailableGpuGovernors(): List<String> {
        val paths = listOf("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors", "/sys/class/devfreq/gpufreq/available_governors")
        for (p in paths) { val raw = node(p); if (raw != null) return raw.split(" ").filter { it.isNotBlank() } }
        return listOf("msm-adreno-tz", "performance", "powersave", "simple_ondemand")
    }

    suspend fun setGpuGovernor(gov: String): Boolean { for (p in GPU_GOV_PATHS) { if (write(p, gov)) return true }; return false }

    // ── I/O ───────────────────────────────────────────────────────
    suspend fun getCurrentScheduler(): String {
        for (b in listOf("sda", "mmcblk0")) {
            val raw = node("/sys/block/$b/queue/scheduler") ?: continue
            val m = Regex("\\[([\\w-]+)\\]").find(raw)
            if (m != null) return m.groupValues[1]
        }
        return "-"
    }

    suspend fun getAvailableSchedulers(): List<String> {
        val raw = node("/sys/block/sda/queue/scheduler") ?: node("/sys/block/mmcblk0/queue/scheduler") ?: return emptyList()
        return Regex("\\[?(\\w[\\w-]*)\\]?").findAll(raw).map { it.groupValues[1] }.filter { it.isNotBlank() }.toList()
    }

    suspend fun setScheduler(sched: String): Boolean {
        var ok = false
        for (b in listOf("sda", "sdb", "mmcblk0", "mmcblk1")) if (write("/sys/block/$b/queue/scheduler", sched)) ok = true
        return ok
    }

    // ── Thermal ───────────────────────────────────────────────────
    suspend fun getCpuTemp(): String {
        val paths = listOf("/sys/class/thermal/thermal_zone0/temp", "/sys/class/thermal/thermal_zone5/temp", "/sys/class/thermal/thermal_zone10/temp")
        for (p in paths) { val v = node(p)?.toLongOrNull() ?: continue; return "${if (v > 1000) v/1000 else v}°C" }
        return "-"
    }

    suspend fun getBatteryTemp(): String {
        val v = node("/sys/class/power_supply/battery/temp")?.toLongOrNull() ?: return "-"
        return "${v/10}°C"
    }

    suspend fun getThermalProfile(): Int = node("/sys/devices/virtual/thermal/thermal_message/sconfig")?.toIntOrNull() ?: 0
    suspend fun setThermalProfile(profile: Int): Boolean = write("/sys/devices/virtual/thermal/thermal_message/sconfig", profile.toString())

    // ── Device Info ───────────────────────────────────────────────
    fun getDeviceInfo(): com.riodev.kernelperf.data.model.DeviceInfo {
        val model = "${Build.MANUFACTURER} ${Build.MODEL}"
        val chipset = Build.HARDWARE.ifBlank { "Unknown" }
        return com.riodev.kernelperf.data.model.DeviceInfo(
            model = model,
            chipset = chipset,
            kernel = System.getProperty("os.version") ?: "-",
            totalRam = getTotalRam(),
            androidVersion = "Android ${Build.VERSION.RELEASE}"
        )
    }

    private fun getTotalRam(): String {
        return try {
            val mi = android.app.ActivityManager.MemoryInfo()
            val am = android.app.ActivityManager::class.java
            // fallback ke /proc/meminfo
            val lines = java.io.File("/proc/meminfo").readLines()
            val total = lines.firstOrNull { it.startsWith("MemTotal") }
                ?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: return "-"
            val gb = total / 1024 / 1024
            if (gb > 0) "${gb} GB" else "${total/1024} MB"
        } catch (e: Exception) { "-" }
    }

    // ── Apply full profile ────────────────────────────────────────
    suspend fun applyProfile(profile: com.riodev.kernelperf.data.model.AppProfile) {
        val gov = when (profile.powerMode) {
            com.riodev.kernelperf.data.model.PowerMode.POWERSAVE -> "powersave"
            com.riodev.kernelperf.data.model.PowerMode.PERFORMANCE -> "performance"
            com.riodev.kernelperf.data.model.PowerMode.GAMING -> "performance"
            com.riodev.kernelperf.data.model.PowerMode.BALANCED -> "schedutil"
            com.riodev.kernelperf.data.model.PowerMode.CUSTOM -> profile.cpuGovernor
        }
        setGovernor(gov)
        if (profile.cpuMinFreq > 0) setMinFreq(profile.cpuMinFreq)
        if (profile.cpuMaxFreq > 0) setMaxFreq(profile.cpuMaxFreq)
        if (profile.gpuGovernor != "default") setGpuGovernor(profile.gpuGovernor)
        if (profile.ioScheduler != "default") setScheduler(profile.ioScheduler)
        if (profile.customTweaks.isNotBlank()) {
            profile.customTweaks.split(";").forEach { pair ->
                val parts = pair.trim().split("=")
                if (parts.size == 2) write(parts[0].trim(), parts[1].trim())
            }
        }
    }

    fun fmtFreq(khz: Long): String = if (khz >= 1_000_000) String.format("%.1f GHz", khz/1_000_000.0) else String.format("%d MHz", khz/1000)
    fun fmtFreqInt(khz: Int): String = fmtFreq(khz.toLong())
}

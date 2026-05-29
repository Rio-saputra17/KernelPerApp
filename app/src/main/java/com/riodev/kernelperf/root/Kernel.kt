package com.riodev.kernelperf.root

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.DeviceInfo
import com.riodev.kernelperf.data.model.IdleProfile
import com.riodev.kernelperf.data.model.KernelStatus
import com.topjohnwu.superuser.Shell

object Kernel {

    fun initShell() {
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR).setTimeout(10)
        )
    }

    val isRooted: Boolean get() = try { Shell.getShell().isRoot } catch (e: Exception) { false }

    // ── Read/Write ────────────────────────────────────────────
    fun read(path: String): String? {
        val r = Shell.cmd("cat $path 2>/dev/null").exec()
        return if (r.isSuccess && r.out.isNotEmpty()) r.out.first().trim() else null
    }

    fun write(path: String, value: String) {
        Shell.cmd("echo '$value' > $path 2>/dev/null").exec()
    }

    // Batch write — eksekusi semua dalam 1 shell call, jauh lebih efisien
    private fun writeBatch(cmds: List<String>) {
        if (cmds.isEmpty()) return
        Shell.cmd(cmds.joinToString(" && ")).exec()
    }

    // ── CPU policies ──────────────────────────────────────────
    // Poco X6 5G (SM7550): policy0=Little(0-3), policy4=Big(4-6), policy7=Prime
    private val LITTLE = "policy0"
    private val BIG    = "policy4"

    private fun cpuPath(policy: String, node: String) =
        "/sys/devices/system/cpu/cpufreq/$policy/$node"

    fun getGovernor(policy: String)  = read(cpuPath(policy, "scaling_governor")) ?: "-"
    fun getCurFreq(policy: String)   = read(cpuPath(policy, "scaling_cur_freq"))?.toLongOrNull()?.let { fmtKhz(it) } ?: "-"
    fun getMinFreq(policy: String)   = read(cpuPath(policy, "scaling_min_freq"))?.toLongOrNull()?.let { fmtKhz(it) } ?: "-"
    fun getMaxFreq(policy: String)   = read(cpuPath(policy, "scaling_max_freq"))?.toLongOrNull()?.let { fmtKhz(it) } ?: "-"
    fun getAvailableGovernors(policy: String): List<String> =
        (read(cpuPath(policy, "scaling_available_governors")) ?: "").split(" ").filter { it.isNotBlank() }.sorted()
    fun getAvailableFreqs(policy: String): List<Int> =
        (read(cpuPath(policy, "scaling_available_frequencies")) ?: "").split(" ").mapNotNull { it.trim().toIntOrNull() }.sorted()

    // ── GPU (kgsl Adreno path) ────────────────────────────────
    private val GPU_GOV         = "/sys/class/kgsl/kgsl-3d0/devfreq/governor"
    private val GPU_CUR         = "/sys/class/kgsl/kgsl-3d0/gpuclk"
    private val GPU_MIN         = "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"
    private val GPU_MAX         = "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq"
    private val GPU_AVAIL_FREQS = "/sys/class/kgsl/kgsl-3d0/gpu_available_frequencies"

    fun getGpuGovernor()   = read(GPU_GOV) ?: "-"
    fun getGpuCurFreq()    = read(GPU_CUR)?.toLongOrNull()?.let { fmtHz(it) } ?: "-"
    fun getGpuMinFreq()    = read(GPU_MIN)?.toLongOrNull()?.let { fmtHz(it) } ?: "-"
    fun getGpuMaxFreq()    = read(GPU_MAX)?.toLongOrNull()?.let { fmtHz(it) } ?: "-"
    fun getAvailableGpuFreqs(): List<Long> =
        (read(GPU_AVAIL_FREQS) ?: "").split(" ").mapNotNull { it.trim().toLongOrNull() }.sorted()
    fun getAvailableGpuGovernors(): List<String> =
        (read("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors") ?: "").split(" ").filter { it.isNotBlank() }

    // ── Thermal ───────────────────────────────────────────────
    fun getCpuTemp(): String {
        for (p in listOf(
            "/sys/devices/virtual/thermal/thermal_zone5/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/thermal/thermal_zone0/temp"
        )) {
            val v = read(p)?.toLongOrNull() ?: continue
            return "${if (v > 1000) v / 1000 else v}°C"
        }
        return "-"
    }

    fun getThermalProfile() =
        read("/sys/devices/virtual/thermal/thermal_message/sconfig")?.toIntOrNull() ?: 0

    // ── Battery ───────────────────────────────────────────────
    fun getBatteryInfo(context: Context): Triple<String, String, String> {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level  = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale  = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val temp   = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
            val status = when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)) {
                BatteryManager.BATTERY_STATUS_CHARGING    -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL        -> "Full"
                else -> "-"
            }
            Triple("${(level * 100 / scale)}%", "${temp / 10}°C", status)
        } catch (e: Exception) { Triple("-", "-", "-") }
    }

    // ── Live status ───────────────────────────────────────────
    fun getLiveStatus(context: Context): KernelStatus {
        val (bLevel, bTemp, bStatus) = getBatteryInfo(context)
        return KernelStatus(
            littleGovernor = getGovernor(LITTLE),
            littleCurFreq  = getCurFreq(LITTLE),
            bigGovernor    = getGovernor(BIG),
            bigCurFreq     = getCurFreq(BIG),
            gpuGovernor    = getGpuGovernor(),
            gpuCurFreq     = getGpuCurFreq(),
            cpuTemp        = getCpuTemp(),
            batteryLevel   = bLevel,
            batteryTemp    = bTemp,
            batteryStatus  = bStatus
        )
    }

    // ── Daemon control ────────────────────────────────────────
    // Daftar daemon MIUI/HyperOS yang override governor
    private val PERF_DAEMONS = listOf(
        "mpdecision",
        "perfservice",
        "vendor.perfservice",
        "joyose"
    )

    // Stop semua daemon performa MIUI — dipanggil saat masuk gaming
    // Setelah ini governor tidak akan di-override, enforce loop tidak perlu agresif
    fun stopPerfDaemons() {
        val cmds = PERF_DAEMONS.map { "stop $it 2>/dev/null; killall -q $it 2>/dev/null" }
        Shell.cmd(cmds.joinToString("; ")).exec()
    }

    // Restart daemon — dipanggil saat keluar gaming / kembali idle
    fun startPerfDaemons() {
        val cmds = PERF_DAEMONS.map { "start $it 2>/dev/null" }
        Shell.cmd(cmds.joinToString("; ")).exec()
    }

    // Cek apakah daemon aktif
    fun isDaemonRunning(name: String): Boolean {
        val r = Shell.cmd("pgrep -x $name").exec()
        return r.isSuccess && r.out.isNotEmpty()
    }

    // ── Apply profiles ────────────────────────────────────────

    // applyIdle: restart daemon dulu, baru set profil
    // Daemon dibiarkan hidup saat idle — normal, hemat baterai
    fun applyIdle(p: IdleProfile) {
        // Restart daemon kalau sebelumnya dimatikan saat gaming
        startPerfDaemons()

        // Kumpulkan semua write dalam 1 batch
        val cmds = mutableListOf<String>()

        // Little cluster
        buildGovernorCmds(LITTLE, p.littleGovernor, cmds)
        if (p.littleMinFreq > 0) cmds.add("echo ${p.littleMinFreq} > ${cpuPath(LITTLE, "scaling_min_freq")}")
        if (p.littleMaxFreq > 0) cmds.add("echo ${p.littleMaxFreq} > ${cpuPath(LITTLE, "scaling_max_freq")}")

        // Big cluster
        buildGovernorCmds(BIG, p.bigGovernor, cmds)
        if (p.bigMinFreq > 0) cmds.add("echo ${p.bigMinFreq} > ${cpuPath(BIG, "scaling_min_freq")}")
        if (p.bigMaxFreq > 0) cmds.add("echo ${p.bigMaxFreq} > ${cpuPath(BIG, "scaling_max_freq")}")

        // GPU
        if (p.gpuMinFreq > 0) cmds.add("echo ${p.gpuMinFreq} > $GPU_MIN")
        if (p.gpuMaxFreq > 0) cmds.add("echo ${p.gpuMaxFreq} > $GPU_MAX")

        // Thermal
        cmds.add("echo ${p.thermalProfile} > /sys/devices/virtual/thermal/thermal_message/sconfig")

        writeBatch(cmds)
    }

    // applyGame: matikan daemon dulu, baru set profil
    // Governor tidak akan di-override setelah daemon mati
    // Enforce loop di AppDetectionService cukup tiap 60 detik sebagai safety net
    fun applyGame(p: AppProfile) {
        // Matikan daemon MIUI yang override governor
        stopPerfDaemons()

        val cmds = mutableListOf<String>()

        // Little cluster
        buildGovernorCmds(LITTLE, p.littleGovernor, cmds)
        if (p.littleMinFreq > 0) cmds.add("echo ${p.littleMinFreq} > ${cpuPath(LITTLE, "scaling_min_freq")}")
        if (p.littleMaxFreq > 0) cmds.add("echo ${p.littleMaxFreq} > ${cpuPath(LITTLE, "scaling_max_freq")}")

        // Big cluster
        buildGovernorCmds(BIG, p.bigGovernor, cmds)
        if (p.bigMinFreq > 0) cmds.add("echo ${p.bigMinFreq} > ${cpuPath(BIG, "scaling_min_freq")}")
        if (p.bigMaxFreq > 0) cmds.add("echo ${p.bigMaxFreq} > ${cpuPath(BIG, "scaling_max_freq")}")

        // GPU
        if (p.gpuMinFreq > 0) cmds.add("echo ${p.gpuMinFreq} > $GPU_MIN")
        if (p.gpuMaxFreq > 0) cmds.add("echo ${p.gpuMaxFreq} > $GPU_MAX")

        // Thermal
        cmds.add("echo ${p.thermalProfile} > /sys/devices/virtual/thermal/thermal_message/sconfig")

        writeBatch(cmds)
    }

    // Build semua perintah governor untuk 1 policy — tulis ke policy node + tiap CPU
    private fun buildGovernorCmds(policy: String, gov: String, out: MutableList<String>) {
        out.add("echo $gov > ${cpuPath(policy, "scaling_governor")}")
        val cpus = when (policy) {
            "policy0" -> listOf(0, 1, 2, 3)
            "policy4" -> listOf(4, 5, 6)
            "policy7" -> listOf(7)
            else -> emptyList()
        }
        for (i in cpus) out.add("echo $gov > /sys/devices/system/cpu/cpu$i/cpufreq/scaling_governor")
    }

    // ── Device info ───────────────────────────────────────────
    fun getDeviceInfo(): DeviceInfo {
        val ram = try {
            val kb = java.io.File("/proc/meminfo").readLines()
                .firstOrNull { it.startsWith("MemTotal") }
                ?.replace(Regex("[^0-9]"), "")?.toLongOrNull() ?: 0L
            if (kb / 1024 / 1024 > 0) "${kb / 1024 / 1024} GB" else "${kb / 1024} MB"
        } catch (e: Exception) { "-" }
        return DeviceInfo(
            model          = "${Build.MANUFACTURER} ${Build.MODEL}",
            chipset        = Build.HARDWARE.ifBlank { "-" },
            kernel         = System.getProperty("os.version") ?: "-",
            totalRam       = ram,
            androidVersion = "Android ${Build.VERSION.RELEASE}"
        )
    }

    // ── Format ────────────────────────────────────────────────
    fun fmtKhz(khz: Long): String  = if (khz >= 1_000_000) "%.1f GHz".format(khz / 1_000_000.0) else "%d MHz".format(khz / 1000)
    fun fmtHz(hz: Long): String    = if (hz >= 1_000_000_000) "%.0f MHz".format(hz / 1_000_000.0) else if (hz >= 1_000_000) "%.0f MHz".format(hz / 1_000_000.0) else "${hz} Hz"
    fun fmtFreqInt(khz: Int): String  = if (khz == 0) "Default" else fmtKhz(khz.toLong())
    fun fmtFreqLong(hz: Long): String = if (hz == 0L) "Default" else fmtHz(hz)
}

package com.riodev.kernelperf.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_profiles")
data class AppProfile(
    @PrimaryKey val packageName: String,
    val appName: String,
    val cpuGovernor: String = "schedutil",
    val cpuMinFreq: Int = 0,
    val cpuMaxFreq: Int = 0,
    val gpuGovernor: String = "default",
    val gpuMinFreq: Int = 0,
    val gpuMaxFreq: Int = 0,
    val ioScheduler: String = "default",
    val powerMode: PowerMode = PowerMode.BALANCED,
    val customTweaks: String = "",
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class PowerMode(val label: String) {
    POWERSAVE("Power Save"), BALANCED("Balanced"),
    PERFORMANCE("Performance"), GAMING("Gaming"), CUSTOM("Custom")
}

data class InstalledApp(val packageName: String, val appName: String, val hasProfile: Boolean = false)

data class KernelStatus(
    val littleGovernor: String = "-", val littleMinFreq: String = "-",
    val littleMaxFreq: String = "-", val littleCurFreq: String = "-",
    val bigGovernor: String = "-", val bigMinFreq: String = "-",
    val bigMaxFreq: String = "-", val bigCurFreq: String = "-",
    val gpuGovernor: String = "-", val gpuMinFreq: String = "-",
    val gpuMaxFreq: String = "-", val gpuCurFreq: String = "-",
    val cpuTemp: String = "-", val batteryTemp: String = "-",
    val ioScheduler: String = "-"
)

data class DeviceInfo(
    val model: String = "-", val chipset: String = "-",
    val kernel: String = "-", val totalRam: String = "-",
    val androidVersion: String = "-"
)

data class DefaultProfile(
    val cpuGovernor: String = "schedutil",
    val cpuMinFreq: Int = 0, val cpuMaxFreq: Int = 0,
    val gpuGovernor: String = "default",
    val gpuMinFreq: Int = 0, val gpuMaxFreq: Int = 0,
    val ioScheduler: String = "default",
    val thermalProfile: Int = 0
)

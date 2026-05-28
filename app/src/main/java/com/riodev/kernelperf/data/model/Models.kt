package com.riodev.kernelperf.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_profiles")
data class AppProfile(
    @PrimaryKey val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val littleGovernor: String = "performance",
    val littleMinFreq: Int = 0,
    val littleMaxFreq: Int = 0,
    val bigGovernor: String = "performance",
    val bigMinFreq: Int = 0,
    val bigMaxFreq: Int = 0,
    val gpuMinFreq: Int = 0,
    val gpuMaxFreq: Int = 0,
    val thermalProfile: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)

data class IdleProfile(
    val littleGovernor: String = "schedutil",
    val littleMinFreq: Int = 0,
    val littleMaxFreq: Int = 0,
    val bigGovernor: String = "schedutil",
    val bigMinFreq: Int = 0,
    val bigMaxFreq: Int = 0,
    val gpuMinFreq: Int = 0,
    val gpuMaxFreq: Int = 0,
    val thermalProfile: Int = 3
)

data class KernelStatus(
    val littleGovernor: String = "-",
    val littleCurFreq: String = "-",
    val bigGovernor: String = "-",
    val bigCurFreq: String = "-",
    val gpuGovernor: String = "-",
    val gpuCurFreq: String = "-",
    val cpuTemp: String = "-",
    val batteryLevel: String = "-",
    val batteryTemp: String = "-",
    val batteryStatus: String = "-"
)

data class DeviceInfo(
    val model: String = "-",
    val chipset: String = "-",
    val kernel: String = "-",
    val totalRam: String = "-",
    val androidVersion: String = "-"
)

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val hasProfile: Boolean = false
)

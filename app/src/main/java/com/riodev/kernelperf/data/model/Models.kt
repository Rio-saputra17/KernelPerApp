package com.riodev.kernelperf.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Profil per-app yang disimpan di database
 */
@Entity(tableName = "app_profiles")
data class AppProfile(
    @PrimaryKey
    val packageName: String,
    val appName: String,

    // CPU Settings
    val cpuGovernor: String = "schedutil",
    val cpuMinFreq: Int = 0,       // dalam kHz, 0 = default
    val cpuMaxFreq: Int = 0,       // dalam kHz, 0 = default

    // GPU Settings
    val gpuGovernor: String = "default",
    val gpuMinFreq: Int = 0,
    val gpuMaxFreq: Int = 0,

    // I/O Scheduler
    val ioScheduler: String = "default",

    // Power Mode
    val powerMode: PowerMode = PowerMode.BALANCED,

    // Custom kernel tweaks (key=value pairs, semicolon separated)
    val customTweaks: String = "",

    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class PowerMode(val label: String, val description: String) {
    POWERSAVE("Power Save", "Hemat baterai maksimal"),
    BALANCED("Balanced", "Seimbang performa & baterai"),
    PERFORMANCE("Performance", "Performa tinggi"),
    GAMING("Gaming", "Unlock semua core, frekuensi max"),
    CUSTOM("Custom", "Pengaturan manual")
}

/**
 * Info app yang terinstall
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val hasProfile: Boolean = false
)

/**
 * Status kernel saat ini
 */
data class KernelStatus(
    val currentGovernor: String = "-",
    val currentMinFreq: String = "-",
    val currentMaxFreq: String = "-",
    val currentFreq: String = "-",
    val cpuTemp: String = "-",
    val gpuGovernor: String = "-",
    val ioScheduler: String = "-",
    val activeProfileApp: String? = null
)

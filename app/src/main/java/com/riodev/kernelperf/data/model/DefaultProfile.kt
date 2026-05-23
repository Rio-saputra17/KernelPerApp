package com.riodev.kernelperf.data.model

/**
 * Profil default/idle — diterapkan saat tidak ada app berprofl yang aktif
 */
data class DefaultProfile(
    val cpuGovernor: String = "schedutil",
    val cpuMinFreq: Int = 0,
    val cpuMaxFreq: Int = 0,
    val gpuGovernor: String = "default",
    val ioScheduler: String = "default"
)

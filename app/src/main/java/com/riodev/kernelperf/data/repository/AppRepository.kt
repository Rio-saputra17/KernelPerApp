package com.riodev.kernelperf.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.riodev.kernelperf.data.model.AppDatabase
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.InstalledApp
import com.riodev.kernelperf.data.model.KernelStatus
import com.riodev.kernelperf.root.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val dao = db.appProfileDao()

    // ─── Profiles ─────────────────────────────────────────────────
    fun getAllProfiles(): Flow<List<AppProfile>> = dao.getAllProfiles()

    suspend fun getProfile(packageName: String): AppProfile? = dao.getProfile(packageName)

    suspend fun saveProfile(profile: AppProfile) {
        dao.insertProfile(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteProfile(packageName: String) = dao.deleteByPackage(packageName)

    suspend fun getEnabledProfiles(): List<AppProfile> = dao.getEnabledProfiles()

    // ─── Installed Apps ───────────────────────────────────────────
    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val enabledProfiles = getEnabledProfiles().map { it.packageName }.toSet()

        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { isUserApp(it) }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    appName = pm.getApplicationLabel(info).toString(),
                    hasProfile = info.packageName in enabledProfiles
                )
            }
            .sortedWith(compareByDescending<InstalledApp> { it.hasProfile }.thenBy { it.appName })
    }

    private fun isUserApp(info: ApplicationInfo): Boolean {
        return (info.flags and ApplicationInfo.FLAG_SYSTEM) == 0 ||
               (info.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    // ─── Kernel Status ────────────────────────────────────────────
    suspend fun getKernelStatus(): KernelStatus {
        return KernelStatus(
            currentGovernor = RootUtils.getCurrentGovernor(),
            currentMinFreq = RootUtils.getCurrentMinFreq(),
            currentMaxFreq = RootUtils.getCurrentMaxFreq(),
            currentFreq = RootUtils.getCurrentFreq(),
            cpuTemp = RootUtils.getCpuTemp(),
            gpuGovernor = RootUtils.getGpuGovernor(),
            ioScheduler = RootUtils.getCurrentScheduler()
        )
    }

    // ─── Available Options ────────────────────────────────────────
    suspend fun getAvailableGovernors(): List<String> = RootUtils.getAvailableGovernors()
    suspend fun getAvailableFrequencies(): List<Int> = RootUtils.getAvailableFrequencies()
    suspend fun getAvailableSchedulers(): List<String> = RootUtils.getAvailableSchedulers()
    suspend fun getAvailableGpuGovernors(): List<String> = RootUtils.getAvailableGpuGovernors()

    // ─── Apply Profile ────────────────────────────────────────────
    suspend fun applyProfile(packageName: String) {
        val profile = dao.getProfile(packageName) ?: return
        if (profile.isEnabled) RootUtils.applyProfile(profile)
    }
}

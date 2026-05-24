package com.riodev.kernelperf.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.riodev.kernelperf.data.model.AppDatabase
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.InstalledApp
import com.riodev.kernelperf.root.RootUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val db = AppDatabase.getInstance(context)
    private val dao = db.appProfileDao()

    fun getAllProfiles(): Flow<List<AppProfile>> = dao.getAllProfiles()

    suspend fun getProfile(packageName: String): AppProfile? =
        withContext(Dispatchers.IO) { dao.getProfile(packageName) }

    suspend fun saveProfile(profile: AppProfile) =
        withContext(Dispatchers.IO) { dao.insertOrUpdate(profile) }

    suspend fun deleteProfile(packageName: String) =
        withContext(Dispatchers.IO) { dao.delete(packageName) }

    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val profiles = dao.getAllProfiles().first().associateBy { it.packageName }
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { it.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    appName = pm.getApplicationLabel(info).toString(),
                    hasProfile = profiles.containsKey(info.packageName)
                )
            }
            .sortedBy { it.appName }
    }

    suspend fun applyProfile(packageName: String) {
        val profile = getProfile(packageName) ?: return
        withContext(Dispatchers.IO) {
            if (profile.cpuGovernor.isNotEmpty()) RootUtils.setGovernor(profile.cpuGovernor)
            if (profile.cpuMinFreq > 0) RootUtils.setMinFreq(profile.cpuMinFreq)
            if (profile.cpuMaxFreq > 0) RootUtils.setMaxFreq(profile.cpuMaxFreq)
            if (profile.gpuGovernor != "default") RootUtils.setGpuGovernor(profile.gpuGovernor)
            if (profile.ioScheduler != "default") RootUtils.setScheduler(profile.ioScheduler)
        }
    }
}

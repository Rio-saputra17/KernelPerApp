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
import kotlinx.coroutines.withContext

class AppRepository(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val dao = db.appProfileDao()

    fun getAllProfiles(): Flow<List<AppProfile>> = dao.getAllProfiles()
    suspend fun getProfile(pkg: String): AppProfile? = dao.getProfile(pkg)
    suspend fun saveProfile(p: AppProfile) = dao.insertProfile(p.copy(updatedAt = System.currentTimeMillis()))
    suspend fun deleteProfile(pkg: String) = dao.deleteByPackage(pkg)
    suspend fun getEnabledProfiles(): List<AppProfile> = dao.getEnabledProfiles()

    suspend fun getInstalledApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val enabled = getEnabledProfiles().map { it.packageName }.toSet()
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || (it.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0 }
            .map { InstalledApp(it.packageName, pm.getApplicationLabel(it).toString(), it.packageName in enabled) }
            .sortedWith(compareByDescending<InstalledApp> { it.hasProfile }.thenBy { it.appName })
    }

    suspend fun applyProfile(pkg: String) {
        val p = dao.getProfile(pkg) ?: return
        if (p.isEnabled) RootUtils.applyProfile(p)
    }

    suspend fun getAvailableGovernors() = RootUtils.getAvailableGovernors("policy0")
    suspend fun getAvailableGovernorsBig() = RootUtils.getAvailableGovernors("policy4").ifEmpty { getAvailableGovernors() }
    suspend fun getAvailableFrequencies() = RootUtils.getAvailableFrequencies("policy0")
    suspend fun getAvailableFrequenciesBig() = RootUtils.getAvailableFrequencies("policy4").ifEmpty { getAvailableFrequencies() }
    suspend fun getAvailableSchedulers() = RootUtils.getAvailableSchedulers()
    suspend fun getAvailableGpuGovernors() = RootUtils.getAvailableGpuGovernors()
}

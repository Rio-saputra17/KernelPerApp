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
    private val db = AppDatabase.getDatabase(context)
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
        val profiles = dao.getAllProfiles().first().associateBy { profile -> profile.packageName }
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { info -> info.flags and ApplicationInfo.FLAG_SYSTEM == 0 }
            .map { info ->
                InstalledApp(
                    packageName = info.packageName,
                    appName = pm.getApplicationLabel(info).toString(),
                    hasProfile = profiles.containsKey(info.packageName)
                )
            }
            .sortedBy { app -> app.appName }
    }

    suspend fun applyProfile(packageName: String) {
        val profile = getProfile(packageName) ?: return
        RootUtils.applyProfile(profile)
    }
}

package com.riodev.kernelperf.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.IdleProfile
import com.riodev.kernelperf.data.repository.AppRepository
import com.riodev.kernelperf.root.Kernel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

// Sama persis dengan di MainViewModel
private val Context.ds by preferencesDataStore("idle")

class AppDetectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: AppRepository
    private lateinit var usm: UsageStatsManager
    private var lastPkg = ""
    private var isGameActive = false
    private var restoreJob: Job? = null
    private var enforceJob: Job? = null
    private var currentIdleProfile = IdleProfile()

    companion object {
        var isRunning = false
        var currentForegroundApp = ""

        // Dipanggil dari ViewModel saat user simpan profil
        var onIdleUpdated: ((IdleProfile) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(applicationContext)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        isRunning = true

        // Update callback dari ViewModel
        onIdleUpdated = { p ->
            currentIdleProfile = p
            // Kalau tidak sedang game, langsung apply + restart enforce
            if (!isGameActive) {
                scope.launch {
                    Kernel.applyIdle(p)
                    startIdleEnforce()
                }
            }
        }

        scope.launch {
            // KUNCI: Baca dari DataStore dulu sebelum apply apapun
            currentIdleProfile = loadIdleFromDataStore()
            delay(2000)
            Kernel.applyIdle(currentIdleProfile)
            startIdleEnforce()
            monitor()
        }
    }

    // Baca DataStore langsung di service — tidak bergantung pada ViewModel
    private suspend fun loadIdleFromDataStore(): IdleProfile {
        return try {
            val prefs = applicationContext.ds.data.first()
            IdleProfile(
                littleGovernor = prefs[stringPreferencesKey("l_gov")] ?: "schedutil",
                littleMinFreq = prefs[intPreferencesKey("l_min")] ?: 0,
                littleMaxFreq = prefs[intPreferencesKey("l_max")] ?: 0,
                bigGovernor = prefs[stringPreferencesKey("b_gov")] ?: "schedutil",
                bigMinFreq = prefs[intPreferencesKey("b_min")] ?: 0,
                bigMaxFreq = prefs[intPreferencesKey("b_max")] ?: 0,
                gpuMinFreq = prefs[intPreferencesKey("g_min")] ?: 0,
                gpuMaxFreq = prefs[intPreferencesKey("g_max")] ?: 0,
                thermalProfile = prefs[intPreferencesKey("thermal")] ?: 3
            )
        } catch (e: Exception) { IdleProfile() }
    }

    private suspend fun monitor() {
        while (scope.isActive) {
            val pkg = getForeground()
            if (pkg != lastPkg) {
                lastPkg = pkg
                currentForegroundApp = pkg
                val profile = if (pkg.isNotBlank()) {
                    try { repo.getProfile(pkg)?.takeIf { it.isEnabled } } catch (e: Exception) { null }
                } else null

                when {
                    profile != null -> {
                        restoreJob?.cancel()
                        startGameEnforce(profile)
                        isGameActive = true
                    }
                    isGameActive -> {
                        stopEnforce()
                        isGameActive = false
                        restoreJob?.cancel()
                        restoreJob = scope.launch {
                            delay(15_000)
                            // Reload idle dari DataStore sebelum restore
                            currentIdleProfile = loadIdleFromDataStore()
                            Kernel.applyIdle(currentIdleProfile)
                            startIdleEnforce()
                        }
                    }
                }
            }
            delay(2000)
        }
    }

    // Re-apply game profile tiap 5 detik — lawan MIUI daemon
    private fun startGameEnforce(profile: AppProfile) {
        enforceJob?.cancel()
        enforceJob = scope.launch {
            while (isActive) {
                Kernel.applyGame(profile)
                delay(5000)
            }
        }
    }

    // Re-apply idle profile tiap 10 detik — lawan MIUI daemon
    private fun startIdleEnforce() {
        enforceJob?.cancel()
        enforceJob = scope.launch {
            while (isActive) {
                Kernel.applyIdle(currentIdleProfile)
                delay(10_000)
            }
        }
    }

    private fun stopEnforce() { enforceJob?.cancel(); enforceJob = null }

    private fun getForeground(): String {
        return try {
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 3000, now)
            val ev = UsageEvents.Event()
            var p = ""; var t = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED && ev.timeStamp > t) {
                    t = ev.timeStamp; p = ev.packageName ?: ""
                }
            }
            if (p.isValid()) p else usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
                ?.maxByOrNull { it.lastTimeUsed }?.packageName?.takeIf { it.isValid() } ?: ""
        } catch (e: Exception) { "" }
    }

    private fun String.isValid() = length > 5 && contains(".") &&
        !startsWith("com.android") && !startsWith("android") &&
        !startsWith("com.miui") && !startsWith("com.xiaomi") &&
        !startsWith("com.mi.") && this != "com.termux" && this != "com.riodev.kernelperf"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isGameActive) scope.launch {
            currentIdleProfile = loadIdleFromDataStore()
            Kernel.applyIdle(currentIdleProfile)
            startIdleEnforce()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        onIdleUpdated = null
        enforceJob?.cancel()
        restoreJob?.cancel()
        scope.cancel()
    }
}

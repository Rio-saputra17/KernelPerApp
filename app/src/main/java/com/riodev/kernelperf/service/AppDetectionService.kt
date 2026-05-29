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
    private var currentGameProfile: AppProfile? = null

    // Enforce hanya sebagai safety net karena daemon sudah dimatikan saat gaming
    // Jauh lebih jarang dari sebelumnya (5s → 60s)
    private val GAME_ENFORCE_INTERVAL  = 60_000L   // 1 menit — daemon sudah mati, governor stabil
    private val IDLE_ENFORCE_INTERVAL  = 120_000L  // 2 menit — daemon hidup, hanya safety net
    private val RESTORE_DELAY          = 45_000L   // 45 detik — aman untuk loading screen / pause
    private val MONITOR_INTERVAL_GAME  = 4_000L    // Cek foreground tiap 4s saat game
    private val MONITOR_INTERVAL_IDLE  = 2_000L    // Cek foreground tiap 2s saat idle

    companion object {
        var isRunning = false
        var currentForegroundApp = ""
        var onIdleUpdated: ((IdleProfile) -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(applicationContext)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        isRunning = true

        onIdleUpdated = { p ->
            currentIdleProfile = p
            if (!isGameActive) {
                scope.launch {
                    Kernel.applyIdle(p) // applyIdle sudah handle restart daemon
                    startIdleEnforce()
                }
            }
        }

        scope.launch {
            currentIdleProfile = loadIdleFromDataStore()
            delay(2000)
            Kernel.applyIdle(currentIdleProfile)
            startIdleEnforce()
            monitor()
        }
    }

    private suspend fun loadIdleFromDataStore(): IdleProfile {
        return try {
            val prefs = applicationContext.ds.data.first()
            IdleProfile(
                littleGovernor = prefs[stringPreferencesKey("l_gov")] ?: "schedutil",
                littleMinFreq  = prefs[intPreferencesKey("l_min")]    ?: 0,
                littleMaxFreq  = prefs[intPreferencesKey("l_max")]    ?: 0,
                bigGovernor    = prefs[stringPreferencesKey("b_gov")] ?: "schedutil",
                bigMinFreq     = prefs[intPreferencesKey("b_min")]    ?: 0,
                bigMaxFreq     = prefs[intPreferencesKey("b_max")]    ?: 0,
                gpuMinFreq     = prefs[intPreferencesKey("g_min")]    ?: 0,
                gpuMaxFreq     = prefs[intPreferencesKey("g_max")]    ?: 0,
                thermalProfile = prefs[intPreferencesKey("thermal")]  ?: 3
            )
        } catch (e: Exception) { IdleProfile() }
    }

    private suspend fun monitor() {
        while (scope.isActive) {
            val pkg = getForeground()

            if (pkg != lastPkg && pkg.isNotBlank()) {
                lastPkg = pkg
                currentForegroundApp = pkg

                val profile = try {
                    repo.getProfile(pkg)?.takeIf { it.isEnabled }
                } catch (e: Exception) { null }

                when {
                    // Masuk game
                    profile != null -> {
                        restoreJob?.cancel()
                        val sameGame = currentGameProfile?.packageName == profile.packageName
                        currentGameProfile = profile
                        if (!isGameActive || !sameGame) {
                            stopEnforce()
                            // applyGame sudah matikan daemon di dalamnya
                            Kernel.applyGame(profile)
                            isGameActive = true
                            startGameEnforce(profile)
                        }
                    }
                    // Keluar game
                    isGameActive -> {
                        stopEnforce()
                        isGameActive = false
                        currentGameProfile = null
                        restoreJob?.cancel()
                        restoreJob = scope.launch {
                            delay(RESTORE_DELAY)
                            currentIdleProfile = loadIdleFromDataStore()
                            // applyIdle sudah restart daemon di dalamnya
                            Kernel.applyIdle(currentIdleProfile)
                            startIdleEnforce()
                        }
                    }
                }
            }

            delay(if (isGameActive) MONITOR_INTERVAL_GAME else MONITOR_INTERVAL_IDLE)
        }
    }

    // Safety net enforce — daemon sudah mati, governor sangat stabil
    // Hanya jaga kalau ada proses sistem lain yang nulis governor
    private fun startGameEnforce(profile: AppProfile) {
        enforceJob?.cancel()
        enforceJob = scope.launch {
            while (isActive) {
                delay(GAME_ENFORCE_INTERVAL)
                // Cek foreground dulu — jangan apply kalau game sudah ditutup
                if (isGameActive && getForeground() == profile.packageName) {
                    // Tidak perlu stop daemon lagi, sudah mati — hanya tulis governor
                    Kernel.applyGame(profile)
                }
            }
        }
    }

    // Safety net enforce saat idle
    private fun startIdleEnforce() {
        enforceJob?.cancel()
        enforceJob = scope.launch {
            while (isActive) {
                delay(IDLE_ENFORCE_INTERVAL)
                if (!isGameActive) {
                    Kernel.applyIdle(currentIdleProfile)
                }
            }
        }
    }

    private fun stopEnforce() {
        enforceJob?.cancel()
        enforceJob = null
    }

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
            if (p.isValid()) p
            else usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
                ?.maxByOrNull { it.lastTimeUsed }?.packageName?.takeIf { it.isValid() } ?: ""
        } catch (e: Exception) { "" }
    }

    private fun String.isValid() = length > 5 && contains(".") &&
        !startsWith("com.android") && !startsWith("android") &&
        !startsWith("com.miui")    && !startsWith("com.xiaomi") &&
        !startsWith("com.mi.")     && this != "com.termux" && this != "com.riodev.kernelperf"

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
        // Pastikan daemon hidup kembali saat service mati
        Kernel.startPerfDaemons()
    }
}

package com.riodev.kernelperf.service

import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.IdleProfile
import com.riodev.kernelperf.data.repository.AppRepository
import com.riodev.kernelperf.root.Kernel
import kotlinx.coroutines.*

class AppDetectionService : Service() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repo: AppRepository
    private lateinit var usm: UsageStatsManager
    private var lastPkg = ""
    private var isGameActive = false
    private var restoreJob: Job? = null
    private var enforceJob: Job? = null  // Loop apply saat game aktif

    companion object {
        var isRunning = false
        var currentForegroundApp = ""
        var idleProfile = IdleProfile()
        fun updateIdle(p: IdleProfile) { idleProfile = p }
    }

    override fun onCreate() {
        super.onCreate()
        repo = AppRepository(applicationContext)
        usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        isRunning = true
        scope.launch {
            delay(2000)
            Kernel.applyIdle(idleProfile)
            monitor()
        }
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
                        // Game dibuka
                        restoreJob?.cancel(); restoreJob = null
                        startEnforceLoop(profile)
                        isGameActive = true
                    }
                    isGameActive -> {
                        // Game ditutup
                        stopEnforceLoop()
                        isGameActive = false
                        restoreJob?.cancel()
                        restoreJob = scope.launch {
                            delay(15_000)
                            Kernel.applyIdle(idleProfile)
                            // Enforce idle juga supaya tidak di-override
                            startIdleEnforce()
                        }
                    }
                }
            }
            delay(2000)
        }
    }

    // Loop re-apply game profile setiap 5 detik
    // Ini mencegah MIUI thermal daemon override governor
    private fun startEnforceLoop(profile: AppProfile) {
        enforceJob?.cancel()
        enforceJob = scope.launch {
            while (isActive) {
                Kernel.applyGame(profile)
                delay(5000)
            }
        }
    }

    // Loop re-apply idle setiap 10 detik saat tidak ada game
    private fun startIdleEnforce() {
        enforceJob?.cancel()
        enforceJob = scope.launch {
            while (isActive) {
                Kernel.applyIdle(idleProfile)
                delay(10_000)
            }
        }
    }

    private fun stopEnforceLoop() {
        enforceJob?.cancel()
        enforceJob = null
    }

    private fun getForeground(): String {
        return try {
            val now = System.currentTimeMillis()
            val events = usm.queryEvents(now - 3000, now)
            val ev = UsageEvents.Event()
            var lastPkg = ""; var lastTime = 0L
            while (events.hasNextEvent()) {
                events.getNextEvent(ev)
                if (ev.eventType == UsageEvents.Event.ACTIVITY_RESUMED && ev.timeStamp > lastTime) {
                    lastTime = ev.timeStamp; lastPkg = ev.packageName ?: ""
                }
            }
            if (lastPkg.isValid()) lastPkg else {
                usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 10000, now)
                    ?.maxByOrNull { it.lastTimeUsed }?.packageName?.takeIf { it.isValid() } ?: ""
            }
        } catch (e: Exception) { "" }
    }

    private fun String.isValid() = length > 5 && contains(".") &&
        !startsWith("com.android") && !startsWith("android") &&
        !startsWith("com.miui") && !startsWith("com.xiaomi") &&
        !startsWith("com.mi.") && this != "com.termux" && this != "com.riodev.kernelperf"

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Restart idle enforce kalau service restart
        if (!isGameActive) scope.launch { startIdleEnforce() }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        enforceJob?.cancel()
        restoreJob?.cancel()
        scope.cancel()
    }
}

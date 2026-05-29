package com.riodev.kernelperf.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riodev.kernelperf.data.model.*
import com.riodev.kernelperf.data.repository.AppRepository
import com.riodev.kernelperf.root.Kernel
import com.riodev.kernelperf.service.AppDetectionService
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val Context.ds by preferencesDataStore("idle")

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppRepository(app)
    private val ds = app.ds

    // DataStore keys
    private val K = object {
        val LG = stringPreferencesKey("l_gov")
        val LN = intPreferencesKey("l_min")
        val LX = intPreferencesKey("l_max")
        val BG = stringPreferencesKey("b_gov")
        val BN = intPreferencesKey("b_min")
        val BX = intPreferencesKey("b_max")
        val GN = intPreferencesKey("g_min")
        val GX = intPreferencesKey("g_max")
        val TH = intPreferencesKey("thermal")
    }

    // ── Idle profile ──────────────────────────────────────────
    val idleProfile: StateFlow<IdleProfile> = ds.data.map { p ->
        IdleProfile(
            littleGovernor = p[K.LG] ?: "schedutil",
            littleMinFreq = p[K.LN] ?: 0,
            littleMaxFreq = p[K.LX] ?: 0,
            bigGovernor = p[K.BG] ?: "schedutil",
            bigMinFreq = p[K.BN] ?: 0,
            bigMaxFreq = p[K.BX] ?: 0,
            gpuMinFreq = p[K.GN] ?: 0,
            gpuMaxFreq = p[K.GX] ?: 0,
            thermalProfile = p[K.TH] ?: 3
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), IdleProfile())

    // ── App profiles ──────────────────────────────────────────
    val profiles: StateFlow<List<AppProfile>> = repo.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Installed apps ────────────────────────────────────────
    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps
    private val _loadingApps = MutableStateFlow(false)
    val loadingApps: StateFlow<Boolean> = _loadingApps
    private val _search = MutableStateFlow("")
    val search: StateFlow<String> = _search

    val filteredApps = combine(_apps, _search) { apps, q ->
        if (q.isBlank()) apps else apps.filter { it.appName.contains(q, true) || it.packageName.contains(q, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Kernel status (dashboard) ─────────────────────────────
    private val _status = MutableStateFlow(KernelStatus())
    val status: StateFlow<KernelStatus> = _status
    private var lastStatus = KernelStatus()

    // ── Device info ───────────────────────────────────────────
    private val _device = MutableStateFlow(DeviceInfo())
    val device: StateFlow<DeviceInfo> = _device

    // ── Root & service ────────────────────────────────────────
    private val _rooted = MutableStateFlow(false)
    val rooted: StateFlow<Boolean> = _rooted

    val activeApp: StateFlow<String> = flow {
        while (true) { emit(AppDetectionService.currentForegroundApp); delay(2000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    // ── Kernel options (cached once) ──────────────────────────
    private val _littleGovs = MutableStateFlow<List<String>>(emptyList())
    val littleGovs: StateFlow<List<String>> = _littleGovs
    private val _bigGovs = MutableStateFlow<List<String>>(emptyList())
    val bigGovs: StateFlow<List<String>> = _bigGovs
    private val _littleFreqs = MutableStateFlow<List<Int>>(emptyList())
    val littleFreqs: StateFlow<List<Int>> = _littleFreqs
    private val _bigFreqs = MutableStateFlow<List<Int>>(emptyList())
    val bigFreqs: StateFlow<List<Int>> = _bigFreqs
    private val _gpuFreqs = MutableStateFlow<List<Long>>(emptyList())
    val gpuFreqs: StateFlow<List<Long>> = _gpuFreqs
    private val _gpuGovs = MutableStateFlow<List<String>>(emptyList())
    val gpuGovs: StateFlow<List<String>> = _gpuGovs

    init {
        viewModelScope.launch {
            try { _rooted.value = Shell.getShell().isRoot } catch (e: Exception) { _rooted.value = false }
            _device.value = Kernel.getDeviceInfo()
            if (_rooted.value) {
                loadKernelOptions()
                startMonitor()
            }
            loadApps()
        }
    }

    private fun startMonitor() {
        viewModelScope.launch {
            while (true) {
                try {
                    val new = Kernel.getLiveStatus(getApplication())
                    if (new != lastStatus) { lastStatus = new; _status.value = new }
                } catch (e: Exception) { }
                delay(2000)
            }
        }
    }

    private suspend fun loadKernelOptions() {
        _littleGovs.value = Kernel.getAvailableGovernors("policy0")
        _bigGovs.value = Kernel.getAvailableGovernors("policy4").ifEmpty { _littleGovs.value }
        _littleFreqs.value = Kernel.getAvailableFreqs("policy0")
        _bigFreqs.value = Kernel.getAvailableFreqs("policy4").ifEmpty { _littleFreqs.value }
        _gpuFreqs.value = Kernel.getAvailableGpuFreqs()
        _gpuGovs.value = Kernel.getAvailableGpuGovernors()
    }

    fun loadApps() {
        viewModelScope.launch {
            _loadingApps.value = true
            try { _apps.value = repo.getInstalledApps() } catch (e: Exception) { }
            _loadingApps.value = false
        }
    }

    fun setSearch(q: String) { _search.value = q }

    fun saveIdle(p: IdleProfile) {
        viewModelScope.launch {
            ds.edit { pr ->
                pr[K.LG] = p.littleGovernor; pr[K.LN] = p.littleMinFreq; pr[K.LX] = p.littleMaxFreq
                pr[K.BG] = p.bigGovernor;    pr[K.BN] = p.bigMinFreq;    pr[K.BX] = p.bigMaxFreq
                pr[K.GN] = p.gpuMinFreq;     pr[K.GX] = p.gpuMaxFreq;   pr[K.TH] = p.thermalProfile
            }
            AppDetectionService.onIdleUpdated?.invoke(p)
        }
    }

    fun saveProfile(p: AppProfile) { viewModelScope.launch { repo.saveProfile(p); loadApps() } }
    fun deleteProfile(pkg: String) { viewModelScope.launch { repo.deleteProfile(pkg); loadApps() } }
    suspend fun getProfile(pkg: String): AppProfile? = repo.getProfile(pkg)
}

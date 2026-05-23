package com.riodev.kernelperf.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riodev.kernelperf.data.model.*
import com.riodev.kernelperf.data.repository.AppRepository
import com.riodev.kernelperf.root.RootUtils
import com.riodev.kernelperf.service.AppDetectionService
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

private val Context.dataStore by preferencesDataStore("default_profile")

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = AppRepository(app)
    private val ds = app.dataStore

    private val K_GOV = stringPreferencesKey("gov")
    private val K_MIN = stringPreferencesKey("min")
    private val K_MAX = stringPreferencesKey("max")
    private val K_GPU = stringPreferencesKey("gpu_gov")
    private val K_GPU_MIN = stringPreferencesKey("gpu_min")
    private val K_GPU_MAX = stringPreferencesKey("gpu_max")
    private val K_IO = stringPreferencesKey("io")
    private val K_THERMAL = stringPreferencesKey("thermal")

    val profiles: StateFlow<List<AppProfile>> = repository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val defaultProfile: StateFlow<DefaultProfile> = ds.data.map { p ->
        DefaultProfile(
            cpuGovernor = p[K_GOV] ?: "schedutil",
            cpuMinFreq = p[K_MIN]?.toIntOrNull() ?: 0,
            cpuMaxFreq = p[K_MAX]?.toIntOrNull() ?: 0,
            gpuGovernor = p[K_GPU] ?: "default",
            gpuMinFreq = p[K_GPU_MIN]?.toIntOrNull() ?: 0,
            gpuMaxFreq = p[K_GPU_MAX]?.toIntOrNull() ?: 0,
            ioScheduler = p[K_IO] ?: "default",
            thermalProfile = p[K_THERMAL]?.toIntOrNull() ?: 0
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DefaultProfile())

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps

    private val _kernelStatus = MutableStateFlow(KernelStatus())
    val kernelStatus: StateFlow<KernelStatus> = _kernelStatus

    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo

    // Little cluster options
    private val _governors = MutableStateFlow<List<String>>(emptyList())
    val governors: StateFlow<List<String>> = _governors

    private val _frequencies = MutableStateFlow<List<Int>>(emptyList())
    val frequencies: StateFlow<List<Int>> = _frequencies

    // Big cluster options
    private val _bigGovernors = MutableStateFlow<List<String>>(emptyList())
    val bigGovernors: StateFlow<List<String>> = _bigGovernors

    private val _bigFrequencies = MutableStateFlow<List<Int>>(emptyList())
    val bigFrequencies: StateFlow<List<Int>> = _bigFrequencies

    private val _schedulers = MutableStateFlow<List<String>>(emptyList())
    val schedulers: StateFlow<List<String>> = _schedulers

    private val _gpuGovernors = MutableStateFlow<List<String>>(emptyList())
    val gpuGovernors: StateFlow<List<String>> = _gpuGovernors

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val filteredApps: StateFlow<List<InstalledApp>> = combine(_installedApps, _searchQuery) { apps, q ->
        if (q.isBlank()) apps else apps.filter { it.appName.contains(q, true) || it.packageName.contains(q, true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeProfileApp: StateFlow<String> = flow {
        while (true) { emit(AppDetectionService.currentForegroundApp); delay(1000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    init {
        viewModelScope.launch {
            try { _isRooted.value = Shell.getShell().isRoot } catch (e: Exception) { _isRooted.value = false }
            _deviceInfo.value = RootUtils.getDeviceInfo()
            if (_isRooted.value) { loadOptions(); startMonitor() }
            loadApps()
        }
    }

    private fun startMonitor() {
        viewModelScope.launch {
            val little = RootUtils.getLittlePolicy()
            val big = RootUtils.getBigPolicy()
            while (true) {
                try {
                    _kernelStatus.value = KernelStatus(
                        littleGovernor = RootUtils.getGovernor(little),
                        littleMinFreq = RootUtils.getMinFreq(little),
                        littleMaxFreq = RootUtils.getMaxFreq(little),
                        littleCurFreq = RootUtils.getCurFreq(little),
                        bigGovernor = RootUtils.getGovernor(big),
                        bigMinFreq = RootUtils.getMinFreq(big),
                        bigMaxFreq = RootUtils.getMaxFreq(big),
                        bigCurFreq = RootUtils.getCurFreq(big),
                        gpuGovernor = RootUtils.getGpuGovernor(),
                        gpuCurFreq = RootUtils.getGpuCurFreq(),
                        gpuMinFreq = RootUtils.getGpuMinFreq(),
                        gpuMaxFreq = RootUtils.getGpuMaxFreq(),
                        cpuTemp = RootUtils.getCpuTemp(),
                        batteryTemp = RootUtils.getBatteryTemp(),
                        ioScheduler = RootUtils.getCurrentScheduler()
                    )
                } catch (e: Exception) { }
                delay(100)
            }
        }
    }

    private suspend fun loadOptions() {
        try {
            _governors.value = RootUtils.getAvailableGovernors("policy0")
            _frequencies.value = RootUtils.getAvailableFrequencies("policy0")
            _bigGovernors.value = RootUtils.getAvailableGovernors("policy4").ifEmpty { _governors.value }
            _bigFrequencies.value = RootUtils.getAvailableFrequencies("policy4").ifEmpty { _frequencies.value }
            _schedulers.value = RootUtils.getAvailableSchedulers()
            _gpuGovernors.value = RootUtils.getAvailableGpuGovernors()
        } catch (e: Exception) { }
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            try { _installedApps.value = repository.getInstalledApps() } catch (e: Exception) { _installedApps.value = emptyList() }
            _isLoadingApps.value = false
        }
    }

    fun setSearchQuery(q: String) { _searchQuery.value = q }

    fun saveProfile(profile: AppProfile) {
        viewModelScope.launch { repository.saveProfile(profile); loadApps() }
    }

    fun deleteProfile(pkg: String) {
        viewModelScope.launch { repository.deleteProfile(pkg); loadApps() }
    }

    suspend fun getProfile(pkg: String): AppProfile? = repository.getProfile(pkg)

    fun saveDefaultProfile(gov: String, minFreq: Int, maxFreq: Int, gpuGov: String, gpuMin: Int, gpuMax: Int, io: String, thermal: Int) {
        viewModelScope.launch {
            ds.edit { p ->
                p[K_GOV] = gov; p[K_MIN] = minFreq.toString(); p[K_MAX] = maxFreq.toString()
                p[K_GPU] = gpuGov; p[K_GPU_MIN] = gpuMin.toString(); p[K_GPU_MAX] = gpuMax.toString()
                p[K_IO] = io; p[K_THERMAL] = thermal.toString()
            }
            AppDetectionService.updateDefaultProfile(gov, minFreq, maxFreq, gpuGov, io)
        }
    }
}

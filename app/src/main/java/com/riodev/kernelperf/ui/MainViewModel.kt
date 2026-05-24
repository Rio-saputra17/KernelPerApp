package com.riodev.kernelperf.ui

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.DefaultProfile
import com.riodev.kernelperf.data.model.KernelStatus
import com.riodev.kernelperf.data.model.DeviceInfo
import com.riodev.kernelperf.data.model.InstalledApp
import com.riodev.kernelperf.data.repository.AppRepository
import com.riodev.kernelperf.root.RootUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

private val Context.dataStore by preferencesDataStore("kernel_prefs")

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AppRepository(application)

    private val _kernelStatus = MutableStateFlow(KernelStatus())
    val kernelStatus: StateFlow<KernelStatus> = _kernelStatus.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val filteredApps: StateFlow<List<InstalledApp>> = combine(_installedApps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter {
            it.appName.contains(query, ignoreCase = true) ||
            it.packageName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _appProfiles = MutableStateFlow<List<AppProfile>>(emptyList())
    val appProfiles: StateFlow<List<AppProfile>> = _appProfiles.asStateFlow()

    // alias dipakai HomeScreen
    val profiles: StateFlow<List<AppProfile>> = _appProfiles

    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted.asStateFlow()

    private val _defaultProfile = MutableStateFlow(DefaultProfile())
    val defaultProfile: StateFlow<DefaultProfile> = _defaultProfile.asStateFlow()

    private val _activeProfileApp = MutableStateFlow("")
    val activeProfileApp: StateFlow<String> = _activeProfileApp.asStateFlow()

    // Kernel option lists — dipakai ProfileEditorScreen
    private val _governors = MutableStateFlow<List<String>>(emptyList())
    val governors: StateFlow<List<String>> = _governors.asStateFlow()

    private val _frequencies = MutableStateFlow<List<Int>>(emptyList())
    val frequencies: StateFlow<List<Int>> = _frequencies.asStateFlow()

    private val _schedulers = MutableStateFlow<List<String>>(emptyList())
    val schedulers: StateFlow<List<String>> = _schedulers.asStateFlow()

    private val _gpuGovernors = MutableStateFlow<List<String>>(emptyList())
    val gpuGovernors: StateFlow<List<String>> = _gpuGovernors.asStateFlow()

    private val DS_DEFAULT_PROFILE = stringPreferencesKey("default_profile_json")

    init {
        viewModelScope.launch {
            _isRooted.value = RootUtils.isRooted()
            loadDeviceInfo()
            loadKernelOptions()
            loadInstalledApps()
            loadDefaultProfile()
            repo.getAllProfiles().collect { _appProfiles.value = it }
        }
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch {
            while (true) {
                _kernelStatus.value = RootUtils.getKernelStatus()
                delay(1500)
            }
        }
    }

    private suspend fun loadDeviceInfo() {
        _deviceInfo.value = RootUtils.getDeviceInfo()
    }

    private fun loadKernelOptions() {
        viewModelScope.launch {
            _governors.value = RootUtils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors")
                .split(" ").filter { it.isNotBlank() }
            _frequencies.value = RootUtils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies")
                .split(" ").filter { it.isNotBlank() }.mapNotNull { it.trim().toIntOrNull() }
            _schedulers.value = RootUtils.readFile("/sys/block/sda/queue/scheduler")
                .replace(Regex("[\\[\\]]"), "").split(" ").filter { it.isNotBlank() }
            _gpuGovernors.value = RootUtils.readFile("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors")
                .split(" ").filter { it.isNotBlank() }
        }
    }

    suspend fun loadInstalledApps() {
        _isLoadingApps.value = true
        _installedApps.value = repo.getInstalledApps()
        _isLoadingApps.value = false
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setActiveApp(packageName: String) {
        _activeProfileApp.value = packageName
    }

    fun saveProfile(profile: AppProfile) {
        viewModelScope.launch { repo.saveProfile(profile) }
    }

    fun deleteProfile(packageName: String) {
        viewModelScope.launch { repo.deleteProfile(packageName) }
    }

    suspend fun getProfile(packageName: String): AppProfile? = repo.getProfile(packageName)

    fun applyProfile(packageName: String) {
        viewModelScope.launch { repo.applyProfile(packageName) }
    }

    fun saveDefaultProfile(
        cpuGovernor: String,
        cpuMinFreq: Int,
        cpuMaxFreq: Int,
        gpuGovernor: String,
        gpuMinFreq: Int,
        gpuMaxFreq: Int,
        ioScheduler: String,
        thermalProfile: Int
    ) {
        viewModelScope.launch {
            val profile = DefaultProfile(
                cpuGovernor = cpuGovernor,
                cpuMinFreq = cpuMinFreq,
                cpuMaxFreq = cpuMaxFreq,
                gpuGovernor = gpuGovernor,
                gpuMinFreq = gpuMinFreq,
                gpuMaxFreq = gpuMaxFreq,
                ioScheduler = ioScheduler,
                thermalProfile = thermalProfile
            )
            _defaultProfile.value = profile
            getApplication<Application>().dataStore.edit { prefs ->
                prefs[DS_DEFAULT_PROFILE] = Json.encodeToString(profile)
            }
        }
    }

    private suspend fun loadDefaultProfile() {
        try {
            val prefs = getApplication<Application>().dataStore.data.first()
            val json = prefs[DS_DEFAULT_PROFILE]
            if (!json.isNullOrEmpty()) {
                _defaultProfile.value = Json.decodeFromString(json)
            }
        } catch (_: Exception) {}
    }
}

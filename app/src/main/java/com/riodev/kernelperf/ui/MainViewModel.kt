package com.riodev.kernelperf.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.InstalledApp
import com.riodev.kernelperf.data.model.KernelStatus
import com.riodev.kernelperf.data.repository.AppRepository
import com.riodev.kernelperf.service.AppDetectionService
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = AppRepository(app)
    val profiles: StateFlow<List<AppProfile>> = repository.getAllProfiles()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val installedApps: StateFlow<List<InstalledApp>> = _installedApps
    private val _isLoadingApps = MutableStateFlow(false)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps
    private val _kernelStatus = MutableStateFlow(KernelStatus())
    val kernelStatus: StateFlow<KernelStatus> = _kernelStatus
    private val _isRooted = MutableStateFlow(false)
    val isRooted: StateFlow<Boolean> = _isRooted
    private val _governors = MutableStateFlow<List<String>>(emptyList())
    val governors: StateFlow<List<String>> = _governors
    private val _frequencies = MutableStateFlow<List<Int>>(emptyList())
    val frequencies: StateFlow<List<Int>> = _frequencies
    private val _schedulers = MutableStateFlow<List<String>>(emptyList())
    val schedulers: StateFlow<List<String>> = _schedulers
    private val _gpuGovernors = MutableStateFlow<List<String>>(emptyList())
    val gpuGovernors: StateFlow<List<String>> = _gpuGovernors
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery
    val filteredApps: StateFlow<List<InstalledApp>> = combine(_installedApps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter { it.appName.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeProfileApp: StateFlow<String> = flow {
        while (true) { emit(AppDetectionService.currentForegroundApp); delay(1000) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")
    init {
        viewModelScope.launch {
            try { _isRooted.value = Shell.getShell().isRoot } catch (e: Exception) { _isRooted.value = false }
            if (_isRooted.value) { loadKernelOptions(); startKernelMonitor() }
            loadInstalledApps()
        }
    }
    fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoadingApps.value = true
            try { _installedApps.value = repository.getInstalledApps() } catch (e: Exception) { _installedApps.value = emptyList() }
            _isLoadingApps.value = false
        }
    }
    private fun startKernelMonitor() {
        viewModelScope.launch {
            while (true) {
                try { _kernelStatus.value = repository.getKernelStatus() } catch (e: Exception) { }
                delay(2000)
            }
        }
    }
    private suspend fun loadKernelOptions() {
        try {
            _governors.value = repository.getAvailableGovernors()
            _frequencies.value = repository.getAvailableFrequencies()
            _schedulers.value = repository.getAvailableSchedulers()
            _gpuGovernors.value = repository.getAvailableGpuGovernors()
        } catch (e: Exception) { }
    }
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun saveProfile(profile: AppProfile) {
        viewModelScope.launch { repository.saveProfile(profile); loadInstalledApps() }
    }
    fun deleteProfile(packageName: String) {
        viewModelScope.launch { repository.deleteProfile(packageName); loadInstalledApps() }
    }
    suspend fun getProfile(packageName: String): AppProfile? = repository.getProfile(packageName)
}

package com.riodev.kernelperf.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.riodev.kernelperf.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppDetectionService : AccessibilityService() {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: AppRepository
    private var lastPackage: String = ""

    companion object {
        var isRunning = false
        var currentForegroundApp: String = ""
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        repository = AppRepository(applicationContext)
        isRunning = true

        serviceInfo = serviceInfo.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            notificationTimeout = 100
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val packageName = event.packageName?.toString() ?: return

        // Skip system UI dan app kita sendiri
        if (packageName == "com.riodev.kernelperf" ||
            packageName == "com.android.systemui" ||
            packageName == lastPackage) return

        lastPackage = packageName
        currentForegroundApp = packageName

        scope.launch {
            repository.applyProfile(packageName)
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
    }
}

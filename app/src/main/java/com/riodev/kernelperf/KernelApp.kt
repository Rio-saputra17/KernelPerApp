package com.riodev.kernelperf

import android.app.Application
import android.content.Intent
import com.riodev.kernelperf.root.RootUtils
import com.riodev.kernelperf.service.AppDetectionService
import com.topjohnwu.superuser.Shell

class KernelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(Shell.Builder.create().setFlags(Shell.FLAG_REDIRECT_STDERR).setTimeout(10))
        startService(Intent(this, AppDetectionService::class.java))
    }
}

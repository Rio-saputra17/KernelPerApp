package com.riodev.kernelperf

import android.app.Application
import com.riodev.kernelperf.root.RootUtils
import com.topjohnwu.superuser.Shell

class KernelApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Init libsu sebelum shell dipakai
        Shell.enableVerboseLogging = false
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
    }
}

package com.riodev.kernelperf.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.riodev.kernelperf.root.Kernel

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Kernel.initShell()
            context.startService(Intent(context, AppDetectionService::class.java))
        }
    }
}

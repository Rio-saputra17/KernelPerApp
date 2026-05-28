package com.riodev.kernelperf.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riodev.kernelperf.data.model.KernelStatus
import com.riodev.kernelperf.service.AppDetectionService
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*

@Composable
fun HomeScreen(vm: MainViewModel) {
    val status by vm.status.collectAsState()
    val rooted by vm.rooted.collectAsState()
    val profiles by vm.profiles.collectAsState()
    val activeApp by vm.activeApp.collectAsState()
    val device by vm.device.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(BgDark)
            .verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("KernelPerf", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Cyan)
                Text("Per-App Kernel Manager", fontSize = 11.sp, color = TextSec)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp))
                .background((if (rooted) Green else Red).copy(alpha = 0.15f))
                .padding(horizontal = 10.dp, vertical = 5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(if (rooted) Icons.Default.VerifiedUser else Icons.Default.Warning,
                        null, tint = if (rooted) Green else Red, modifier = Modifier.size(12.dp))
                    Text(if (rooted) "Rooted" else "No Root", fontSize = 11.sp,
                        color = if (rooted) Green else Red, fontWeight = FontWeight.Medium)
                }
            }
        }

        // Device info
        InfoCard(device.model, device.chipset, device.kernel, device.totalRam)

        // Active game banner
        if (activeApp.isNotBlank() && AppDetectionService.isRunning) {
            val hasProfile = profiles.any { it.packageName == activeApp && it.isEnabled }
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(CyanDark.copy(alpha = 0.3f))
                    .border(1.dp, Cyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.SportsEsports, null, tint = Cyan, modifier = Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("App Aktif", fontSize = 10.sp, color = TextSec)
                    Text(activeApp.substringAfterLast("."), fontSize = 13.sp,
                        color = TextPri, fontWeight = FontWeight.Medium)
                }
                if (hasProfile) Text("Game Mode", fontSize = 10.sp, color = Green,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Green.copy(0.1f)).padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }

        // Service status
        val svcColor = if (AppDetectionService.isRunning) Green else Orange
        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Card).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(svcColor))
            Text(if (AppDetectionService.isRunning) "Service Aktif" else "Service Nonaktif — Restart app",
                fontSize = 12.sp, color = if (AppDetectionService.isRunning) TextPri else TextSec,
                modifier = Modifier.weight(1f))
            Icon(if (AppDetectionService.isRunning) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                null, tint = svcColor, modifier = Modifier.size(16.dp))
        }

        // Live stats
        Text("• CPU", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cyan)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Modifier.weight(1f), "Little Cluster", status.littleGovernor, status.littleCurFreq, Icons.Default.Tune)
            StatCard(Modifier.weight(1f), "Big Cluster", status.bigGovernor, status.bigCurFreq, Icons.Default.Tune)
        }

        Text("• GPU", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cyan)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Modifier.weight(1f), "GPU Governor", status.gpuGovernor, "", Icons.Default.Memory)
            StatCard(Modifier.weight(1f), "GPU Freq", status.gpuCurFreq, "", Icons.Default.Speed)
        }

        Text("• Thermal & Battery", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cyan)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard(Modifier.weight(1f), "CPU Temp", status.cpuTemp, "", Icons.Default.Thermostat,
                color = tempColor(status.cpuTemp))
            StatCard(Modifier.weight(1f), "Battery",
                "${status.batteryLevel} · ${status.batteryTemp}",
                status.batteryStatus, Icons.Default.BatteryChargingFull,
                color = battColor(status.batteryStatus))
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun InfoCard(model: String, chipset: String, kernel: String, ram: String) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(Card).border(1.dp, CardBorder, RoundedCornerShape(10.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Default.PhoneAndroid, null, tint = Cyan, modifier = Modifier.size(14.dp))
            Text("Device Info", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextPri)
        }
        HorizontalDivider(color = CardBorder, modifier = Modifier.padding(vertical = 2.dp))
        IRow("Model", model)
        IRow("Chipset", chipset)
        IRow("Kernel", kernel, small = true)
        IRow("RAM", ram)
    }
}

@Composable
fun IRow(label: String, value: String, small: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = TextSec)
        Text(value, fontSize = if (small) 9.sp else 11.sp, color = TextPri,
            fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, false).padding(start = 8.dp), maxLines = 1)
    }
}

@Composable
fun StatCard(modifier: Modifier, label: String, value: String, sub: String, icon: ImageVector, color: Color = Cyan) {
    Column(modifier = modifier.clip(RoundedCornerShape(10.dp)).background(Card)
        .border(1.dp, CardBorder, RoundedCornerShape(10.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = TextSec, modifier = Modifier.size(11.dp))
            Text(label, fontSize = 10.sp, color = TextSec)
        }
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
        if (sub.isNotBlank()) Text(sub, fontSize = 10.sp, color = TextSec, maxLines = 1)
    }
}

private fun tempColor(t: String): Color {
    val v = t.replace("°C", "").toIntOrNull() ?: return Green
    return when { v >= 70 -> Red; v >= 55 -> Orange; else -> Green }
}

private fun battColor(status: String): Color = when {
    status.contains("Charging") -> Green
    status.contains("Full") -> Cyan
    else -> Yellow
}

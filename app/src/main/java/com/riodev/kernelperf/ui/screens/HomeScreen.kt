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
import com.riodev.kernelperf.data.model.DeviceInfo
import com.riodev.kernelperf.data.model.KernelStatus
import com.riodev.kernelperf.service.AppDetectionService
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*

@Composable
fun HomeScreen(viewModel: MainViewModel) {
    val kernelStatus by viewModel.kernelStatus.collectAsState()
    val isRooted by viewModel.isRooted.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeApp by viewModel.activeProfileApp.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("KernelPerf", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                Text("Per-App Kernel Manager", fontSize = 11.sp, color = TextSecondary)
            }
            RootBadge(isRooted)
        }

        // Device Info Card
        DeviceInfoCard(deviceInfo)

        // Active App
        if (activeApp.isNotBlank() && AppDetectionService.isRunning) {
            val hasProfile = profiles.any { it.packageName == activeApp && it.isEnabled }
            ActiveBanner(activeApp, hasProfile)
        }

        // Service Status
        ServiceCard()

        // CPU Little Cluster
        SectionLabel("CPU — Little Cluster")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), "Governor", kernelStatus.littleGovernor, Icons.Default.Tune)
            StatTile(Modifier.weight(1f), "Cur Freq", kernelStatus.littleCurFreq, Icons.Default.Speed)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), "Min Freq", kernelStatus.littleMinFreq, Icons.Default.KeyboardArrowDown, color = GreenAccent)
            StatTile(Modifier.weight(1f), "Max Freq", kernelStatus.littleMaxFreq, Icons.Default.KeyboardArrowUp, color = OrangeAccent)
        }

        // CPU Big Cluster
        SectionLabel("CPU — Big Cluster")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), "Governor", kernelStatus.bigGovernor, Icons.Default.Tune)
            StatTile(Modifier.weight(1f), "Cur Freq", kernelStatus.bigCurFreq, Icons.Default.Speed)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), "Min Freq", kernelStatus.bigMinFreq, Icons.Default.KeyboardArrowDown, color = GreenAccent)
            StatTile(Modifier.weight(1f), "Max Freq", kernelStatus.bigMaxFreq, Icons.Default.KeyboardArrowUp, color = OrangeAccent)
        }

        // GPU
        SectionLabel("GPU")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), "GPU Gov", kernelStatus.gpuGovernor, Icons.Default.Memory)
            StatTile(Modifier.weight(1f), "GPU Freq", kernelStatus.gpuCurFreq, Icons.Default.Speed)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), "GPU Min", kernelStatus.gpuMinFreq, Icons.Default.KeyboardArrowDown, color = GreenAccent)
            StatTile(Modifier.weight(1f), "GPU Max", kernelStatus.gpuMaxFreq, Icons.Default.KeyboardArrowUp, color = OrangeAccent)
        }

        // Thermal & I/O
        SectionLabel("Thermal & I/O")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), "CPU Temp", kernelStatus.cpuTemp, Icons.Default.Thermostat, color = tempColor(kernelStatus.cpuTemp))
            StatTile(Modifier.weight(1f), "Batt Temp", kernelStatus.batteryTemp, Icons.Default.BatteryChargingFull, color = tempColor(kernelStatus.batteryTemp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(Modifier.weight(1f), "I/O Sched", kernelStatus.ioScheduler, Icons.Default.Storage)
            StatTile(Modifier.weight(1f), "Profil Aktif", "${profiles.count { it.isEnabled }}", Icons.Default.Apps, color = Cyan400)
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun DeviceInfoCard(info: DeviceInfo) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.PhoneAndroid, null, tint = Cyan400, modifier = Modifier.size(18.dp))
            Text("Device Info", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        }
        HorizontalDivider(color = DarkCardElevated, modifier = Modifier.padding(vertical = 2.dp))
        DeviceInfoRow("Model", info.model)
        DeviceInfoRow("Chipset", info.chipset)
        DeviceInfoRow("Kernel", info.kernel, small = true)
        DeviceInfoRow("RAM", info.totalRam)
        DeviceInfoRow("Android", info.androidVersion)
    }
}

@Composable
fun DeviceInfoRow(label: String, value: String, small: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(
            value,
            fontSize = if (small) 9.sp else 11.sp,
            color = TextPrimary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false).padding(start = 8.dp),
            maxLines = 2
        )
    }
}

@Composable
fun SectionLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun RootBadge(isRooted: Boolean) {
    val color = if (isRooted) GreenAccent else RedAccent
    Row(
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(if (isRooted) Icons.Default.VerifiedUser else Icons.Default.Warning, null, tint = color, modifier = Modifier.size(13.dp))
        Text(if (isRooted) "Rooted" else "No Root", fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ActiveBanner(pkg: String, hasProfile: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Cyan900.copy(alpha = 0.4f))
            .border(1.dp, Cyan400.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Default.PlayCircle, null, tint = Cyan400, modifier = Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text("App Aktif", fontSize = 10.sp, color = TextSecondary)
            Text(pkg.substringAfterLast("."), fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        if (hasProfile) Text("Profil Aktif", fontSize = 10.sp, color = GreenAccent,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(GreenAccent.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 3.dp))
    }
}

@Composable
fun ServiceCard() {
    val running = AppDetectionService.isRunning
    val color = if (running) GreenAccent else OrangeAccent
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DarkCard).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text("App Detection Service", fontSize = 12.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(if (running) "Aktif — Memantau foreground app" else "Nonaktif — Restart app", fontSize = 10.sp, color = TextSecondary)
        }
        Icon(if (running) Icons.Default.CheckCircle else Icons.Default.ErrorOutline, null, tint = color, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun StatTile(modifier: Modifier, label: String, value: String, icon: ImageVector, color: Color = Cyan400) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(DarkCard).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(12.dp))
            Text(label, fontSize = 10.sp, color = TextSecondary)
        }
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1)
    }
}

private fun tempColor(temp: String): Color {
    val v = temp.replace("°C","").toIntOrNull() ?: return Cyan400
    return when { v >= 70 -> RedAccent; v >= 50 -> OrangeAccent; else -> GreenAccent }
}

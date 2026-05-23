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
import androidx.compose.ui.graphics.Brush
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
fun HomeScreen(viewModel: MainViewModel) {
    val kernelStatus by viewModel.kernelStatus.collectAsState()
    val isRooted by viewModel.isRooted.collectAsState()
    val profiles by viewModel.profiles.collectAsState()
    val activeApp by viewModel.activeProfileApp.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("KernelPerf", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Cyan400)
                Text("Per-App Kernel Manager", fontSize = 12.sp, color = TextSecondary)
            }
            RootBadge(isRooted)
        }

        // Active App Banner - tampil hanya kalau ada
        if (activeApp.isNotBlank() && AppDetectionService.isRunning) {
            val hasProfile = profiles.any { it.packageName == activeApp && it.isEnabled }
            ActiveProfileBanner(activeApp, hasProfile)
        }

        // Service Status
        ServiceStatusCard()

        // Kernel Status
        Text("Kernel Status", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        KernelStatusGrid(kernelStatus)

        // Overview
        Text("Overview", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(Modifier.weight(1f), Icons.Default.Apps, "Profil Aktif", "${profiles.count { it.isEnabled }}", Cyan400)
            StatCard(Modifier.weight(1f), Icons.Default.Storage, "Total Profil", "${profiles.size}", Color(0xFF4FC3F7))
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
fun RootBadge(isRooted: Boolean) {
    val color = if (isRooted) GreenAccent else RedAccent
    val icon = if (isRooted) Icons.Default.VerifiedUser else Icons.Default.Warning
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(if (isRooted) "Rooted" else "No Root", fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ActiveProfileBanner(packageName: String, hasProfile: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.horizontalGradient(listOf(Cyan900.copy(alpha = 0.5f), DarkCard)))
            .border(1.dp, Cyan400.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.PlayCircle, null, tint = Cyan400, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text("App Aktif", fontSize = 11.sp, color = TextSecondary)
            Text(packageName.substringAfterLast("."), fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        if (hasProfile) {
            Text(
                "Profil Aktif",
                fontSize = 10.sp, color = GreenAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GreenAccent.copy(alpha = 0.1f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun ServiceStatusCard() {
    val isRunning = AppDetectionService.isRunning
    val color = if (isRunning) GreenAccent else OrangeAccent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Column(Modifier.weight(1f)) {
            Text("App Detection Service", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(
                if (isRunning) "Aktif — Memantau foreground app" else "Nonaktif — Restart app",
                fontSize = 11.sp, color = TextSecondary
            )
        }
        Icon(
            if (isRunning) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            null, tint = color, modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun KernelStatusGrid(status: KernelStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KernelStatItem(Modifier.weight(1f), "Governor", status.currentGovernor, Icons.Default.Tune)
            KernelStatItem(Modifier.weight(1f), "CPU Freq", status.currentFreq, Icons.Default.Speed)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KernelStatItem(Modifier.weight(1f), "Min/Max", "${status.currentMinFreq}/${status.currentMaxFreq}", Icons.Default.Timeline, small = true)
            KernelStatItem(Modifier.weight(1f), "Suhu CPU", status.cpuTemp, Icons.Default.Thermostat, valueColor = tempColor(status.cpuTemp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KernelStatItem(Modifier.weight(1f), "GPU Gov", status.gpuGovernor, Icons.Default.Memory)
            KernelStatItem(Modifier.weight(1f), "I/O Sched", status.ioScheduler, Icons.Default.Storage)
        }
    }
}

@Composable
fun KernelStatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    small: Boolean = false,
    valueColor: Color = Cyan400
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(13.dp))
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
        Text(value, fontSize = if (small) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
fun StatCard(modifier: Modifier, icon: ImageVector, label: String, value: String, color: Color) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

private fun tempColor(temp: String): Color {
    val v = temp.replace("°C", "").toIntOrNull() ?: return Cyan400
    return when { v >= 70 -> RedAccent; v >= 55 -> OrangeAccent; else -> GreenAccent }
}

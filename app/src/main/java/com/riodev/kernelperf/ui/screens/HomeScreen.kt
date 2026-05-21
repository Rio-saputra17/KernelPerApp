package com.riodev.kernelperf.ui.screens

import androidx.compose.animation.*
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
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "KernelPerf",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cyan400
                )
                Text(
                    "Per-App Kernel Manager",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
            RootBadge(isRooted)
        }

        // Active Profile Banner
        if (activeApp.isNotBlank() && AppDetectionService.isRunning) {
            ActiveProfileBanner(activeApp, profiles.count { it.packageName == activeApp && it.isEnabled } > 0)
        }

        // Service Status
        ServiceStatusCard()

        // Kernel Status Grid
        Text(
            "Kernel Status",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
        KernelStatusGrid(kernelStatus)

        // Quick Stats
        Text(
            "Overview",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextSecondary
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Apps,
                label = "Profil Aktif",
                value = "${profiles.count { it.isEnabled }}",
                color = Cyan400
            )
            StatCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Storage,
                label = "Total Profil",
                value = "${profiles.size}",
                color = Color(0xFF4FC3F7)
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
fun RootBadge(isRooted: Boolean) {
    val color = if (isRooted) GreenAccent else RedAccent
    val label = if (isRooted) "Rooted" else "No Root"
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
        Text(label, fontSize = 12.sp, color = color, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ActiveProfileBanner(packageName: String, hasProfile: Boolean) {
    val appName = packageName.substringAfterLast(".")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(Cyan900.copy(alpha = 0.6f), DarkCard)
                )
            )
            .border(1.dp, Cyan400.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.PlayCircle, null, tint = Cyan400, modifier = Modifier.size(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("App Aktif", fontSize = 11.sp, color = TextSecondary)
            Text(appName, fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
        if (hasProfile) {
            Text(
                "Profil Diterapkan",
                fontSize = 10.sp,
                color = GreenAccent,
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
    val statusColor = if (isRunning) GreenAccent else OrangeAccent
    val statusText = if (isRunning) "Aktif — Memantau app" else "Nonaktif — Enable di Accessibility"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(statusColor)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text("App Detection Service", fontSize = 13.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
            Text(statusText, fontSize = 11.sp, color = TextSecondary)
        }
        Icon(
            if (isRunning) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
            null,
            tint = statusColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun KernelStatusGrid(status: KernelStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KernelStatItem(
                modifier = Modifier.weight(1f),
                label = "Governor",
                value = status.currentGovernor,
                icon = Icons.Default.Tune
            )
            KernelStatItem(
                modifier = Modifier.weight(1f),
                label = "CPU Freq",
                value = status.currentFreq,
                icon = Icons.Default.Speed
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KernelStatItem(
                modifier = Modifier.weight(1f),
                label = "Min / Max",
                value = "${status.currentMinFreq} / ${status.currentMaxFreq}",
                icon = Icons.Default.Timeline,
                smallText = true
            )
            KernelStatItem(
                modifier = Modifier.weight(1f),
                label = "Suhu CPU",
                value = status.cpuTemp,
                icon = Icons.Default.Thermostat,
                valueColor = tempColor(status.cpuTemp)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KernelStatItem(
                modifier = Modifier.weight(1f),
                label = "GPU Gov",
                value = status.gpuGovernor,
                icon = Icons.Default.Memory
            )
            KernelStatItem(
                modifier = Modifier.weight(1f),
                label = "I/O Scheduler",
                value = status.ioScheduler,
                icon = Icons.Default.Storage
            )
        }
    }
}

@Composable
fun KernelStatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    icon: ImageVector,
    smallText: Boolean = false,
    valueColor: Color = Cyan400
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 11.sp, color = TextSecondary)
        }
        Text(
            value,
            fontSize = if (smallText) 12.sp else 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor
        )
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    color: Color
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Text(value, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = TextSecondary)
    }
}

private fun tempColor(temp: String): Color {
    val value = temp.replace("°C", "").toIntOrNull() ?: return Cyan400
    return when {
        value >= 70 -> RedAccent
        value >= 55 -> OrangeAccent
        else -> GreenAccent
    }
}

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riodev.kernelperf.root.RootUtils
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun DefaultProfileScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val governors by viewModel.governors.collectAsState()
    val frequencies by viewModel.frequencies.collectAsState()
    val schedulers by viewModel.schedulers.collectAsState()
    val gpuGovernors by viewModel.gpuGovernors.collectAsState()
    val defaultProfile by viewModel.defaultProfile.collectAsState()

    var cpuGovernor by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuGovernor) }
    var cpuMinFreq by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuMinFreq) }
    var cpuMaxFreq by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuMaxFreq) }
    var gpuGovernor by remember(defaultProfile) { mutableStateOf(defaultProfile.gpuGovernor) }
    var ioScheduler by remember(defaultProfile) { mutableStateOf(defaultProfile.ioScheduler) }
    var isSaving by remember { mutableStateOf(false) }
    var isApplying by remember { mutableStateOf(false) }
    var showSuccess by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Profil Default / Idle", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Diterapkan saat tidak ada app berprofl yang aktif", fontSize = 12.sp, color = TextSecondary)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Info banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cyan400.copy(alpha = 0.08f))
                    .border(1.dp, Cyan400.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Info, null, tint = Cyan400, modifier = Modifier.size(18.dp))
                Text(
                    "Setting ini aktif saat idle / tidak ada game yang berjalan. Gunakan governor hemat baterai.",
                    fontSize = 12.sp, color = TextSecondary, lineHeight = 18.sp
                )
            }

            // CPU Governor
            SectionTitle("CPU Governor")
            SectionCard {
                DropdownSetting(
                    label = "Governor",
                    value = cpuGovernor,
                    options = governors.ifEmpty { listOf("schedutil", "powersave", "performance", "conservative", "ondemand", "userspace", "walt", "schedhorizon") },
                    onSelect = { cpuGovernor = it }
                )
            }

            // CPU Frequency
            SectionTitle("CPU Frequency")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    FrequencyDropdown("Min Frequency", cpuMinFreq, frequencies) { cpuMinFreq = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Max Frequency", cpuMaxFreq, frequencies) { cpuMaxFreq = it }
                }
            }

            // GPU
            SectionTitle("GPU Governor")
            SectionCard {
                DropdownSetting(
                    label = "GPU Governor",
                    value = gpuGovernor,
                    options = (listOf("default") + gpuGovernors).ifEmpty { listOf("default", "msm-adreno-tz", "performance", "powersave", "simple_ondemand") },
                    onSelect = { gpuGovernor = it }
                )
            }

            // I/O Scheduler
            SectionTitle("I/O Scheduler")
            SectionCard {
                DropdownSetting(
                    label = "Scheduler",
                    value = ioScheduler,
                    options = (listOf("default") + schedulers).ifEmpty { listOf("default", "bfq", "kyber", "mq-deadline", "none", "noop") },
                    onSelect = { ioScheduler = it }
                )
            }

            // Success banner
            if (showSuccess) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(GreenAccent.copy(alpha = 0.1f))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = GreenAccent, modifier = Modifier.size(18.dp))
                    Text("Profil default berhasil disimpan!", fontSize = 13.sp, color = GreenAccent)
                }
            }

            // Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Apply Now
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isApplying = true
                            applyNow(cpuGovernor, cpuMinFreq, cpuMaxFreq, gpuGovernor, ioScheduler)
                            isApplying = false
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Cyan400),
                    enabled = !isApplying && !isSaving
                ) {
                    if (isApplying) {
                        CircularProgressIndicator(color = Cyan400, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.PlayArrow, null, tint = Cyan400, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Terapkan", color = Cyan400, fontSize = 13.sp)
                    }
                }

                // Save
                Button(
                    onClick = {
                        scope.launch {
                            isSaving = true
                            viewModel.saveDefaultProfile(cpuGovernor, cpuMinFreq, cpuMaxFreq, gpuGovernor, ioScheduler)
                            showSuccess = true
                            isSaving = false
                            kotlinx.coroutines.delay(3000)
                            showSuccess = false
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan400),
                    enabled = !isSaving && !isApplying
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Save, null, tint = DarkBg, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Simpan", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

private suspend fun applyNow(gov: String, minFreq: Int, maxFreq: Int, gpuGov: String, ioSched: String) {
    if (gov.isNotBlank()) RootUtils.setGovernor(gov)
    if (minFreq > 0) RootUtils.setMinFreq(minFreq)
    if (maxFreq > 0) RootUtils.setMaxFreq(maxFreq)
    if (gpuGov != "default") RootUtils.setGpuGovernor(gpuGov)
    if (ioSched != "default") RootUtils.setScheduler(ioSched)
}

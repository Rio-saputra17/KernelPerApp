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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.riodev.kernelperf.root.RootUtils
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DefaultProfileScreen(viewModel: MainViewModel) {
    val scope = rememberCoroutineScope()
    val governors by viewModel.governors.collectAsState()
    val bigGovernors by viewModel.bigGovernors.collectAsState()
    val frequencies by viewModel.frequencies.collectAsState()
    val bigFrequencies by viewModel.bigFrequencies.collectAsState()
    val schedulers by viewModel.schedulers.collectAsState()
    val gpuGovernors by viewModel.gpuGovernors.collectAsState()
    val defaultProfile by viewModel.defaultProfile.collectAsState()

    var littleGov by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuGovernor) }
    var bigGov by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuGovernor) }
    var littleMinFreq by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuMinFreq) }
    var littleMaxFreq by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuMaxFreq) }
    var bigMinFreq by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuMinFreq) }
    var bigMaxFreq by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuMaxFreq) }
    var gpuGov by remember(defaultProfile) { mutableStateOf(defaultProfile.gpuGovernor) }
    var gpuMinFreq by remember(defaultProfile) { mutableStateOf(defaultProfile.gpuMinFreq) }
    var gpuMaxFreq by remember(defaultProfile) { mutableStateOf(defaultProfile.gpuMaxFreq) }
    var ioSched by remember(defaultProfile) { mutableStateOf(defaultProfile.ioScheduler) }
    var thermalProfile by remember(defaultProfile) { mutableStateOf(defaultProfile.thermalProfile) }

    var isBusy by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var doneMsg by remember { mutableStateOf("") }

    // Done popup
    if (showDone) {
        Dialog(onDismissRequest = { showDone = false }) {
            Column(
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(DarkCard).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = GreenAccent, modifier = Modifier.size(48.dp))
                Text(doneMsg, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text("Setting berhasil diterapkan", fontSize = 13.sp, color = TextSecondary)
                Button(
                    onClick = { showDone = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan400),
                    shape = RoundedCornerShape(10.dp)
                ) { Text("OK", color = DarkBg, fontWeight = FontWeight.Bold) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("Profil Default / Idle", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Diterapkan saat tidak ada app berprofl yang aktif", fontSize = 11.sp, color = TextSecondary)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ── Little Cluster ─────────────────────────────────
            SectionTitle("CPU Little Cluster")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownSetting("Governor", littleGov, governors.ifEmpty { listOf("schedutil","powersave","performance","conservative","ondemand","walt","schedhorizon") }) { littleGov = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Min Frequency", littleMinFreq, frequencies) { littleMinFreq = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Max Frequency", littleMaxFreq, frequencies) { littleMaxFreq = it }
                }
            }

            // ── Big Cluster ────────────────────────────────────
            SectionTitle("CPU Big Cluster")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownSetting("Governor", bigGov, bigGovernors.ifEmpty { listOf("schedutil","powersave","performance","conservative","ondemand","walt","schedhorizon") }) { bigGov = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Min Frequency", bigMinFreq, bigFrequencies) { bigMinFreq = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Max Frequency", bigMaxFreq, bigFrequencies) { bigMaxFreq = it }
                }
            }

            // ── GPU ────────────────────────────────────────────
            SectionTitle("GPU")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownSetting("GPU Governor", gpuGov, (listOf("default") + gpuGovernors)) { gpuGov = it }
                }
            }

            // ── I/O ────────────────────────────────────────────
            SectionTitle("I/O Scheduler")
            SectionCard {
                DropdownSetting("Scheduler", ioSched, (listOf("default") + schedulers)) { ioSched = it }
            }

            // ── Thermal ────────────────────────────────────────
            SectionTitle("Thermal Profile")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profile: $thermalProfile", fontSize = 13.sp, color = Cyan400, fontWeight = FontWeight.Medium)
                    Text("0=Default, 1=Performance, 2=Balanced, 3=Cool", fontSize = 11.sp, color = TextSecondary)
                    Slider(
                        value = thermalProfile.toFloat(),
                        onValueChange = { thermalProfile = it.toInt() },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400, inactiveTrackColor = DarkCardElevated)
                    )
                }
            }

            // ── Buttons ────────────────────────────────────────
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            RootUtils.setGovernor(littleGov)
                            if (littleMinFreq > 0) RootUtils.setMinFreq(littleMinFreq)
                            if (littleMaxFreq > 0) RootUtils.setMaxFreq(littleMaxFreq)
                            if (gpuGov != "default") RootUtils.setGpuGovernor(gpuGov)
                            if (ioSched != "default") RootUtils.setScheduler(ioSched)
                            RootUtils.setThermalProfile(thermalProfile)
                            isBusy = false
                            doneMsg = "Terapkan Sekarang"
                            showDone = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Cyan400),
                    enabled = !isBusy
                ) {
                    if (isBusy) CircularProgressIndicator(color = Cyan400, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.PlayArrow, null, tint = Cyan400, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Terapkan", color = Cyan400, fontSize = 13.sp) }
                }

                Button(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            viewModel.saveDefaultProfile(littleGov, littleMinFreq, littleMaxFreq, gpuGov, gpuMinFreq, gpuMaxFreq, ioSched, thermalProfile)
                            isBusy = false
                            doneMsg = "Profil Tersimpan"
                            showDone = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan400),
                    enabled = !isBusy
                ) {
                    if (isBusy) CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Save, null, tint = DarkBg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Simpan", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

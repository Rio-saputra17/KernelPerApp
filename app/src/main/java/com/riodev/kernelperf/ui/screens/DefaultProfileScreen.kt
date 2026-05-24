package com.riodev.kernelperf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
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
    val dp by viewModel.defaultProfile.collectAsState()

    var littleGov by remember(dp.cpuGovernor) { mutableStateOf(dp.cpuGovernor) }
    var bigGov by remember(dp.cpuGovernor) { mutableStateOf(dp.cpuGovernor) }
    var littleMin by remember(dp.cpuMinFreq) { mutableStateOf(dp.cpuMinFreq) }
    var littleMax by remember(dp.cpuMaxFreq) { mutableStateOf(dp.cpuMaxFreq) }
    var bigMin by remember(dp.cpuMinFreq) { mutableStateOf(dp.cpuMinFreq) }
    var bigMax by remember(dp.cpuMaxFreq) { mutableStateOf(dp.cpuMaxFreq) }
    var gpuGov by remember(dp.gpuGovernor) { mutableStateOf(dp.gpuGovernor) }
    var ioSched by remember(dp.ioScheduler) { mutableStateOf(dp.ioScheduler) }
    var thermal by remember(dp.thermalProfile) { mutableStateOf(dp.thermalProfile) }
    var isBusy by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var doneMsg by remember { mutableStateOf("") }

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
                Button(onClick = { showDone = false }, colors = ButtonDefaults.buttonColors(containerColor = Cyan400), shape = RoundedCornerShape(10.dp)) {
                    Text("OK", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(16.dp)) {
            Text("Profil Default / Idle", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text("Diterapkan saat tidak ada app berprofl yang aktif", fontSize = 11.sp, color = TextSecondary)
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SectionTitle("CPU Little Cluster")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownSetting("Governor", littleGov, governors.ifEmpty { listOf("schedutil","powersave","performance","conservative","ondemand","walt","schedhorizon") }) { littleGov = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Min Frequency", littleMin, frequencies) { littleMin = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Max Frequency", littleMax, frequencies) { littleMax = it }
                }
            }

            SectionTitle("CPU Big Cluster")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownSetting("Governor", bigGov, bigGovernors.ifEmpty { listOf("schedutil","powersave","performance","conservative","ondemand","walt","schedhorizon") }) { bigGov = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Min Frequency", bigMin, bigFrequencies) { bigMin = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Max Frequency", bigMax, bigFrequencies) { bigMax = it }
                }
            }

            SectionTitle("GPU")
            SectionCard {
                DropdownSetting("GPU Governor", gpuGov, (listOf("default") + gpuGovernors).ifEmpty { listOf("default","msm-adreno-tz","performance","powersave","simple_ondemand") }) { gpuGov = it }
            }

            SectionTitle("I/O Scheduler")
            SectionCard {
                DropdownSetting("Scheduler", ioSched, (listOf("default") + schedulers).ifEmpty { listOf("default","bfq","kyber","mq-deadline","noop") }) { ioSched = it }
            }

            SectionTitle("Thermal Profile")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profile: $thermal", fontSize = 13.sp, color = Cyan400, fontWeight = FontWeight.Medium)
                    Text("0=Default  1=Performance  2=Balanced  3=Cool", fontSize = 11.sp, color = TextSecondary)
                    Slider(
                        value = thermal.toFloat(), onValueChange = { thermal = it.toInt() },
                        valueRange = 0f..10f, steps = 9,
                        colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400, inactiveTrackColor = DarkCardElevated)
                    )
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            RootUtils.setGovernor(littleGov)
                            if (littleMin > 0) RootUtils.setMinFreq(littleMin)
                            if (littleMax > 0) RootUtils.setMaxFreq(littleMax)
                            if (gpuGov != "default") RootUtils.setGpuGovernor(gpuGov)
                            if (ioSched != "default") RootUtils.setScheduler(ioSched)
                            RootUtils.setThermalProfile(thermal)
                            isBusy = false; doneMsg = "Terapkan Sekarang"; showDone = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, Cyan400), enabled = !isBusy
                ) {
                    if (isBusy) CircularProgressIndicator(color = Cyan400, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.PlayArrow, null, tint = Cyan400, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Terapkan", color = Cyan400, fontSize = 13.sp) }
                }

                Button(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            viewModel.saveDefaultProfile(littleGov, littleMin, littleMax, gpuGov, 0, 0, ioSched, thermal)
                            isBusy = false; doneMsg = "Profil Tersimpan"; showDone = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan400), enabled = !isBusy
                ) {
                    if (isBusy) CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Save, null, tint = DarkBg, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Simpan", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

package com.riodev.kernelperf.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
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
    val defaultProfile by viewModel.defaultProfile.collectAsState()
    val kernelStatus by viewModel.kernelStatus.collectAsState()

    var littleGov by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuGovernor) }
    var littleMin by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuMinFreq) }
    var littleMax by remember(defaultProfile) { mutableStateOf(defaultProfile.cpuMaxFreq) }
    var gpuGov by remember(defaultProfile) { mutableStateOf(defaultProfile.gpuGovernor) }
    var ioSched by remember(defaultProfile) { mutableStateOf(defaultProfile.ioScheduler) }
    var thermal by remember(defaultProfile) { mutableStateOf(defaultProfile.thermalProfile) }

    var isBusy by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var doneMsg by remember { mutableStateOf("") }

    val littleGovernors = remember { RootUtils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_governors").split(" ").filter { it.isNotBlank() } }
    val bigGovernors = remember { RootUtils.readFile("/sys/devices/system/cpu/cpu4/cpufreq/scaling_available_governors").split(" ").filter { it.isNotBlank() } }
    val gpuGovernors = remember { RootUtils.readFile("/sys/class/kgsl/kgsl-3d0/devfreq/available_governors").split(" ").filter { it.isNotBlank() } }
    val schedulers = remember { RootUtils.readFile("/sys/block/sda/queue/scheduler").replace(Regex("[\\[\\]]"), "").split(" ").filter { it.isNotBlank() } }
    val littleFrequencies = remember { RootUtils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies").split(" ").filter { it.isNotBlank() }.mapNotNull { it.trim().toIntOrNull() } }
    val littleFrequencies = remember { RootUtils.readFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_available_frequencies").split(" ").filter { s -> s.isNotBlank() }.mapNotNull { s -> s.trim().toIntOrNull() } }
    val bigFrequencies = remember { RootUtils.readFile("/sys/devices/system/cpu/cpu4/cpufreq/scaling_available_frequencies").split(" ").filter { s -> s.isNotBlank() }.mapNotNull { s -> s.trim().toIntOrNull() } }
    if (showDone) {
        LaunchedEffect(showDone) {
            kotlinx.coroutines.delay(2000)
            showDone = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            if (showDone) {
                Card(colors = CardDefaults.cardColors(containerColor = Cyan400.copy(alpha = 0.15f)), shape = RoundedCornerShape(10.dp)) {
                    Text(doneMsg, modifier = Modifier.padding(12.dp), color = Cyan400, fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            SectionTitle("CPU Little Cluster")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownSetting("Governor", littleGov,
                        littleGovernors.ifEmpty { listOf("schedutil","powersave","performance","conservative","ondemand","walt","schedhorizon") }
                    ) { littleGov = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Min Frequency", littleMin, littleFrequencies) { littleMin = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Max Frequency", littleMax, littleFrequencies) { littleMax = it }
                }
            }
        }

        item {
            SectionTitle("CPU Big Cluster")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DropdownSetting("Governor", littleGov,
                        bigGovernors.ifEmpty { listOf("schedutil","powersave","performance","conservative","ondemand","walt","schedhorizon") }
                    ) { littleGov = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Min Frequency", littleMin, littleFrequencies) { littleMin = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Max Frequency", littleMax, littleFrequencies) { littleMax = it }
                }
            }
        }

        item {
            SectionTitle("GPU")
            SectionCard {
                DropdownSetting("GPU Governor", gpuGov,
                    (listOf("default") + gpuGovernors.toList()).ifEmpty { listOf("default","msm-adreno-tz","performance","powersave","simple_ondemand") }
                ) { gpuGov = it }
            }
        }

        item {
            SectionTitle("I/O Scheduler")
            SectionCard {
                DropdownSetting("Scheduler", ioSched,
                    (listOf("default") + schedulers.toList()).ifEmpty { listOf("default","bfq","kyber","mq-deadline","noop") }
                ) { ioSched = it }
            }
        }

        item {
            SectionTitle("Thermal Profile")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Profile: $thermal", fontSize = 13.sp, color = Cyan400, fontWeight = FontWeight.Medium)
                    Text("0=Default  1=Performance  2=Balanced  3=Cool", fontSize = 11.sp, color = TextSecondary)
                    Slider(
                        value = thermal.toFloat(),
                        onValueChange = { thermal = it.toInt() },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(thumbColor = Cyan400, activeTrackColor = Cyan400, inactiveTrackColor = DarkCardElevated)
                    )
                }
            }
        }

        item {
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
                    else {
                        Icon(Icons.Default.PlayArrow, null, tint = Cyan400, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Terapkan", color = Cyan400, fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = {
                        scope.launch {
                            isBusy = true
                            viewModel.saveDefaultProfile(littleGov, littleMin, littleMax, gpuGov, 0, 0, ioSched, thermal)
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
                    else {
                        Icon(Icons.Default.Save, null, tint = DarkBg, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Simpan", color = DarkBg, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

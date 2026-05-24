package com.riodev.kernelperf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.PowerMode
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProfileEditorScreen(packageName: String, viewModel: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val governors by viewModel.governors.collectAsState()
    val frequencies by viewModel.frequencies.collectAsState()
    val schedulers by viewModel.schedulers.collectAsState()
    val gpuGovernors by viewModel.gpuGovernors.collectAsState()

    val appName = remember {
        try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(packageName, 0)).toString() }
        catch (e: Exception) { packageName }
    }

    var existing by remember { mutableStateOf<AppProfile?>(null) }
    var isEnabled by remember { mutableStateOf(true) }
    var powerMode by remember { mutableStateOf(PowerMode.BALANCED) }
    var cpuGov by remember { mutableStateOf("schedutil") }
    var cpuMin by remember { mutableStateOf(0) }
    var cpuMax by remember { mutableStateOf(0) }
    var gpuGov by remember { mutableStateOf("default") }
    var ioSched by remember { mutableStateOf("default") }
    var customTweaks by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(packageName) {
        viewModel.getProfile(packageName)?.let { p ->
            existing = p; isEnabled = p.isEnabled; powerMode = p.powerMode
            cpuGov = p.cpuGovernor; cpuMin = p.cpuMinFreq; cpuMax = p.cpuMaxFreq
            gpuGov = p.gpuGovernor; ioSched = p.ioScheduler; customTweaks = p.customTweaks
        }
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            containerColor = DarkCard,
            title = { Text("Hapus Profil?", color = TextPrimary) },
            text = { Text("Profil untuk $appName akan dihapus.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteProfile(packageName); showDelete = false; onBack() }) {
                    Text("Hapus", color = RedAccent)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal", color = TextSecondary) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
            Column(Modifier.weight(1f)) {
                Text(appName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(packageName, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
            if (existing != null) {
                IconButton(onClick = { showDelete = true }) { Icon(Icons.Default.Delete, null, tint = RedAccent) }
            }
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Aktifkan Profil", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("Terapkan setting saat app dibuka", fontSize = 12.sp, color = TextSecondary)
                    }
                    Switch(checked = isEnabled, onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = DarkBg, checkedTrackColor = Cyan400))
                }
            }

            SectionTitle("Power Mode")
            PowerModeSelector(selected = powerMode, onSelect = {
                powerMode = it
                if (it != PowerMode.CUSTOM) cpuGov = when (it) {
                    PowerMode.POWERSAVE -> "powersave"
                    PowerMode.BALANCED -> "schedutil"
                    PowerMode.PERFORMANCE, PowerMode.GAMING -> "performance"
                    else -> cpuGov
                }
            })

            SectionTitle("CPU Settings")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DropdownSetting("Governor", cpuGov,
                        governors.ifEmpty { listOf("schedutil","performance","powersave","ondemand","conservative","walt","schedhorizon") },
                        enabled = powerMode == PowerMode.CUSTOM
                    ) { cpuGov = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Min Frequency", cpuMin, frequencies) { cpuMin = it }
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown("Max Frequency", cpuMax, frequencies) { cpuMax = it }
                }
            }

            SectionTitle("GPU Settings")
            SectionCard {
                DropdownSetting("GPU Governor", gpuGov, listOf("default") + gpuGovernors) { gpuGov = it }
            }

            SectionTitle("I/O Scheduler")
            SectionCard {
                DropdownSetting("Scheduler", ioSched, listOf("default") + schedulers) { ioSched = it }
            }

            SectionTitle("Custom Tweaks (Opsional)")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Format: /path/node=value  pisahkan dengan ;", fontSize = 11.sp, color = TextSecondary)
                    OutlinedTextField(
                        value = customTweaks, onValueChange = { customTweaks = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("/sys/kernel/gpu/gpu_max_clock=587000", fontSize = 11.sp, color = TextSecondary.copy(alpha = 0.4f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400, unfocusedBorderColor = DarkCardElevated,
                            focusedContainerColor = DarkCardElevated, unfocusedContainerColor = DarkCardElevated,
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Cyan400
                        ),
                        shape = RoundedCornerShape(8.dp), minLines = 3
                    )
                }
            }

            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        viewModel.saveProfile(AppProfile(
                            packageName = packageName, appName = appName, isEnabled = isEnabled,
                            powerMode = powerMode, cpuGovernor = cpuGov, cpuMinFreq = cpuMin,
                            cpuMaxFreq = cpuMax, gpuGovernor = gpuGov, ioScheduler = ioSched,
                            customTweaks = customTweaks
                        ))
                        isSaving = false; onBack()
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan400),
                shape = RoundedCornerShape(12.dp), enabled = !isSaving
            ) {
                if (isSaving) CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(20.dp))
                else {
                    Icon(Icons.Default.Save, null, tint = DarkBg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (existing != null) "Update Profil" else "Simpan Profil", color = DarkBg, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun PowerModeSelector(selected: PowerMode, onSelect: (PowerMode) -> Unit) {
    val modes = listOf(
        Triple(PowerMode.POWERSAVE, Icons.Default.BatteryFull, Color(0xFF4CAF50)),
        Triple(PowerMode.BALANCED, Icons.Default.Balance, Cyan400),
        Triple(PowerMode.PERFORMANCE, Icons.Default.Bolt, OrangeAccent),
        Triple(PowerMode.GAMING, Icons.Default.SportsEsports, RedAccent),
        Triple(PowerMode.CUSTOM, Icons.Default.Tune, Color(0xFF9C27B0))
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.take(3).forEach { (mode, icon, color) ->
                PMChip(Modifier.weight(1f), mode, icon, color, selected == mode, onSelect)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.drop(3).forEach { (mode, icon, color) ->
                PMChip(Modifier.weight(1f), mode, icon, color, selected == mode, onSelect)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun PMChip(modifier: Modifier, mode: PowerMode, icon: ImageVector, color: Color, isSelected: Boolean, onSelect: (PowerMode) -> Unit) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) color.copy(alpha = 0.2f) else DarkCard)
            .clickable { onSelect(mode) }.padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = if (isSelected) color else TextSecondary, modifier = Modifier.size(20.dp))
        Text(mode.label, fontSize = 10.sp, color = if (isSelected) color else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
fun DropdownSetting(label: String, value: String, options: List<String>, enabled: Boolean = true, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = if (enabled) TextPrimary else TextSecondary)
        Box {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DarkCardElevated)
                    .let { if (enabled) it.clickable { expanded = true } else it }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(value, fontSize = 12.sp, color = if (enabled) Cyan400 else TextSecondary)
                Icon(Icons.Default.ArrowDropDown, null, tint = if (enabled) Cyan400 else TextSecondary, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(DarkCard)) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, fontSize = 13.sp, color = if (opt == value) Cyan400 else TextPrimary) },
                        onClick = { onSelect(opt); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun FrequencyDropdown(label: String, value: Int, frequencies: List<Int>, onSelect: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    fun fmt(khz: Int): String {
        val k = khz.toLong()
        return if (k >= 1_000_000) String.format("%.1f GHz", k / 1_000_000.0) else String.format("%d MHz", k / 1000)
    }
    val displayValue = if (value == 0) "Default" else fmt(value)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextPrimary)
        Box {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(DarkCardElevated)
                    .clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(displayValue, fontSize = 12.sp, color = Cyan400)
                Icon(Icons.Default.ArrowDropDown, null, tint = Cyan400, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(DarkCard)) {
                DropdownMenuItem(
                    text = { Text("Default", fontSize = 13.sp, color = if (value == 0) Cyan400 else TextPrimary) },
                    onClick = { onSelect(0); expanded = false }
                )
                frequencies.reversed().forEach { freq ->
                    DropdownMenuItem(
                        text = { Text(fmt(freq), fontSize = 13.sp, color = if (freq == value) Cyan400 else TextPrimary) },
                        onClick = { onSelect(freq); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary)
}

@Composable
fun SectionCard(content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(DarkCard).padding(16.dp), content = content)
}

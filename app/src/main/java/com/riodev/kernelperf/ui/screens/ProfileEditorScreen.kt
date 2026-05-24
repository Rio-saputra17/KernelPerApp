package com.riodev.kernelperf.ui.screens

import android.content.pm.PackageManager
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.data.model.PowerMode
import com.riodev.kernelperf.root.RootUtils
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProfileEditorScreen(
    packageName: String,
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val governors by viewModel.governors.collectAsState()
    val frequencies by viewModel.frequencies.collectAsState()
    val schedulers by viewModel.schedulers.collectAsState()
    val gpuGovernors by viewModel.gpuGovernors.collectAsState()

    val appName = remember {
        try { context.packageManager.getApplicationLabel(
            context.packageManager.getApplicationInfo(packageName, 0)
        ).toString() } catch (e: PackageManager.NameNotFoundException) { packageName }
    }

    // Form state
    var existingProfile by remember { mutableStateOf<AppProfile?>(null) }
    var isEnabled by remember { mutableStateOf(true) }
    var powerMode by remember { mutableStateOf(PowerMode.BALANCED) }
    var cpuGovernor by remember { mutableStateOf("schedutil") }
    var cpuMinFreq by remember { mutableStateOf(0) }
    var cpuMaxFreq by remember { mutableStateOf(0) }
    var gpuGovernor by remember { mutableStateOf("default") }
    var ioScheduler by remember { mutableStateOf("default") }
    var customTweaks by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Load existing profile
    LaunchedEffect(packageName) {
        val profile = viewModel.getProfile(packageName)
        if (profile != null) {
            existingProfile = profile
            isEnabled = profile.isEnabled
            powerMode = profile.powerMode
            cpuGovernor = profile.cpuGovernor
            cpuMinFreq = profile.cpuMinFreq
            cpuMaxFreq = profile.cpuMaxFreq
            gpuGovernor = profile.gpuGovernor
            ioScheduler = profile.ioScheduler
            customTweaks = profile.customTweaks
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = DarkCard,
            title = { Text("Hapus Profil?", color = TextPrimary) },
            text = { Text("Profil untuk $appName akan dihapus.", color = TextSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(packageName)
                    showDeleteDialog = false
                    onBack()
                }) { Text("Hapus", color = RedAccent) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Batal", color = TextSecondary)
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(appName, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(packageName, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
            }
            if (existingProfile != null) {
                IconButton(onClick = { showDeleteDialog = true }) {
                    Icon(Icons.Default.Delete, null, tint = RedAccent)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Enable toggle
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Aktifkan Profil", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
                        Text("Terapkan setting saat app dibuka", fontSize = 12.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { isEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = DarkBg,
                            checkedTrackColor = Cyan400
                        )
                    )
                }
            }

            // Power Mode
            SectionTitle("Power Mode")
            PowerModeSelector(
                selected = powerMode,
                onSelect = {
                    powerMode = it
                    // Auto-set governor
                    if (it != PowerMode.CUSTOM) {
                        cpuGovernor = when (it) {
                            PowerMode.POWERSAVE -> "powersave"
                            PowerMode.BALANCED -> "schedutil"
                            PowerMode.PERFORMANCE -> "performance"
                            PowerMode.GAMING -> "performance"
                            else -> cpuGovernor
                        }
                    }
                }
            )

            // CPU Settings
            SectionTitle("CPU Settings")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    DropdownSetting(
                        label = "Governor",
                        value = cpuGovernor,
                        options = governors.ifEmpty { listOf("schedutil", "performance", "powersave", "ondemand") },
                        enabled = powerMode == PowerMode.CUSTOM,
                        onSelect = { cpuGovernor = it }
                    )
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown(
                        label = "Min Frequency",
                        value = cpuMinFreq,
                        frequencies = frequencies,
                        onSelect = { cpuMinFreq = it }
                    )
                    HorizontalDivider(color = DarkCardElevated)
                    FrequencyDropdown(
                        label = "Max Frequency",
                        value = cpuMaxFreq,
                        frequencies = frequencies,
                        onSelect = { cpuMaxFreq = it }
                    )
                }
            }

            // GPU Settings
            SectionTitle("GPU Settings")
            SectionCard {
                DropdownSetting(
                    label = "GPU Governor",
                    value = gpuGovernor,
                    options = listOf("default") + gpuGovernors,
                    onSelect = { gpuGovernor = it }
                )
            }

            // I/O Scheduler
            SectionTitle("I/O Scheduler")
            SectionCard {
                DropdownSetting(
                    label = "Scheduler",
                    value = ioScheduler,
                    options = listOf("default") + schedulers,
                    onSelect = { ioScheduler = it }
                )
            }

            // Custom Tweaks
            SectionTitle("Custom Tweaks (Opsional)")
            SectionCard {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Format: /path/node=value; pisahkan dengan semicolon",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = customTweaks,
                        onValueChange = { customTweaks = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("/sys/kernel/gpu/gpu_max_clock=587000", fontSize = 12.sp, color = TextSecondary.copy(alpha = 0.5f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Cyan400,
                            unfocusedBorderColor = DarkCardElevated,
                            focusedContainerColor = DarkCardElevated,
                            unfocusedContainerColor = DarkCardElevated,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Cyan400
                        ),
                        shape = RoundedCornerShape(8.dp),
                        minLines = 3
                    )
                }
            }

            // Save Button
            Button(
                onClick = {
                    scope.launch {
                        isSaving = true
                        val profile = AppProfile(
                            packageName = packageName,
                            appName = appName,
                            isEnabled = isEnabled,
                            powerMode = powerMode,
                            cpuGovernor = cpuGovernor,
                            cpuMinFreq = cpuMinFreq,
                            cpuMaxFreq = cpuMaxFreq,
                            gpuGovernor = gpuGovernor,
                            ioScheduler = ioScheduler,
                            customTweaks = customTweaks
                        )
                        viewModel.saveProfile(profile)
                        isSaving = false
                        onBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan400),
                shape = RoundedCornerShape(12.dp),
                enabled = !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = DarkBg, modifier = Modifier.size(20.dp))
                } else {
                    Icon(Icons.Default.Save, null, tint = DarkBg, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (existingProfile != null) "Update Profil" else "Simpan Profil",
                        color = DarkBg,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun PowerModeSelector(selected: PowerMode, onSelect: (PowerMode) -> Unit) {
    val modes = listOf(
        PowerMode.POWERSAVE to Pair(Icons.Default.BatteryFull, Color(0xFF4CAF50)),
        PowerMode.BALANCED to Pair(Icons.Default.Balance, Cyan400),
        PowerMode.PERFORMANCE to Pair(Icons.Default.Bolt, OrangeAccent),
        PowerMode.GAMING to Pair(Icons.Default.SportsEsports, RedAccent),
        PowerMode.CUSTOM to Pair(Icons.Default.Tune, Color(0xFF9C27B0))
    )

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.take(3).forEach { (mode, pair) ->
                PowerModeChip(
                    modifier = Modifier.weight(1f),
                    mode = mode,
                    icon = pair.first,
                    color = pair.second,
                    isSelected = selected == mode,
                    onSelect = onSelect
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            modes.drop(3).forEach { (mode, pair) ->
                PowerModeChip(
                    modifier = Modifier.weight(1f),
                    mode = mode,
                    icon = pair.first,
                    color = pair.second,
                    isSelected = selected == mode,
                    onSelect = onSelect
                )
            }
            // Filler
            if (modes.drop(3).size % 3 != 0) {
                Spacer(modifier = Modifier.weight((3 - modes.drop(3).size % 3).toFloat()))
            }
        }
    }
}

@Composable
fun PowerModeChip(
    modifier: Modifier = Modifier,
    mode: PowerMode,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isSelected: Boolean,
    onSelect: (PowerMode) -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) color.copy(alpha = 0.2f) else DarkCard)
            .border(
                width = if (isSelected) 1.5.dp else 0.dp,
                color = if (isSelected) color else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect(mode) }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, null, tint = if (isSelected) color else TextSecondary, modifier = Modifier.size(20.dp))
        Text(
            mode.label,
            fontSize = 10.sp,
            color = if (isSelected) color else TextSecondary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
fun DropdownSetting(
    label: String,
    value: String,
    options: List<String>,
    enabled: Boolean = true,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = if (enabled) TextPrimary else TextSecondary)

        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCardElevated)
                    .let { if (enabled) it.clickable { expanded = true } else it }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(value, fontSize = 12.sp, color = if (enabled) Cyan400 else TextSecondary)
                Icon(
                    Icons.Default.ArrowDropDown, null,
                    tint = if (enabled) Cyan400 else TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkCard)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option, fontSize = 13.sp, color = if (option == value) Cyan400 else TextPrimary) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FrequencyDropdown(
    label: String,
    val displayValue = if (value == 0) "Default" else {
        val k = value.toLong()
        if (k >= 1_000_000) String.format("%.1f GHz", k / 1_000_000.0)
        else String.format("%d MHz", k / 1000)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, color = TextPrimary)
        Box {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkCardElevated)
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(displayValue, fontSize = 12.sp, color = Cyan400)
                Icon(Icons.Default.ArrowDropDown, null, tint = Cyan400, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(DarkCard)
            ) {
                DropdownMenuItem(
                    text = { Text("Default", fontSize = 13.sp, color = if (value == 0) Cyan400 else TextPrimary) },
                    onClick = { onSelect(0); expanded = false }
                )
                frequencies.reversed().forEach { freq ->
                    val freqLabel = freq.toLong().let { k ->
                        if (k >= 1_000_000) String.format("%.1f GHz", k / 1_000_000.0)
                        else String.format("%d MHz", k / 1000)
                    }
                    DropdownMenuItem(
                        text = { Text(freqLabel, fontSize = 13.sp, color = if (freq == value) Cyan400 else TextPrimary) },
                        onClick = { onSelect(freq); expanded = false }
                    )
                }
            }
        }
    }
}

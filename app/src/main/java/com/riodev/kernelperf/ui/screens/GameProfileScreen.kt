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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.riodev.kernelperf.data.model.AppProfile
import com.riodev.kernelperf.root.Kernel
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun GameProfileScreen(pkg: String, vm: MainViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val littleGovs by vm.littleGovs.collectAsState()
    val bigGovs by vm.bigGovs.collectAsState()
    val littleFreqs by vm.littleFreqs.collectAsState()
    val bigFreqs by vm.bigFreqs.collectAsState()
    val gpuFreqs by vm.gpuFreqs.collectAsState()

    val appName = remember {
        try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString() }
        catch (e: Exception) { pkg }
    }

    var existing by remember { mutableStateOf<AppProfile?>(null) }
    var enabled by remember { mutableStateOf(true) }
    var lGov by remember { mutableStateOf("performance") }
    var lMin by remember { mutableStateOf(0) }
    var lMax by remember { mutableStateOf(0) }
    var bGov by remember { mutableStateOf("performance") }
    var bMin by remember { mutableStateOf(0) }
    var bMax by remember { mutableStateOf(0) }
    var gMin by remember { mutableStateOf(0L) }
    var gMax by remember { mutableStateOf(0L) }
    var thermal by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(pkg) {
        vm.getProfile(pkg)?.let { p ->
            existing = p; enabled = p.isEnabled
            lGov = p.littleGovernor; lMin = p.littleMinFreq; lMax = p.littleMaxFreq
            bGov = p.bigGovernor; bMin = p.bigMinFreq; bMax = p.bigMaxFreq
            gMin = p.gpuMinFreq.toLong(); gMax = p.gpuMaxFreq.toLong()
            thermal = p.thermalProfile
        }
    }

    if (showDone) {
        Dialog(onDismissRequest = { showDone = false }) {
            Column(modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Card).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(44.dp))
                Text("Profil Game Tersimpan", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPri)
                Text(appName, fontSize = 12.sp, color = TextSec)
                Button(onClick = { showDone = false; onBack() },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("OK", color = BgDark, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (showDelete) {
        AlertDialog(onDismissRequest = { showDelete = false },
            containerColor = Card,
            title = { Text("Hapus Profil?", color = TextPri) },
            text = { Text("Profil game untuk $appName akan dihapus.", color = TextSec) },
            confirmButton = {
                TextButton(onClick = { vm.deleteProfile(pkg); showDelete = false; onBack() }) {
                    Text("Hapus", color = Red)
                }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text("Batal", color = TextSec) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Top bar
        Row(modifier = Modifier.fillMaxWidth().background(Card)
            .border(BorderStroke(1.dp, CardBorder)).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPri) }
            Column(Modifier.weight(1f)) {
                Text(appName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPri)
                Text(pkg, fontSize = 10.sp, color = TextSec, maxLines = 1)
            }
            if (existing != null) {
                IconButton(onClick = { showDelete = true }) {
                    Icon(Icons.Default.Delete, null, tint = Red)
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Enable toggle
            KCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("Aktifkan Profil Game", fontSize = 13.sp, color = TextPri, fontWeight = FontWeight.Medium)
                        Text("Terapkan otomatis saat game dibuka", fontSize = 11.sp, color = TextSec)
                    }
                    Switch(checked = enabled, onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = BgDark, checkedTrackColor = Cyan))
                }
            }

            // Little Cluster
            SectionHeader("Little Cluster CPU")
            KCard {
                DropRow("Maximum CPU Frequency", Kernel.fmtFreqInt(lMax),
                    listOf("Default") + littleFreqs.reversed().map { Kernel.fmtKhz(it.toLong()) }
                ) { sel -> lMax = if (sel == "Default") 0 else littleFreqs.reversed().firstOrNull { Kernel.fmtKhz(it.toLong()) == sel } ?: 0 }
                HorizontalDivider(color = CardBorder)
                DropRow("Minimum CPU Frequency", Kernel.fmtFreqInt(lMin),
                    listOf("Default") + littleFreqs.map { Kernel.fmtKhz(it.toLong()) }
                ) { sel -> lMin = if (sel == "Default") 0 else littleFreqs.firstOrNull { Kernel.fmtKhz(it.toLong()) == sel } ?: 0 }
                HorizontalDivider(color = CardBorder)
                DropRow("Little cluster CPU governor", lGov,
                    littleGovs.ifEmpty { listOf("schedutil","powersave","performance","conservative","ondemand","walt","schedhorizon") }
                ) { lGov = it }
            }

            // Big Cluster
            SectionHeader("Big Cluster CPU")
            KCard {
                DropRow("Maximum CPU Frequency", Kernel.fmtFreqInt(bMax),
                    listOf("Default") + bigFreqs.reversed().map { Kernel.fmtKhz(it.toLong()) }
                ) { sel -> bMax = if (sel == "Default") 0 else bigFreqs.reversed().firstOrNull { Kernel.fmtKhz(it.toLong()) == sel } ?: 0 }
                HorizontalDivider(color = CardBorder)
                DropRow("Minimum CPU Frequency", Kernel.fmtFreqInt(bMin),
                    listOf("Default") + bigFreqs.map { Kernel.fmtKhz(it.toLong()) }
                ) { sel -> bMin = if (sel == "Default") 0 else bigFreqs.firstOrNull { Kernel.fmtKhz(it.toLong()) == sel } ?: 0 }
                HorizontalDivider(color = CardBorder)
                DropRow("Big cluster CPU governor", bGov,
                    bigGovs.ifEmpty { listOf("schedutil","powersave","performance","conservative","ondemand","walt","schedhorizon") }
                ) { bGov = it }
            }

            // GPU
            SectionHeader("GPU")
            KCard {
                GpuFreqRow("Maximum GPU Frequency", gMax, gpuFreqs) { gMax = it }
                HorizontalDivider(color = CardBorder)
                GpuFreqRow("Minimum GPU Frequency", gMin, gpuFreqs) { gMin = it }
            }

            // Thermal
            SectionHeader("Thermal")
            KCard { ThermalRow(thermal) { thermal = it } }

            // Save button
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        vm.saveProfile(AppProfile(
                            packageName = pkg, appName = appName, isEnabled = enabled,
                            littleGovernor = lGov, littleMinFreq = lMin, littleMaxFreq = lMax,
                            bigGovernor = bGov, bigMinFreq = bMin, bigMaxFreq = bMax,
                            gpuMinFreq = gMin.toInt(), gpuMaxFreq = gMax.toInt(),
                            thermalProfile = thermal
                        ))
                        busy = false; showDone = true
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                enabled = !busy
            ) {
                if (busy) CircularProgressIndicator(color = BgDark, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Default.Save, null, tint = BgDark, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (existing != null) "Update Profil Game" else "Simpan Profil Game",
                        color = BgDark, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

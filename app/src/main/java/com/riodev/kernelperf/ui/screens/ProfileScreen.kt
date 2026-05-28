package com.riodev.kernelperf.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.riodev.kernelperf.data.model.IdleProfile
import com.riodev.kernelperf.root.Kernel
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen(vm: MainViewModel) {
    val scope = rememberCoroutineScope()
    val dp by vm.idleProfile.collectAsState()
    val littleGovs by vm.littleGovs.collectAsState()
    val bigGovs by vm.bigGovs.collectAsState()
    val littleFreqs by vm.littleFreqs.collectAsState()
    val bigFreqs by vm.bigFreqs.collectAsState()
    val gpuFreqs by vm.gpuFreqs.collectAsState()

    var lGov by remember(dp) { mutableStateOf(dp.littleGovernor) }
    var lMin by remember(dp) { mutableStateOf(dp.littleMinFreq) }
    var lMax by remember(dp) { mutableStateOf(dp.littleMaxFreq) }
    var bGov by remember(dp) { mutableStateOf(dp.bigGovernor) }
    var bMin by remember(dp) { mutableStateOf(dp.bigMinFreq) }
    var bMax by remember(dp) { mutableStateOf(dp.bigMaxFreq) }
    var gMin by remember(dp) { mutableStateOf(dp.gpuMinFreq.toLong()) }
    var gMax by remember(dp) { mutableStateOf(dp.gpuMaxFreq.toLong()) }
    var thermal by remember(dp) { mutableStateOf(dp.thermalProfile) }
    var busy by remember { mutableStateOf(false) }
    var showDone by remember { mutableStateOf(false) }
    var doneMsg by remember { mutableStateOf("") }

    if (showDone) {
        Dialog(onDismissRequest = { showDone = false }) {
            Column(
                modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(Card).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.CheckCircle, null, tint = Green, modifier = Modifier.size(44.dp))
                Text(doneMsg, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPri)
                Button(onClick = { showDone = false },
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                    shape = RoundedCornerShape(8.dp)
                ) { Text("OK", color = BgDark, fontWeight = FontWeight.Bold) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        // Header
        Column(modifier = Modifier.fillMaxWidth().background(Card)
            .border(BorderStroke(1.dp, CardBorder)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Profil Idle", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPri)
            Text("Diterapkan saat tidak ada game aktif", fontSize = 11.sp, color = TextSec)
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // Little Cluster
            SectionHeader("Little Cluster CPU")
            KCard {
                DropRow("Maximum CPU Frequency", Kernel.fmtFreqInt(lMax),
                    listOf("Default") + littleFreqs.reversed().map { Kernel.fmtKhz(it.toLong()) }
                ) { sel ->
                    lMax = if (sel == "Default") 0 else littleFreqs.reversed().firstOrNull { Kernel.fmtKhz(it.toLong()) == sel } ?: 0
                }
                HorizontalDivider(color = CardBorder)
                DropRow("Minimum CPU Frequency", Kernel.fmtFreqInt(lMin),
                    listOf("Default") + littleFreqs.map { Kernel.fmtKhz(it.toLong()) }
                ) { sel ->
                    lMin = if (sel == "Default") 0 else littleFreqs.firstOrNull { Kernel.fmtKhz(it.toLong()) == sel } ?: 0
                }
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
                ) { sel ->
                    bMax = if (sel == "Default") 0 else bigFreqs.reversed().firstOrNull { Kernel.fmtKhz(it.toLong()) == sel } ?: 0
                }
                HorizontalDivider(color = CardBorder)
                DropRow("Minimum CPU Frequency", Kernel.fmtFreqInt(bMin),
                    listOf("Default") + bigFreqs.map { Kernel.fmtKhz(it.toLong()) }
                ) { sel ->
                    bMin = if (sel == "Default") 0 else bigFreqs.firstOrNull { Kernel.fmtKhz(it.toLong()) == sel } ?: 0
                }
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

            // Buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            val p = IdleProfile(lGov, lMin, lMax, bGov, bMin, bMax, gMin.toInt(), gMax.toInt(), thermal)
                            Kernel.applyIdle(p)
                            busy = false; doneMsg = "Profil Diterapkan"; showDone = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Cyan),
                    enabled = !busy
                ) {
                    if (busy) CircularProgressIndicator(color = Cyan, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.PlayArrow, null, tint = Cyan, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Terapkan", color = Cyan, fontSize = 13.sp) }
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            val p = IdleProfile(lGov, lMin, lMax, bGov, bMin, bMax, gMin.toInt(), gMax.toInt(), thermal)
                            vm.saveIdle(p)
                            busy = false; doneMsg = "Profil Tersimpan"; showDone = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan),
                    enabled = !busy
                ) {
                    if (busy) CircularProgressIndicator(color = BgDark, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else { Icon(Icons.Default.Save, null, tint = BgDark, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Simpan", color = BgDark, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(80.dp))
        }
    }
}

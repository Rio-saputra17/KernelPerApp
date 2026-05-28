package com.riodev.kernelperf.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.riodev.kernelperf.root.Kernel
import com.riodev.kernelperf.ui.theme.*

@Composable
fun SectionHeader(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Cyan,
        modifier = Modifier.padding(top = 4.dp))
}

@Composable
fun KCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
            .background(Card).border(1.dp, CardBorder, RoundedCornerShape(10.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content
    )
}

@Composable
fun DropRow(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextPri)
        Box {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BgDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .clickable { exp = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(value, fontSize = 12.sp, color = Cyan, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ArrowDropDown, null, tint = Cyan, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = exp, onDismissRequest = { exp = false },
                modifier = Modifier.background(Card).border(1.dp, CardBorder, RoundedCornerShape(8.dp))) {
                options.forEach { opt ->
                    DropdownMenuItem(
                        text = { Text(opt, fontSize = 12.sp, color = if (opt == value) Cyan else TextPri) },
                        onClick = { onSelect(opt); exp = false }
                    )
                }
            }
        }
    }
}

// Frequency dropdown untuk CPU (Int khz)
@Composable
fun FreqRow(label: String, value: Int, freqs: List<Int>, onSelect: (Int) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    val display = Kernel.fmtFreqInt(value)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextPri)
        Box {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BgDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .clickable { exp = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(display, fontSize = 12.sp, color = Cyan, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ArrowDropDown, null, tint = Cyan, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = exp, onDismissRequest = { exp = false },
                modifier = Modifier.background(Card).border(1.dp, CardBorder, RoundedCornerShape(8.dp))) {
                DropdownMenuItem(
                    text = { Text("Default", fontSize = 12.sp, color = if (value == 0) Cyan else TextPri) },
                    onClick = { onSelect(0); exp = false }
                )
                freqs.reversed().forEach { f ->
                    DropdownMenuItem(
                        text = { Text(Kernel.fmtKhz(f.toLong()), fontSize = 12.sp,
                            color = if (f == value) Cyan else TextPri) },
                        onClick = { onSelect(f); exp = false }
                    )
                }
            }
        }
    }
}

// Frequency dropdown untuk GPU (Long hz)
@Composable
fun GpuFreqRow(label: String, value: Long, freqs: List<Long>, onSelect: (Long) -> Unit) {
    var exp by remember { mutableStateOf(false) }
    val display = if (value == 0L) "Default" else Kernel.fmtHz(value)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 13.sp, color = TextPri)
        Box {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(BgDark)
                    .border(1.dp, CardBorder, RoundedCornerShape(8.dp))
                    .clickable { exp = true }.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(display, fontSize = 12.sp, color = Cyan, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.ArrowDropDown, null, tint = Cyan, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(expanded = exp, onDismissRequest = { exp = false },
                modifier = Modifier.background(Card).border(1.dp, CardBorder, RoundedCornerShape(8.dp))) {
                DropdownMenuItem(
                    text = { Text("Default", fontSize = 12.sp, color = if (value == 0L) Cyan else TextPri) },
                    onClick = { onSelect(0L); exp = false }
                )
                freqs.reversed().forEach { f ->
                    DropdownMenuItem(
                        text = { Text(Kernel.fmtHz(f), fontSize = 12.sp,
                            color = if (f == value) Cyan else TextPri) },
                        onClick = { onSelect(f); exp = false }
                    )
                }
            }
        }
    }
}

@Composable
fun ThermalRow(value: Int, onChange: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Thermal Profile", fontSize = 13.sp, color = TextPri)
            Text("$value", fontSize = 13.sp, color = Cyan, fontWeight = FontWeight.Bold)
        }
        Text("0=Default  1=Performance  2=Balanced  3=Cool",
            fontSize = 10.sp, color = TextSec)
        Slider(value = value.toFloat(), onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..10f, steps = 9,
            colors = SliderDefaults.colors(thumbColor = Cyan, activeTrackColor = Cyan,
                inactiveTrackColor = CardBorder))
    }
}

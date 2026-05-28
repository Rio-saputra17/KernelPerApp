package com.riodev.kernelperf.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.riodev.kernelperf.data.model.InstalledApp
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.theme.*

@Composable
fun AppListScreen(vm: MainViewModel, onApp: (String) -> Unit) {
    val apps by vm.filteredApps.collectAsState()
    val loading by vm.loadingApps.collectAsState()
    val search by vm.search.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(BgDark)) {
        Column(modifier = Modifier.fillMaxWidth().background(Card)
            .border(BorderStroke(1.dp, CardBorder)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Game Profiles", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPri)
            OutlinedTextField(
                value = search, onValueChange = { vm.setSearch(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari aplikasi...", color = TextSec, fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSec, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (search.isNotBlank()) IconButton(onClick = { vm.setSearch("") }) {
                        Icon(Icons.Default.Clear, null, tint = TextSec, modifier = Modifier.size(16.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan, unfocusedBorderColor = CardBorder,
                    focusedContainerColor = BgDark, unfocusedContainerColor = BgDark,
                    focusedTextColor = TextPri, unfocusedTextColor = TextPri, cursorColor = Cyan
                ),
                shape = RoundedCornerShape(8.dp), singleLine = true
            )
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CircularProgressIndicator(color = Cyan, strokeWidth = 2.dp)
                    Text("Memuat...", color = TextSec, fontSize = 12.sp)
                }
            }
        } else {
            val withP = apps.filter { it.hasProfile }
            val withoutP = apps.filter { !it.hasProfile }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (withP.isNotEmpty()) {
                    item { Text("Sudah ada profil (${withP.size})", fontSize = 11.sp, color = Green, fontWeight = FontWeight.SemiBold) }
                    items(withP, key = { it.packageName }) { AppItem(it) { onApp(it.packageName) } }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                if (withoutP.isNotEmpty()) {
                    item { Text("Semua aplikasi (${withoutP.size})", fontSize = 11.sp, color = TextSec, fontWeight = FontWeight.SemiBold) }
                    items(withoutP, key = { it.packageName }) { AppItem(it) { onApp(it.packageName) } }
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun AppItem(app: InstalledApp, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        try { context.packageManager.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap() }
        catch (e: PackageManager.NameNotFoundException) { null }
    }
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .background(Card).border(1.dp, CardBorder, RoundedCornerShape(10.dp))
        .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(BgDark), contentAlignment = Alignment.Center) {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(32.dp))
            else Icon(Icons.Default.Android, null, tint = TextSec, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(app.appName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPri)
            Text(app.packageName, fontSize = 10.sp, color = TextSec, maxLines = 1)
        }
        if (app.hasProfile) Icon(Icons.Default.SportsEsports, null, tint = Cyan, modifier = Modifier.size(16.dp))
        Icon(Icons.Default.ChevronRight, null, tint = TextSec, modifier = Modifier.size(16.dp))
    }
}

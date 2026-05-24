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
fun AppListScreen(viewModel: MainViewModel, onAppSelected: (String) -> Unit) {
    val apps by viewModel.filteredApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(DarkBg)) {
        Column(modifier = Modifier.fillMaxWidth().background(DarkSurface).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Per-App Profiles", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            OutlinedTextField(
                value = searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari aplikasi...", color = TextSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(Icons.Default.Clear, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan400, unfocusedBorderColor = DarkCardElevated,
                    focusedContainerColor = DarkCard, unfocusedContainerColor = DarkCard,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Cyan400
                ),
                shape = RoundedCornerShape(10.dp), singleLine = true
            )
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cyan400)
                    Spacer(Modifier.height(10.dp))
                    Text("Memuat aplikasi...", color = TextSecondary, fontSize = 12.sp)
                }
            }
        } else {
            val withProfile = apps.filter { it.hasProfile }
            val withoutProfile = apps.filter { !it.hasProfile }
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (withProfile.isNotEmpty()) {
                    item { Text("Memiliki Profil (${withProfile.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = GreenAccent) }
                    items(withProfile, key = { it.packageName }) { AppItem(it) { onAppSelected(it.packageName) } }
                    item { Spacer(Modifier.height(4.dp)) }
                }
                if (withoutProfile.isNotEmpty()) {
                    item { Text("Semua Aplikasi (${withoutProfile.size})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = TextSecondary) }
                    items(withoutProfile, key = { it.packageName }) { AppItem(it) { onAppSelected(it.packageName) } }
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
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(DarkCard).clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(DarkCardElevated), contentAlignment = Alignment.Center) {
            if (icon != null) Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(34.dp))
            else Icon(Icons.Default.Android, null, tint = TextSecondary, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(app.appName, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
            Text(app.packageName, fontSize = 10.sp, color = TextSecondary, maxLines = 1)
        }
        if (app.hasProfile) Icon(Icons.Default.Bolt, null, tint = Cyan400, modifier = Modifier.size(16.dp))
        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary, modifier = Modifier.size(16.dp))
    }
}

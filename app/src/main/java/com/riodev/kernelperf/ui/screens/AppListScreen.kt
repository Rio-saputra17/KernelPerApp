package com.riodev.kernelperf.ui.screens

import android.content.pm.PackageManager
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
fun AppListScreen(
    viewModel: MainViewModel,
    onAppSelected: (String) -> Unit
) {
    val apps by viewModel.filteredApps.collectAsState()
    val isLoading by viewModel.isLoadingApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Per-App Profiles",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Cari aplikasi...", color = TextSecondary) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Clear, null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Cyan400,
                    unfocusedBorderColor = DarkCardElevated,
                    focusedContainerColor = DarkCard,
                    unfocusedContainerColor = DarkCard,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Cyan400
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Cyan400)
                    Spacer(Modifier.height(12.dp))
                    Text("Memuat aplikasi...", color = TextSecondary, fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Profil aktif section
                val withProfile = apps.filter { it.hasProfile }
                val withoutProfile = apps.filter { !it.hasProfile }

                if (withProfile.isNotEmpty()) {
                    item {
                        SectionLabel("Memiliki Profil (${withProfile.size})", GreenAccent)
                    }
                    items(withProfile, key = { it.packageName }) { app ->
                        AppItem(app = app, onClick = { onAppSelected(app.packageName) })
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                if (withoutProfile.isNotEmpty()) {
                    item {
                        SectionLabel("Semua Aplikasi (${withoutProfile.size})", TextSecondary)
                    }
                    items(withoutProfile, key = { it.packageName }) { app ->
                        AppItem(app = app, onClick = { onAppSelected(app.packageName) })
                    }
                }

                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun AppItem(app: InstalledApp, onClick: () -> Unit) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        try {
            context.packageManager.getApplicationIcon(app.packageName).toBitmap(48, 48).asImageBitmap()
        } catch (e: PackageManager.NameNotFoundException) { null }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkCard)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Icon
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkCardElevated),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Icon(Icons.Default.Android, null, tint = TextSecondary, modifier = Modifier.size(24.dp))
            }
        }

        // App Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                app.appName,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary
            )
            Text(
                app.packageName,
                fontSize = 11.sp,
                color = TextSecondary,
                maxLines = 1
            )
        }

        // Profile badge / arrow
        if (app.hasProfile) {
            Icon(
                Icons.Default.Bolt,
                null,
                tint = Cyan400,
                modifier = Modifier.size(18.dp)
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = TextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

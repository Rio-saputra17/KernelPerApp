package com.riodev.kernelperf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.riodev.kernelperf.ui.MainViewModel
import com.riodev.kernelperf.ui.screens.*
import com.riodev.kernelperf.ui.theme.*
import java.net.URLDecoder
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KernelPerfTheme { KernelPerfApp(viewModel) } }
    }
}

data class NavItem(val route: String, val icon: ImageVector, val label: String)

@Composable
fun KernelPerfApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route
    val bottomRoutes = listOf("home", "profile_default", "apps")

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            if (currentRoute in bottomRoutes) {
                val items = listOf(
                    NavItem("home", Icons.Default.Dashboard, "Dashboard"),
                    NavItem("profile_default", Icons.Default.Tune, "Profil"),
                    NavItem("apps", Icons.Default.Apps, "Aplikasi")
                )
                NavigationBar(containerColor = DarkSurface, tonalElevation = 0.dp) {
                    items.forEach { item ->
                        val selected = currentRoute == item.route
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, contentDescription = item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan400, selectedTextColor = Cyan400,
                                unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary,
                                indicatorColor = Cyan400.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(160)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(160)) },
            popExitTransition = { fadeOut(tween(120)) }
        ) {
            composable("home") { HomeScreen(viewModel) }
            composable("profile_default") { DefaultProfileScreen(viewModel) }
            composable("apps") {
                AppListScreen(viewModel = viewModel, onAppSelected = { pkg ->
                    navController.navigate("app_profile/${URLEncoder.encode(pkg, "UTF-8")}")
                })
            }
            composable(
                route = "app_profile/{packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { back ->
                val pkg = URLDecoder.decode(back.arguments?.getString("packageName") ?: "", "UTF-8")
                ProfileEditorScreen(packageName = pkg, viewModel = viewModel, onBack = { navController.popBackStack() })
            }
        }
    }
}

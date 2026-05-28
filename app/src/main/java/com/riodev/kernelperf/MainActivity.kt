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
    private val vm: MainViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Theme { App(vm) } }
    }
}

data class NavItem(val route: String, val icon: ImageVector, val label: String)

@Composable
fun App(vm: MainViewModel) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val cur = entry?.destination?.route
    val bottomRoutes = listOf("home", "profile", "games")

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            if (cur in bottomRoutes) {
                val items = listOf(
                    NavItem("home", Icons.Default.Dashboard, "Dashboard"),
                    NavItem("profile", Icons.Default.Tune, "Profil"),
                    NavItem("games", Icons.Default.Apps, "Game")
                )
                NavigationBar(containerColor = Card, tonalElevation = 0.dp) {
                    items.forEach { item ->
                        val sel = cur == item.route
                        NavigationBarItem(
                            selected = sel,
                            onClick = {
                                nav.navigate(item.route) {
                                    popUpTo("home") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(item.icon, item.label) },
                            label = { Text(item.label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Cyan, selectedTextColor = Cyan,
                                unselectedIconColor = TextSec, unselectedTextColor = TextSec,
                                indicatorColor = Cyan.copy(alpha = 0.15f)
                            )
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            navController = nav,
            startDestination = "home",
            modifier = Modifier.padding(pad),
            enterTransition = { fadeIn(tween(150)) },
            exitTransition = { fadeOut(tween(100)) },
            popEnterTransition = { fadeIn(tween(150)) },
            popExitTransition = { fadeOut(tween(100)) }
        ) {
            composable("home") { HomeScreen(vm) }
            composable("profile") { ProfileScreen(vm) }
            composable("games") {
                AppListScreen(vm) { pkg ->
                    nav.navigate("game/${URLEncoder.encode(pkg, "UTF-8")}")
                }
            }
            composable(
                "game/{pkg}",
                arguments = listOf(navArgument("pkg") { type = NavType.StringType })
            ) { back ->
                val pkg = URLDecoder.decode(back.arguments?.getString("pkg") ?: "", "UTF-8")
                GameProfileScreen(pkg = pkg, vm = vm, onBack = { nav.popBackStack() })
            }
        }
    }
}

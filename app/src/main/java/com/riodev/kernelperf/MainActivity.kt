package com.riodev.kernelperf

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
        setContent {
            KernelPerfTheme {
                KernelPerfApp(viewModel)
            }
        }
    }
}

@Composable
fun KernelPerfApp(viewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in listOf("home", "apps")

    Scaffold(
        containerColor = DarkBg,
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(300, easing = EaseOutCubic)
                ),
                exit = slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(200, easing = EaseInCubic)
                )
            ) {
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo("home") { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(padding),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it },
                    animationSpec = tween(320, easing = EaseOutCubic)
                ) + fadeIn(tween(320))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 3 },
                    animationSpec = tween(320, easing = EaseInCubic)
                ) + fadeOut(tween(200))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 3 },
                    animationSpec = tween(320, easing = EaseOutCubic)
                ) + fadeIn(tween(320))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it },
                    animationSpec = tween(320, easing = EaseInCubic)
                ) + fadeOut(tween(200))
            }
        ) {
            composable("home") { HomeScreen(viewModel) }

            composable("apps") {
                AppListScreen(
                    viewModel = viewModel,
                    onAppSelected = { packageName ->
                        val encoded = URLEncoder.encode(packageName, "UTF-8")
                        navController.navigate("profile/$encoded")
                    }
                )
            }

            composable(
                route = "profile/{packageName}",
                arguments = listOf(navArgument("packageName") { type = NavType.StringType })
            ) { backStackEntry ->
                val encoded = backStackEntry.arguments?.getString("packageName") ?: return@composable
                val packageName = URLDecoder.decode(encoded, "UTF-8")
                ProfileEditorScreen(
                    packageName = packageName,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}

data class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
)

@Composable
fun BottomNavBar(currentRoute: String?, onNavigate: (String) -> Unit) {
    val items = listOf(
        BottomNavItem("home", Icons.Default.Dashboard, "Dashboard"),
        BottomNavItem("apps", Icons.Default.Apps, "Aplikasi")
    )

    NavigationBar(
        containerColor = DarkSurface,
        tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val isSelected = currentRoute == item.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(item.route) },
                icon = {
                    val scale by animateFloatAsState(
                        targetValue = if (isSelected) 1.1f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "icon_scale"
                    )
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        modifier = Modifier.size((24 * scale).dp)
                    )
                },
                label = {
                    AnimatedContent(
                        targetState = isSelected,
                        transitionSpec = { fadeIn(tween(150)) togetherWith fadeOut(tween(150)) },
                        label = "label"
                    ) { selected ->
                        Text(item.label, style = if (selected) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall)
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Cyan400,
                    selectedTextColor = Cyan400,
                    unselectedIconColor = TextSecondary,
                    unselectedTextColor = TextSecondary,
                    indicatorColor = Cyan400.copy(alpha = 0.15f)
                )
            )
        }
    }
}

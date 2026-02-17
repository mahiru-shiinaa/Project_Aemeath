package com.aemeath.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aemeath.app.navigation.Screen
import com.aemeath.app.ui.auth.SetupScreen
import com.aemeath.app.ui.auth.UnlockScreen
import com.aemeath.app.ui.home.HomeScreen
import com.aemeath.app.ui.main.MainViewModel
import com.aemeath.app.ui.theme.AemeathTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val mainViewModel: MainViewModel = hiltViewModel()
            val theme by mainViewModel.theme.collectAsStateWithLifecycle(initialValue = "system")
            val isSetupComplete by mainViewModel.isSetupComplete.collectAsStateWithLifecycle(initialValue = null)

            val isDark = when (theme) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }

            AemeathTheme(darkTheme = isDark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    // Đợi check setup xong mới navigate
                    if (isSetupComplete != null) {
                        AemeathNavigation(isSetupComplete = isSetupComplete!!)
                    }
                }
            }
        }
    }
}

@Composable
fun AemeathNavigation(isSetupComplete: Boolean) {
    val navController = rememberNavController()

    val startDestination = if (isSetupComplete) Screen.Unlock.route else Screen.Setup.route

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ─── Auth ─────────────────────────────────────────────────────────────
        composable(Screen.Setup.route) {
            SetupScreen(
                onSetupComplete = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Setup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Unlock.route) {
            UnlockScreen(
                onUnlocked = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Unlock.route) { inclusive = true }
                    }
                }
            )
        }

        // ─── Main ─────────────────────────────────────────────────────────────
        composable(Screen.Home.route) {
            HomeScreen(navController = navController)
        }

        // TODO Phase 2: AccountList, AddAccount, EditAccount, Settings, LanSync, Backup
    }
}
package com.odom.applimit.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun AppNavGraph() {
    val context = LocalContext.current
    val navController = rememberNavController()
    val startDestination = remember {
        if (checkAllPermissionsGranted(context)) "home" else "permissions"
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("permissions") {
            PermissionSetupScreen(
                onAllGranted = {
                    navController.navigate("home") {
                        popUpTo("permissions") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            HomeScreen(onAddLimit = { navController.navigate("add_limit") })
        }
        composable("add_limit") {
            AddLimitScreen(onBack = { navController.popBackStack() })
        }
    }
}

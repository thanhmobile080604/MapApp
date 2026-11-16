package com.example.mapapp.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.mapapp.ui.HomeScreen
import com.example.mapapp.ui.MainScreen
import com.example.mapapp.ui.SettingsScreen
import com.example.mapapp.viewmodel.MainViewModel

/**
 * Quản lý Navigation Graph toàn bộ ứng dụng.
 */
@Composable
fun AppNavigation(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        // Màn hình 1: Trang chủ (Home)
        composable(route = Screen.Home.route) {
            HomeScreen(
                onNavigateToMain = {
                    navController.navigate(Screen.Main.route)
                },
                onNavigateToSettings = {
                    navController.navigate(Screen.Settings.route)
                }
            )
        }

        // Màn hình 2: Bản đồ dẫn đường (MainScreen)
        composable(route = Screen.Main.route) {
            MainScreen(
                viewModel = viewModel,
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }

        // Màn hình 3: Cài đặt & Thông tin (SettingsScreen)
        composable(route = Screen.Settings.route) {
            SettingsScreen(
                onBackClick = {
                    navController.popBackStack()
                }
            )
        }
    }
}

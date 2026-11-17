package com.example.mapapp.navigation

/**
 * Định nghĩa các Route điều hướng trong ứng dụng.
 */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Main : Screen("main")
    data object Settings : Screen("settings")
    data object ScreenA : Screen("screen_a")
    data object ScreenB : Screen("screen_b")
}

package com.aemeath.app.navigation

sealed class Screen(val route: String) {
    // Auth
    object Setup : Screen("setup")
    object Unlock : Screen("unlock")

    // Main
    object Home : Screen("home")
    object Settings : Screen("settings")

    // Accounts
    object AccountList : Screen("account_list/{webAppId}") {
        fun createRoute(webAppId: Long) = "account_list/$webAppId"
    }
    object AddAccount : Screen("add_account?webAppId={webAppId}") {
        fun createRoute(webAppId: Long? = null) =
            if (webAppId != null) "add_account?webAppId=$webAppId" else "add_account"
    }
    object EditAccount : Screen("edit_account/{accountId}") {
        fun createRoute(accountId: Long) = "edit_account/$accountId"
    }

    // LAN Sync
    object LanSync : Screen("lan_sync")
    object LanSyncHost : Screen("lan_sync_host")
    object LanSyncLog : Screen("lan_sync_log")

    // Backup
    object Backup : Screen("backup")

    // Change Password
    object ChangePassword : Screen("change_password")
}
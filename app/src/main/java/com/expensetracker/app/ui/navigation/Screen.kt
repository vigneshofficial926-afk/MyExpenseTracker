package com.expensetracker.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val selectedIcon: ImageVector? = null,
    val unselectedIcon: ImageVector? = null
) {
    data object Home : Screen(
        route = "home",
        title = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    )

    data object Transactions : Screen(
        route = "transactions",
        title = "Transaction",
        selectedIcon = Icons.Filled.ReceiptLong,
        unselectedIcon = Icons.Outlined.ReceiptLong
    )

    data object Reports : Screen(
        route = "reports",
        title = "Reports",
        selectedIcon = Icons.Filled.BarChart,
        unselectedIcon = Icons.Outlined.BarChart
    )

    data object Budget : Screen(
        route = "budget",
        title = "Budget",
        selectedIcon = Icons.Filled.AccountBalanceWallet,
        unselectedIcon = Icons.Outlined.AccountBalanceWallet
    )

    data object Settings : Screen(
        route = "settings",
        title = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )

    // Sub-screens
    data object AddEditTransaction : Screen(
        route = "add_edit_transaction?transactionId={transactionId}&type={type}",
        title = "Transaction"
    ) {
        fun createRoute(transactionId: Long? = null, type: String? = null): String {
            val txParam = if (transactionId != null) "transactionId=$transactionId" else "transactionId="
            val typeParam = if (type != null) "type=$type" else "type="
            return "add_edit_transaction?$txParam&$typeParam"
        }
    }

    data object SavingsGoals : Screen(
        route = "savings_goals",
        title = "Savings Goals"
    )

    data object Recurring : Screen(
        route = "recurring_transactions",
        title = "Recurring Bills"
    )

    data object Categories : Screen(
        route = "category_management",
        title = "Categories"
    )

    companion object {
        val bottomNavItems = listOf(
            Home,
            Transactions,
            Reports,
            Budget,
            Settings
        )
    }
}

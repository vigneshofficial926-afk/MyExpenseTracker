package com.expensetracker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.ui.navigation.BottomNavBar
import com.expensetracker.app.ui.navigation.Screen
import com.expensetracker.app.ui.screens.budget.BudgetScreen
import com.expensetracker.app.ui.screens.budget.BudgetViewModel
import com.expensetracker.app.ui.screens.categories.CategoryManagementScreen
import com.expensetracker.app.ui.screens.categories.CategoryViewModel
import com.expensetracker.app.ui.screens.dashboard.DashboardScreen
import com.expensetracker.app.ui.screens.dashboard.DashboardViewModel
import com.expensetracker.app.ui.screens.goals.SavingsGoalsScreen
import com.expensetracker.app.ui.screens.goals.SavingsGoalsViewModel
import com.expensetracker.app.ui.screens.recurring.RecurringTransactionsScreen
import com.expensetracker.app.ui.screens.recurring.RecurringViewModel
import com.expensetracker.app.ui.screens.reports.ReportsScreen
import com.expensetracker.app.ui.screens.reports.ReportsViewModel
import com.expensetracker.app.ui.screens.settings.PinLockScreen
import com.expensetracker.app.ui.screens.settings.SettingsScreen
import com.expensetracker.app.ui.screens.settings.SettingsViewModel
import com.expensetracker.app.ui.screens.settings.ThemeMode
import com.expensetracker.app.ui.screens.transaction.AddEditTransactionSheet
import com.expensetracker.app.ui.screens.transaction.AddEditTransactionViewModel
import com.expensetracker.app.ui.screens.transaction.TransactionListScreen
import com.expensetracker.app.ui.screens.transaction.TransactionListViewModel
import com.expensetracker.app.ui.theme.MyExpenseTrackerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as MyExpenseTrackerApp
        val repository = app.repository
        val database = app.database

        setContent {
            val settingsViewModel: SettingsViewModel = remember {
                SettingsViewModel(repository, database, applicationContext)
            }
            val settingsState by settingsViewModel.uiState.collectAsState()

            val isDark = when (settingsState.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            MyExpenseTrackerTheme(darkTheme = isDark) {
                var isUnlocked by remember {
                    mutableStateOf(!settingsState.isPinLockEnabled || settingsState.userPin.isEmpty())
                }

                if (!isUnlocked && settingsState.isPinLockEnabled && settingsState.userPin.isNotEmpty()) {
                    PinLockScreen(
                        correctPin = settingsState.userPin,
                        onPinSuccess = { isUnlocked = true }
                    )
                } else {
                    MainAppScaffold(
                        app = app,
                        settingsViewModel = settingsViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun MainAppScaffold(
    app: MyExpenseTrackerApp,
    settingsViewModel: SettingsViewModel
) {
    val repository = app.repository
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val isBottomBarVisible = currentRoute in Screen.bottomNavItems.map { it.route }

    // Retain ViewModels at Scaffold scope so bottom tab switching does not recreate them.
    // This keeps Room database flows hot and cached for 0ms instantaneous navigation.
    val dashboardViewModel = remember { DashboardViewModel(repository) }
    val listViewModel = remember { TransactionListViewModel(repository) }
    val reportsViewModel = remember { ReportsViewModel(repository) }
    val budgetViewModel = remember { BudgetViewModel(repository) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (isBottomBarVisible) {
                BottomNavBar(navController = navController)
            }
        },
        floatingActionButton = {
            if (isBottomBarVisible) {
                FloatingActionButton(
                    onClick = {
                        navController.navigate(Screen.AddEditTransaction.createRoute(type = TransactionType.EXPENSE.name))
                    },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Transaction"
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(bottom = if (isBottomBarVisible) 0.dp else innerPadding.calculateBottomPadding()),
            enterTransition = { EnterTransition.None },
            exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None },
            popExitTransition = { ExitTransition.None }
        ) {
            // 1. Home Dashboard
            composable(Screen.Home.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToTransactions = { navController.navigate(Screen.Transactions.route) },
                    onNavigateToAddTransaction = { type ->
                        navController.navigate(Screen.AddEditTransaction.createRoute(type = type.name))
                    },
                    onNavigateToEditTransaction = { id ->
                        navController.navigate(Screen.AddEditTransaction.createRoute(transactionId = id))
                    }
                )
            }

            // 2. Transactions History
            composable(Screen.Transactions.route) {
                TransactionListScreen(
                    viewModel = listViewModel,
                    onNavigateToEditTransaction = { id ->
                        navController.navigate(Screen.AddEditTransaction.createRoute(transactionId = id))
                    }
                )
            }

            // 3. Reports & Analytics
            composable(Screen.Reports.route) {
                ReportsScreen(viewModel = reportsViewModel)
            }

            // 4. Budget Management
            composable(Screen.Budget.route) {
                BudgetScreen(viewModel = budgetViewModel)
            }

            // 5. Settings
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                    onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) }
                )
            }

            // 6. Add/Edit Transaction Sub-screen
            composable(
                route = Screen.AddEditTransaction.route,
                arguments = listOf(
                    navArgument("transactionId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument("type") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val idStr = backStackEntry.arguments?.getString("transactionId")
                val typeStr = backStackEntry.arguments?.getString("type")
                val txId = idStr?.toLongOrNull()
                val type = if (!typeStr.isNullOrBlank()) TransactionType.fromString(typeStr) else null

                val addEditViewModel: AddEditTransactionViewModel = remember(txId, type) {
                    AddEditTransactionViewModel(repository, txId, type)
                }
                AddEditTransactionSheet(
                    viewModel = addEditViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 7. Savings Goals Sub-screen
            composable(Screen.SavingsGoals.route) {
                val goalsViewModel: SavingsGoalsViewModel = remember {
                    SavingsGoalsViewModel(repository)
                }
                SavingsGoalsScreen(
                    viewModel = goalsViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 8. Recurring Bills Sub-screen
            composable(Screen.Recurring.route) {
                val recurringViewModel: RecurringViewModel = remember {
                    RecurringViewModel(repository)
                }
                RecurringTransactionsScreen(
                    viewModel = recurringViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }

            // 9. Categories Sub-screen
            composable(Screen.Categories.route) {
                val catViewModel: CategoryViewModel = remember {
                    CategoryViewModel(repository)
                }
                CategoryManagementScreen(
                    viewModel = catViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}

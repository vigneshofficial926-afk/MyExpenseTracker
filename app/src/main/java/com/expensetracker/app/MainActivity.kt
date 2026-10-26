package com.expensetracker.app

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.ui.navigation.BottomNavBar
import com.expensetracker.app.ui.navigation.Screen
import com.expensetracker.app.ui.screens.budget.*
import com.expensetracker.app.ui.screens.categories.*
import com.expensetracker.app.ui.screens.dashboard.*
import com.expensetracker.app.ui.screens.goals.*
import com.expensetracker.app.ui.screens.recurring.*
import com.expensetracker.app.ui.screens.reports.*
import com.expensetracker.app.ui.screens.settings.*
import com.expensetracker.app.ui.screens.transaction.*
import com.expensetracker.app.ui.theme.MyExpenseTrackerTheme

class MainActivity : FragmentActivity() {

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as MyExpenseTrackerApp
        SettingsViewModel.Factory(app.repository, app.database, applicationContext)
    }
    private var stoppedAt = 0L

    override fun onStop() { super.onStop(); stoppedAt = SystemClock.elapsedRealtime() }
    override fun onStart() {
        super.onStart()
        if (stoppedAt != 0L && SystemClock.elapsedRealtime() - stoppedAt > LOCK_TIMEOUT_MS) settingsViewModel.lock()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as MyExpenseTrackerApp

        setContent {
            val settings by settingsViewModel.uiState.collectAsState()
            val unlocked by settingsViewModel.sessionUnlocked.collectAsState()
            val isDark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true
            }
            MyExpenseTrackerTheme(darkTheme = isDark) {
                val lockActive = settings.isPinLockEnabled && settings.userPin.isNotEmpty()
                if (lockActive && !unlocked) {
                    val canBio = settings.isBiometricEnabled && biometricAvailable()
                    LaunchedEffect(canBio) { if (canBio) promptBiometric { settingsViewModel.unlock() } }
                    PinLockScreen(
                        verifyPin = settingsViewModel::verifyPin,
                        onPinSuccess = settingsViewModel::unlock,
                        onBiometricClick = if (canBio) ({ promptBiometric { settingsViewModel.unlock() } }) else null
                    )
                } else MainAppScaffold(app, settingsViewModel)
            }
        }
    }

    private fun biometricAvailable() = BiometricManager.from(this)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    private fun promptBiometric(onSuccess: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
            })
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Expense Tracker").setNegativeButtonText("Use PIN")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build()
        )
    }

    companion object { private const val LOCK_TIMEOUT_MS = 30_000L }
}

@Composable
fun MainAppScaffold(app: MyExpenseTrackerApp, settingsViewModel: SettingsViewModel) {
    val repository = app.repository
    val navController = rememberNavController()
    val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route

    val isBottomBarVisible = currentRoute in Screen.bottomNavItems.map { it.route }
    val isFabVisible = currentRoute == Screen.Home.route || currentRoute == Screen.Transactions.route

    // Real ViewModels: survive rotation, cleared properly
    val dashboardViewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory(repository))
    val listViewModel: TransactionListViewModel = viewModel(factory = TransactionListViewModel.Factory(repository))
    val reportsViewModel: ReportsViewModel = viewModel(factory = ReportsViewModel.Factory(repository))
    val budgetViewModel: BudgetViewModel = viewModel(factory = BudgetViewModel.Factory(repository))

    val navigateTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true; restoreState = true
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = { if (isBottomBarVisible) BottomNavBar(navController) },
        floatingActionButton = {
            if (isFabVisible) FloatingActionButton(
                onClick = { navController.navigate(Screen.AddEditTransaction.createRoute(type = TransactionType.EXPENSE.name)) },
                shape = CircleShape, containerColor = MaterialTheme.colorScheme.primary, contentColor = Color(0xFF030712),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp)
            ) { Icon(Icons.Default.Add, "Add Transaction") }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController, startDestination = Screen.Home.route,
            // correct inset handling: no more 90.dp hacks, no double padding in nested Scaffolds
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding),
            enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
            popEnterTransition = { EnterTransition.None }, popExitTransition = { ExitTransition.None }
        ) {
            composable(Screen.Home.route) {
                DashboardScreen(
                    viewModel = dashboardViewModel,
                    onNavigateToTransactions = { navigateTab(Screen.Transactions.route) },
                    onNavigateToAddTransaction = { navController.navigate(Screen.AddEditTransaction.createRoute(type = it.name)) },
                    onNavigateToEditTransaction = { navController.navigate(Screen.AddEditTransaction.createRoute(transactionId = it)) }
                )
            }
            composable(Screen.Transactions.route) {
                TransactionListScreen(listViewModel) { navController.navigate(Screen.AddEditTransaction.createRoute(transactionId = it)) }
            }
            composable(Screen.Reports.route) { ReportsScreen(reportsViewModel) }
            composable(Screen.Budget.route) { BudgetScreen(budgetViewModel) }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    viewModel = settingsViewModel,
                    onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                    onNavigateToRecurring = { navController.navigate(Screen.Recurring.route) },
                    onNavigateToGoals = { navController.navigate(Screen.SavingsGoals.route) }
                )
            }
            composable(
                route = Screen.AddEditTransaction.route,
                arguments = listOf(
                    navArgument("transactionId") { type = NavType.StringType; nullable = true; defaultValue = null },
                    navArgument("type") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { entry ->
                val txId = entry.arguments?.getString("transactionId")?.toLongOrNull()
                val type = entry.arguments?.getString("type")?.takeIf { it.isNotBlank() }?.let { TransactionType.fromString(it) }
                val vm: AddEditTransactionViewModel = viewModel(
                    key = "tx_${txId}_$type", factory = AddEditTransactionViewModel.Factory(repository, txId, type)
                )
                AddEditTransactionSheet(vm) { navController.popBackStack() }
            }
            composable(Screen.SavingsGoals.route) {
                val vm: SavingsGoalsViewModel = viewModel(factory = SavingsGoalsViewModel.Factory(repository))
                SavingsGoalsScreen(vm) { navController.popBackStack() }
            }
            composable(Screen.Recurring.route) {
                val vm: RecurringViewModel = viewModel(factory = RecurringViewModel.Factory(repository))
                RecurringTransactionsScreen(vm) { navController.popBackStack() }
            }
            composable(Screen.Categories.route) {
                val vm: CategoryViewModel = viewModel(factory = CategoryViewModel.Factory(repository))
                CategoryManagementScreen(vm) { navController.popBackStack() }
            }
        }
    }
}

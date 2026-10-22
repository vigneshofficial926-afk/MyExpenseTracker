package com.expensetracker.app.ui.screens.dashboard

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.BudgetEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.FinancialCalculator
import com.expensetracker.app.ui.components.ChartSlice
import com.expensetracker.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class DashboardUiState(
    val totalBalance: Double = 0.0,
    val monthIncome: Double = 0.0,
    val monthExpense: Double = 0.0,
    val monthlyBudget: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val budgetUtilization: Float = 0f,
    val budgetAlertMessage: String? = null,
    val todayExpense: Double = 0.0,
    val thisWeekExpense: Double = 0.0,
    val recentTransactions: List<TransactionEntity> = emptyList(),
    val categoryBreakdown: List<ChartSlice> = emptyList(),
    val isLoading: Boolean = false
)

class DashboardViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val currentMonthYear = YearMonth.now().format(DateTimeFormatter.ofPattern("MM-yyyy"))
    private val monthRange = DateUtils.getMonthStartAndEnd()
    private val todayRange = DateUtils.getTodayStartAndEnd()
    private val weekRange = DateUtils.getWeekStartAndEnd()

    private val categoryColors = listOf(
        Color(0xFFF97316), Color(0xFF3B82F6), Color(0xFF10B981),
        Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFEAB308),
        Color(0xFF06B6D4), Color(0xFFEF4444), Color(0xFF14B8A6)
    )

    val uiState: StateFlow<DashboardUiState> = combine(
        repository.allTransactions,
        repository.allAccounts,
        repository.getBudgetsForMonth(currentMonthYear)
    ) { transactions, accounts, budgets ->
        // Total balance
        val balance = FinancialCalculator.calculateTotalBalance(accounts, transactions)

        // Month transactions
        val monthTransactions = transactions.filter {
            it.dateTime in monthRange.first..monthRange.second
        }
        val monthIncome = FinancialCalculator.calculateMonthlyIncome(monthTransactions)
        val monthExpense = FinancialCalculator.calculateMonthlyExpenses(monthTransactions)

        // Today & week expenses
        val todayExpense = monthTransactions
            .filter { it.dateTime in todayRange.first..todayRange.second && it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }
            .sumOf { it.amount }

        val weekExpense = monthTransactions
            .filter { it.dateTime in weekRange.first..weekRange.second && it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }
            .sumOf { it.amount }

        // Budget
        val overallBudget = budgets.find { it.categoryId == null }?.budgetAmount ?: 0.0
        val remainingBudget = if (overallBudget > 0.0) FinancialCalculator.calculateBudgetRemaining(overallBudget, monthExpense) else 0.0
        val budgetUtil = FinancialCalculator.calculateBudgetUtilization(overallBudget, monthExpense)

        val budgetAlert = when {
            overallBudget > 0.0 && budgetUtil >= 100f -> "Warning: You have reached 100% of your monthly budget!"
            overallBudget > 0.0 && budgetUtil >= 80f -> "Heads up: You have utilized ${String.format("%.0f", budgetUtil)}% of your monthly budget."
            else -> null
        }

        // Category breakdown for current month expenses
        val expenseByCategory = monthTransactions
            .filter { it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }
            .groupBy { it.categoryName }
            .mapValues { entry -> entry.value.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }

        val slices = expenseByCategory.mapIndexed { idx, (catName, amount) ->
            ChartSlice(
                label = catName,
                value = amount,
                color = categoryColors[idx % categoryColors.size]
            )
        }

        DashboardUiState(
            totalBalance = balance,
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            monthlyBudget = overallBudget,
            remainingBudget = remainingBudget,
            budgetUtilization = budgetUtil,
            budgetAlertMessage = budgetAlert,
            todayExpense = todayExpense,
            thisWeekExpense = weekExpense,
            recentTransactions = transactions.take(6),
            categoryBreakdown = slices
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = DashboardUiState(isLoading = false)
    )

    init {
        // Trigger auto-processing of recurring transactions on startup
        viewModelScope.launch {
            repository.processDueRecurringTransactions()
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(repository) as T
        }
    }
}

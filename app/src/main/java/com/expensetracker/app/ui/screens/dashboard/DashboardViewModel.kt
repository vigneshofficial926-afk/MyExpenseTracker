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
import com.expensetracker.app.ui.components.toSlices
import com.expensetracker.app.util.DateUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val keyFmt = DateTimeFormatter.ofPattern("MM-yyyy")
    private val palette = listOf(
        Color(0xFFF97316), Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFF8B5CF6),
        Color(0xFFEC4899), Color(0xFFEAB308), Color(0xFF06B6D4), Color(0xFFEF4444), Color(0xFF14B8A6)
    )

    val uiState: StateFlow<DashboardUiState> = DateUtils.todayFlow().flatMapLatest { today ->
        combine(
            repository.allTransactions, repository.allAccounts,
            repository.getBudgetsForMonth(YearMonth.from(today).format(keyFmt))
        ) { txs, accounts, budgets -> compute(today, txs, accounts, budgets) }
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    private fun compute(today: LocalDate, txs: List<TransactionEntity>, accounts: List<AccountEntity>, budgets: List<BudgetEntity>): DashboardUiState {
        val (ms, me) = DateUtils.getMonthStartAndEnd(YearMonth.from(today))
        val (ds, de) = DateUtils.getTodayStartAndEnd(today)
        val (ws, we) = DateUtils.getWeekStartAndEnd(today = today)

        val monthTxs = txs.filter { it.dateTime in ms..me }
        val monthExpense = FinancialCalculator.calculateMonthlyExpenses(monthTxs)
        // week/today come from ALL transactions (a week can straddle two months) and net out refunds like the month does
        val todayExpense = FinancialCalculator.calculateMonthlyExpenses(txs.filter { it.dateTime in ds..de })
        val weekExpense = FinancialCalculator.calculateMonthlyExpenses(txs.filter { it.dateTime in ws..we })

        val budget = budgets.firstOrNull { it.categoryId == null }?.budgetAmount ?: 0.0
        val util = FinancialCalculator.calculateBudgetUtilization(budget, monthExpense)
        val alert = when {
            budget > 0 && util >= 100f -> "Warning: You have reached 100% of your monthly budget!"
            budget > 0 && util >= 80f -> "Heads up: You have utilized ${"%.0f".format(util)}% of your monthly budget."
            else -> null
        }
        val slices = monthTxs.filter { it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }
            .groupBy { it.categoryName }.mapValues { e -> e.value.sumOf { it.amount } }
            .toList().sortedByDescending { it.second }.toSlices(palette)

        return DashboardUiState(
            totalBalance = FinancialCalculator.calculateTotalBalance(accounts, txs),
            monthIncome = FinancialCalculator.calculateMonthlyIncome(monthTxs),
            monthExpense = monthExpense, monthlyBudget = budget,
            remainingBudget = if (budget > 0) FinancialCalculator.calculateBudgetRemaining(budget, monthExpense) else 0.0,
            budgetUtilization = util, budgetAlertMessage = alert,
            todayExpense = todayExpense, thisWeekExpense = weekExpense,
            recentTransactions = txs.take(6), categoryBreakdown = slices
        )
    }

    init { viewModelScope.launch { repository.processDueRecurringTransactions() } }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = DashboardViewModel(repository) as T
    }
}

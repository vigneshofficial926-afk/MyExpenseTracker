package com.expensetracker.app.ui.screens.reports

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.BudgetEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.FinancialCalculator
import com.expensetracker.app.ui.components.ChartSlice
import com.expensetracker.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

data class ReportsUiState(
    val currentMonthYear: YearMonth = YearMonth.now(),
    val totalIncome: Double = 0.0,
    val totalExpense: Double = 0.0,
    val netSavings: Double = 0.0,
    val previousMonthExpense: Double = 0.0,
    val monthExpenseChangePercent: Double = 0.0, // e.g. +12.5% or -8.0%
    val weeklyDays: List<String> = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"),
    val weeklyExpenses: List<Double> = List(7) { 0.0 },
    val todayDayIndex: Int = 0,
    val annualMonths: List<String> = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
    val annualExpenses: List<Double> = List(12) { 0.0 },
    val categorySlices: List<ChartSlice> = emptyList(),
    val paymentMethodSlices: List<ChartSlice> = emptyList(),
    val highestSpendingCategories: List<Pair<String, Double>> = emptyList(),
    val currentBudget: Double = 0.0
)

class ReportsViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val colors = listOf(
        Color(0xFFF97316), Color(0xFF3B82F6), Color(0xFF10B981),
        Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFEAB308),
        Color(0xFF06B6D4), Color(0xFFEF4444), Color(0xFF14B8A6),
        Color(0xFFD97706), Color(0xFF6366F1), Color(0xFFA855F7)
    )

    private val currentMonth = YearMonth.now()

    val uiState: StateFlow<ReportsUiState> = combine(
        repository.allTransactions,
        repository.getBudgetsForMonth(currentMonth.format(DateTimeFormatter.ofPattern("MM-yyyy")))
    ) { transactions, budgets ->
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()

        // 1. Current Month & Previous Month
        val (monthStart, monthEnd) = DateUtils.getMonthStartAndEnd(currentMonth)
        val prevMonth = currentMonth.minusMonths(1)
        val (prevMonthStart, prevMonthEnd) = DateUtils.getMonthStartAndEnd(prevMonth)

        val currentMonthTxs = transactions.filter { it.dateTime in monthStart..monthEnd }
        val prevMonthTxs = transactions.filter { it.dateTime in prevMonthStart..prevMonthEnd }

        val curIncome = FinancialCalculator.calculateMonthlyIncome(currentMonthTxs)
        val curExpense = FinancialCalculator.calculateMonthlyExpenses(currentMonthTxs)
        val prevExpense = FinancialCalculator.calculateMonthlyExpenses(prevMonthTxs)

        val netSavings = curIncome - curExpense
        val expenseChangePct = if (prevExpense > 0.0) {
            ((curExpense - prevExpense) / prevExpense) * 100.0
        } else 0.0

        // 2. Weekly Daily Expenses (Mon to Sun)
        val startOfWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekExpensesList = (0..6).map { dayOffset ->
            val dayDate = startOfWeek.plusDays(dayOffset.toLong())
            val dayStart = dayDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = dayDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

            transactions.filter {
                it.dateTime in dayStart..dayEnd &&
                it.type == TransactionType.EXPENSE &&
                !it.isCreditCardBillPayment
            }.sumOf { it.amount }
        }
        val currentDayIndex = today.dayOfWeek.value - 1 // 0 for Mon, 6 for Sun

        // 3. Annual Monthly Expenses (Jan to Dec of current year)
        val annualExpensesList = (1..12).map { monthNum ->
            val ym = YearMonth.of(today.year, monthNum)
            val (s, e) = DateUtils.getMonthStartAndEnd(ym)
            transactions.filter {
                it.dateTime in s..e &&
                it.type == TransactionType.EXPENSE &&
                !it.isCreditCardBillPayment
            }.sumOf { it.amount }
        }

        // 4. Category-Wise Spending
        val catMap = currentMonthTxs
            .filter { it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }
            .groupBy { it.categoryName }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .toList()
            .sortedByDescending { it.second }

        val categorySlices = catMap.mapIndexed { index, (cat, amt) ->
            ChartSlice(cat, amt, colors[index % colors.size])
        }

        // 5. Payment-Method-Wise Spending
        val pmMap = currentMonthTxs
            .filter { it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }
            .groupBy { it.paymentMethod.displayName }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .toList()
            .sortedByDescending { it.second }

        val pmSlices = pmMap.mapIndexed { index, (method, amt) ->
            ChartSlice(method, amt, colors[(index + 3) % colors.size])
        }

        // 6. Overall Budget
        val overallBudget = budgets.find { it.categoryId == null }?.budgetAmount ?: 0.0

        ReportsUiState(
            currentMonthYear = currentMonth,
            totalIncome = curIncome,
            totalExpense = curExpense,
            netSavings = netSavings,
            previousMonthExpense = prevExpense,
            monthExpenseChangePercent = expenseChangePct,
            weeklyExpenses = weekExpensesList,
            todayDayIndex = currentDayIndex,
            annualExpenses = annualExpensesList,
            categorySlices = categorySlices,
            paymentMethodSlices = pmSlices,
            highestSpendingCategories = catMap.take(5),
            currentBudget = overallBudget
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = ReportsUiState()
    )

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ReportsViewModel(repository) as T
        }
    }
}

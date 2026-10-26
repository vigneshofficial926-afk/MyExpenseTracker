package com.expensetracker.app.ui.screens.budget

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.BudgetEntity
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.FinancialCalculator
import com.expensetracker.app.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.Dispatchers

data class CategoryBudgetUiModel(
    val category: CategoryEntity,
    val budgetAmount: Double,
    val spentAmount: Double,
    val remainingAmount: Double,
    val utilizationPercent: Float
)

data class BudgetUiState(
    val selectedMonthYear: YearMonth = YearMonth.now(),
    val overallBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val totalIncome: Double = 0.0,
    val remainingBudget: Double = 0.0,
    val actualBankBalance: Double = 0.0,
    val overallUtilization: Float = 0f,
    val categoryBudgets: List<CategoryBudgetUiModel> = emptyList(),
    val availableMonths: List<YearMonth> = emptyList(),
    val isSettingBudgetDialogOpen: Boolean = false,
    val selectedCategoryForBudget: CategoryEntity? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel(private val repository: ExpenseRepository) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _isSettingDialogOpen = MutableStateFlow(false)
    private val _selectedCatForBudget = MutableStateFlow<CategoryEntity?>(null)
    private val keyFmt = DateTimeFormatter.ofPattern("MM-yyyy")

    private val budgetsForMonth = _selectedMonth.flatMapLatest { repository.getBudgetsForMonth(it.format(keyFmt)) }

    val uiState: StateFlow<BudgetUiState> = combine(
        _selectedMonth, repository.allTransactions, repository.allAccounts, _isSettingDialogOpen, _selectedCatForBudget
    ) { month, txs, accounts, isDialog, cat ->
        val (s, e) = DateUtils.getMonthStartAndEnd(month)
        val monthTxs = txs.filter { it.dateTime in s..e }
        BudgetUiState(
            selectedMonthYear = month,
            totalSpent = FinancialCalculator.calculateMonthlyExpenses(monthTxs),
            totalIncome = FinancialCalculator.calculateMonthlyIncome(monthTxs),
            actualBankBalance = FinancialCalculator.calculateTotalBalance(accounts, txs),
            isSettingBudgetDialogOpen = isDialog, selectedCategoryForBudget = cat
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    val overallBudgetFlow: StateFlow<Double> = budgetsForMonth
        .map { b -> b.firstOrNull { it.categoryId == null }?.budgetAmount ?: 0.0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val categoryBudgetsFlow: StateFlow<List<CategoryBudgetUiModel>> = combine(
        budgetsForMonth, repository.allTransactions, repository.allCategories, _selectedMonth
    ) { budgets, txs, cats, month ->
        val (s, e) = DateUtils.getMonthStartAndEnd(month)
        val monthTxs = txs.filter { it.dateTime in s..e && !it.isCreditCardBillPayment }
        cats.filter { it.type == TransactionType.EXPENSE }.map { cat ->
            val budget = budgets.firstOrNull { it.categoryId == cat.id }?.budgetAmount ?: 0.0
            val spent = monthTxs.filter { it.categoryId == cat.id }.sumOf {
                when (it.type) { TransactionType.EXPENSE -> it.amount; TransactionType.REFUND -> -it.amount; else -> 0.0 }
            }.coerceAtLeast(0.0)
            CategoryBudgetUiModel(cat, budget, spent, budget - spent, if (budget > 0) (spent / budget * 100).toFloat() else 0f)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _feedbackMessage = MutableSharedFlow<String>()
    val feedbackMessage: SharedFlow<String> = _feedbackMessage.asSharedFlow()

    fun selectMonth(ym: YearMonth) { _selectedMonth.value = ym }
    fun openSetBudgetDialog(category: CategoryEntity? = null) { _selectedCatForBudget.value = category; _isSettingDialogOpen.value = true }
    fun closeSetBudgetDialog() { _isSettingDialogOpen.value = false; _selectedCatForBudget.value = null }

    fun saveBudget(amount: Double) {
        if (amount <= 0.0) return
        val ymStr = _selectedMonth.value.format(keyFmt)
        val cat = _selectedCatForBudget.value
        viewModelScope.launch {
            repository.setBudget(BudgetEntity(monthYear = ymStr, categoryId = cat?.id, budgetAmount = amount))
            closeSetBudgetDialog()
            _feedbackMessage.emit(if (cat != null) "Budget for ${cat.name} updated successfully" else "Overall monthly budget updated successfully")
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = BudgetViewModel(repository) as T
    }
}

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

class BudgetViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _selectedMonth = MutableStateFlow(YearMonth.now())
    private val _isSettingDialogOpen = MutableStateFlow(false)
    private val _selectedCatForBudget = MutableStateFlow<CategoryEntity?>(null)

    val uiState: StateFlow<BudgetUiState> = combine(
        _selectedMonth,
        repository.allTransactions,
        repository.allCategories,
        repository.allAccounts,
        _isSettingDialogOpen,
        _selectedCatForBudget
    ) { args: Array<Any?> ->
        val month = args[0] as YearMonth
        @Suppress("UNCHECKED_CAST")
        val transactions = args[1] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = args[2] as List<CategoryEntity>
        @Suppress("UNCHECKED_CAST")
        val accounts = args[3] as List<AccountEntity>
        val isDialog = args[4] as Boolean
        val catForBudget = args[5] as CategoryEntity?

        val (start, end) = DateUtils.getMonthStartAndEnd(month)

        // Filter transactions for this month
        val monthTxs = transactions.filter { it.dateTime in start..end }
        val monthExpenses = FinancialCalculator.calculateMonthlyExpenses(monthTxs)
        val monthIncome = FinancialCalculator.calculateMonthlyIncome(monthTxs)
        val bankBalance = FinancialCalculator.calculateTotalBalance(accounts, transactions)

        BudgetUiState(
            selectedMonthYear = month,
            totalSpent = monthExpenses,
            totalIncome = monthIncome,
            actualBankBalance = bankBalance,
            isSettingBudgetDialogOpen = isDialog,
            selectedCategoryForBudget = catForBudget
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = BudgetUiState()
    )

    private val _overallBudgetFlow = MutableStateFlow(0.0)
    val overallBudgetFlow: StateFlow<Double> = _overallBudgetFlow.asStateFlow()

    private val _categoryBudgetsFlow = MutableStateFlow<List<CategoryBudgetUiModel>>(emptyList())
    val categoryBudgetsFlow: StateFlow<List<CategoryBudgetUiModel>> = _categoryBudgetsFlow.asStateFlow()

    private val _feedbackMessage = MutableSharedFlow<String>()
    val feedbackMessage: SharedFlow<String> = _feedbackMessage.asSharedFlow()

    private var budgetCollectJob: Job? = null

    init {
        // Collect month changes and reload budgets
        viewModelScope.launch {
            _selectedMonth.collect { ym ->
                loadMonthBudgetDetails(ym)
            }
        }
    }

    private fun loadMonthBudgetDetails(ym: YearMonth) {
        budgetCollectJob?.cancel()
        budgetCollectJob = viewModelScope.launch {
            val monthYearStr = ym.format(DateTimeFormatter.ofPattern("MM-yyyy"))
            val (start, end) = DateUtils.getMonthStartAndEnd(ym)
            val txs = repository.getTransactionsBetweenSync(start, end)

            repository.getBudgetsForMonth(monthYearStr).collect { budgets ->
                val overall = budgets.find { it.categoryId == null }?.budgetAmount ?: 0.0
                _overallBudgetFlow.value = overall

                val cats = repository.getAllCategoriesSync().filter { it.type == TransactionType.EXPENSE }
                val catModels = cats.map { cat ->
                    val catBudget = budgets.find { it.categoryId == cat.id }?.budgetAmount ?: 0.0
                    val catSpent = txs.filter { it.categoryId == cat.id && it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }.sumOf { it.amount }
                    val remaining = catBudget - catSpent
                    val util = if (catBudget > 0) ((catSpent / catBudget) * 100f).toFloat() else 0f

                    CategoryBudgetUiModel(
                        category = cat,
                        budgetAmount = catBudget,
                        spentAmount = catSpent,
                        remainingAmount = remaining,
                        utilizationPercent = util
                    )
                }
                _categoryBudgetsFlow.value = catModels
            }
        }
    }

    fun selectMonth(ym: YearMonth) {
        _selectedMonth.value = ym
    }

    fun openSetBudgetDialog(category: CategoryEntity? = null) {
        _selectedCatForBudget.value = category
        _isSettingDialogOpen.value = true
    }

    fun closeSetBudgetDialog() {
        _isSettingDialogOpen.value = false
        _selectedCatForBudget.value = null
    }

    fun saveBudget(amount: Double) {
        if (amount <= 0.0) return
        val ym = _selectedMonth.value
        val ymStr = ym.format(DateTimeFormatter.ofPattern("MM-yyyy"))
        val catId = _selectedCatForBudget.value?.id
        val catName = _selectedCatForBudget.value?.name

        viewModelScope.launch {
            val budget = BudgetEntity(
                monthYear = ymStr,
                categoryId = catId,
                budgetAmount = amount
            )
            repository.setBudget(budget)

            // Update in-memory state immediately so UI updates with 0ms lag
            if (catId == null) {
                _overallBudgetFlow.value = amount
            }

            closeSetBudgetDialog()
            _feedbackMessage.emit(
                if (catName != null) "Budget for $catName updated successfully"
                else "Overall monthly budget updated successfully"
            )
            loadMonthBudgetDetails(ym)
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return BudgetViewModel(repository) as T
        }
    }
}

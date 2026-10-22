package com.expensetracker.app.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class TimePeriodFilter(val displayName: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month")
}

enum class SortOrder(val displayName: String) {
    DATE_DESC("Date: Newest First"),
    DATE_ASC("Date: Oldest First"),
    AMOUNT_DESC("Amount: High to Low"),
    AMOUNT_ASC("Amount: Low to High")
}

data class DayGroupedTransactions(
    val dateString: String,
    val dayTotalExpense: Double,
    val transactions: List<TransactionEntity>
)

data class TransactionListUiState(
    val searchQuery: String = "",
    val selectedPeriod: TimePeriodFilter = TimePeriodFilter.THIS_MONTH,
    val selectedType: TransactionType? = null,
    val selectedCategory: CategoryEntity? = null,
    val selectedPaymentMethod: PaymentMethod? = null,
    val sortOrder: SortOrder = SortOrder.DATE_DESC,
    val totalFilteredExpense: Double = 0.0,
    val totalFilteredIncome: Double = 0.0,
    val groupedTransactions: List<DayGroupedTransactions> = emptyList(),
    val allCategories: List<CategoryEntity> = emptyList(),
    val rawTransactions: List<TransactionEntity> = emptyList(),
    val isLoading: Boolean = false
)

class TransactionListViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedPeriod = MutableStateFlow(TimePeriodFilter.THIS_MONTH)
    private val _selectedType = MutableStateFlow<TransactionType?>(null)
    private val _selectedCategory = MutableStateFlow<CategoryEntity?>(null)
    private val _selectedPaymentMethod = MutableStateFlow<PaymentMethod?>(null)
    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)

    val uiState: StateFlow<TransactionListUiState> = combine(
        repository.allTransactions,
        repository.allCategories,
        _searchQuery,
        _selectedPeriod,
        _selectedType,
        _selectedCategory,
        _selectedPaymentMethod,
        _sortOrder
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val transactions = args[0] as List<TransactionEntity>
        @Suppress("UNCHECKED_CAST")
        val categories = args[1] as List<CategoryEntity>
        val query = args[2] as String
        val period = args[3] as TimePeriodFilter
        val type = args[4] as TransactionType?
        val category = args[5] as CategoryEntity?
        val paymentMethod = args[6] as PaymentMethod?
        val sort = args[7] as SortOrder

        // 1. Time range filter
        val (start, end) = when (period) {
            TimePeriodFilter.ALL -> Pair(0L, Long.MAX_VALUE)
            TimePeriodFilter.TODAY -> DateUtils.getTodayStartAndEnd()
            TimePeriodFilter.THIS_WEEK -> DateUtils.getWeekStartAndEnd()
            TimePeriodFilter.THIS_MONTH -> DateUtils.getMonthStartAndEnd()
        }

        // 2. Filter
        var filtered = transactions.filter { it.dateTime in start..end }

        if (query.isNotBlank()) {
            filtered = filtered.filter {
                it.description.contains(query, ignoreCase = true) ||
                it.tags.contains(query, ignoreCase = true) ||
                it.categoryName.contains(query, ignoreCase = true) ||
                it.paymentMethod.displayName.contains(query, ignoreCase = true) ||
                it.amount.toString().contains(query)
            }
        }

        if (type != null) {
            filtered = filtered.filter { it.type == type }
        }

        if (category != null) {
            filtered = filtered.filter { it.categoryId == category.id }
        }

        if (paymentMethod != null) {
            filtered = filtered.filter { it.paymentMethod == paymentMethod }
        }

        // 3. Sort
        filtered = when (sort) {
            SortOrder.DATE_DESC -> filtered.sortedByDescending { it.dateTime }
            SortOrder.DATE_ASC -> filtered.sortedBy { it.dateTime }
            SortOrder.AMOUNT_DESC -> filtered.sortedByDescending { it.amount }
            SortOrder.AMOUNT_ASC -> filtered.sortedBy { it.amount }
        }

        // 4. Totals
        val totalExp = filtered.filter { it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }.sumOf { it.amount }
        val totalInc = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }

        // 5. Group by day
        val zone = ZoneId.systemDefault()
        val groups = filtered.groupBy {
            Instant.ofEpochMilli(it.dateTime).atZone(zone).toLocalDate()
        }.map { (date, dayTxs) ->
            val dayExp = dayTxs.filter { it.type == TransactionType.EXPENSE && !it.isCreditCardBillPayment }.sumOf { it.amount }
            val formattedDate = when (date) {
                LocalDate.now() -> "Today (${date.format(DateUtils.DATE_FORMATTER_DD_MM_YYYY)})"
                LocalDate.now().minusDays(1) -> "Yesterday (${date.format(DateUtils.DATE_FORMATTER_DD_MM_YYYY)})"
                else -> date.format(DateUtils.DATE_FORMATTER_DD_MM_YYYY)
            }
            DayGroupedTransactions(
                dateString = formattedDate,
                dayTotalExpense = dayExp,
                transactions = dayTxs
            )
        }

        TransactionListUiState(
            searchQuery = query,
            selectedPeriod = period,
            selectedType = type,
            selectedCategory = category,
            selectedPaymentMethod = paymentMethod,
            sortOrder = sort,
            totalFilteredExpense = totalExp,
            totalFilteredIncome = totalInc,
            groupedTransactions = groups,
            allCategories = categories,
            rawTransactions = filtered
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = TransactionListUiState(isLoading = false)
    )

    fun onSearchQueryChanged(q: String) { _searchQuery.value = q }
    fun onPeriodSelected(p: TimePeriodFilter) { _selectedPeriod.value = p }
    fun onTypeSelected(t: TransactionType?) { _selectedType.value = t }
    fun onCategorySelected(c: CategoryEntity?) { _selectedCategory.value = c }
    fun onPaymentMethodSelected(m: PaymentMethod?) { _selectedPaymentMethod.value = m }
    fun onSortOrderChanged(s: SortOrder) { _sortOrder.value = s }

    fun deleteTransaction(tx: TransactionEntity) {
        viewModelScope.launch {
            repository.deleteTransaction(tx)
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return TransactionListViewModel(repository) as T
        }
    }
}

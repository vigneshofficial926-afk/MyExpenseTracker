package com.expensetracker.app.ui.screens.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.RecurringTransactionEntity
import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.RecurrenceFrequency
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RecurringUiState(
    val recurringList: List<RecurringTransactionEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val isAddDialogOpen: Boolean = false
)

class RecurringViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _isAddDialogOpen = MutableStateFlow(false)

    val uiState: StateFlow<RecurringUiState> = combine(
        repository.allRecurring,
        repository.allCategories,
        _isAddDialogOpen
    ) { recurring, categories, isAdd ->
        RecurringUiState(
            recurringList = recurring,
            categories = categories,
            isAddDialogOpen = isAdd
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringUiState()
    )

    fun openAddDialog() { _isAddDialogOpen.value = true }
    fun closeAddDialog() { _isAddDialogOpen.value = false }

    fun addRecurring(
        title: String,
        amount: Double,
        type: TransactionType,
        categoryId: Long,
        categoryName: String,
        paymentMethod: PaymentMethod,
        frequency: RecurrenceFrequency,
        startDateMillis: Long,
        description: String
    ) {
        viewModelScope.launch {
            val entity = RecurringTransactionEntity(
                title = title,
                amount = amount,
                type = type,
                categoryId = categoryId,
                categoryName = categoryName,
                paymentMethod = paymentMethod,
                frequency = frequency,
                startDate = startDateMillis,
                nextDueDate = startDateMillis,
                description = description
            )
            repository.insertRecurring(entity)
            closeAddDialog()
        }
    }

    fun toggleActive(rule: RecurringTransactionEntity) {
        viewModelScope.launch {
            repository.updateRecurring(rule.copy(isActive = !rule.isActive))
        }
    }

    fun deleteRecurring(rule: RecurringTransactionEntity) {
        viewModelScope.launch {
            repository.deleteRecurring(rule)
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return RecurringViewModel(repository) as T
        }
    }
}

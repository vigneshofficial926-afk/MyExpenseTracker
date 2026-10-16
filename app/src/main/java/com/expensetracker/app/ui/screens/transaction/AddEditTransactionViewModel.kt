package com.expensetracker.app.ui.screens.transaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class AddEditTransactionUiState(
    val transactionId: Long? = null,
    val isEditing: Boolean = false,
    val amount: String = "",
    val amountError: String? = null,
    val type: TransactionType = TransactionType.EXPENSE,
    val selectedCategory: CategoryEntity? = null,
    val categoryError: String? = null,
    val dateTimeMillis: Long = System.currentTimeMillis(),
    val paymentMethod: PaymentMethod = PaymentMethod.UPI,
    val description: String = "",
    val tags: String = "",
    val isCreditCardBillPayment: Boolean = false,
    val availableCategories: List<CategoryEntity> = emptyList(),
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val errorMessage: String? = null,
    val showDeleteConfirmation: Boolean = false
)

class AddEditTransactionViewModel(
    private val repository: ExpenseRepository,
    private val initialTransactionId: Long? = null,
    private val initialType: TransactionType? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AddEditTransactionUiState(
            transactionId = initialTransactionId,
            isEditing = initialTransactionId != null && initialTransactionId > 0,
            type = initialType ?: TransactionType.EXPENSE
        )
    )
    val uiState: StateFlow<AddEditTransactionUiState> = _uiState.asStateFlow()

    init {
        // Load categories reactively
        viewModelScope.launch {
            repository.allCategories.collect { categories ->
                val filtered = categories.filter { it.type == _uiState.value.type }
                _uiState.value = _uiState.value.copy(
                    availableCategories = filtered,
                    selectedCategory = _uiState.value.selectedCategory ?: filtered.firstOrNull()
                )
            }
        }

        // If editing, load existing transaction
        if (initialTransactionId != null && initialTransactionId > 0) {
            viewModelScope.launch {
                val existing = repository.getTransactionById(initialTransactionId).firstOrNull()
                if (existing != null) {
                    val allCats = repository.getAllCategoriesSync()
                    val matchingCategory = allCats.find { it.id == existing.categoryId }
                    _uiState.value = _uiState.value.copy(
                        transactionId = existing.id,
                        isEditing = true,
                        amount = if (existing.amount % 1.0 == 0.0) existing.amount.toLong().toString() else existing.amount.toString(),
                        type = existing.type,
                        selectedCategory = matchingCategory,
                        dateTimeMillis = existing.dateTime,
                        paymentMethod = existing.paymentMethod,
                        description = existing.description,
                        tags = existing.tags,
                        isCreditCardBillPayment = existing.isCreditCardBillPayment
                    )
                }
            }
        }
    }

    fun onTypeChanged(newType: TransactionType) {
        viewModelScope.launch {
            val allCats = repository.getAllCategoriesSync()
            val filtered = allCats.filter { it.type == newType }
            _uiState.value = _uiState.value.copy(
                type = newType,
                availableCategories = filtered,
                selectedCategory = filtered.firstOrNull()
            )
        }
    }

    fun onAmountChanged(newAmount: String) {
        // Only allow numbers and max one decimal point
        if (newAmount.isEmpty() || newAmount.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
            _uiState.value = _uiState.value.copy(
                amount = newAmount,
                amountError = null
            )
        }
    }

    fun onCategorySelected(category: CategoryEntity) {
        _uiState.value = _uiState.value.copy(
            selectedCategory = category,
            categoryError = null
        )
    }

    fun onPaymentMethodChanged(method: PaymentMethod) {
        _uiState.value = _uiState.value.copy(paymentMethod = method)
    }

    fun onDateTimeChanged(millis: Long) {
        _uiState.value = _uiState.value.copy(dateTimeMillis = millis)
    }

    fun onDescriptionChanged(desc: String) {
        _uiState.value = _uiState.value.copy(description = desc)
    }

    fun onTagsChanged(tags: String) {
        _uiState.value = _uiState.value.copy(tags = tags)
    }

    fun onCreditCardBillPaymentToggled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isCreditCardBillPayment = enabled)
    }

    fun setShowDeleteConfirmation(show: Boolean) {
        _uiState.value = _uiState.value.copy(showDeleteConfirmation = show)
    }

    fun saveTransaction() {
        val state = _uiState.value
        val amountVal = state.amount.toDoubleOrNull()

        if (amountVal == null || amountVal <= 0.0) {
            _uiState.value = state.copy(amountError = "Please enter a valid amount greater than ₹0")
            return
        }

        val category = state.selectedCategory
        if (category == null) {
            _uiState.value = state.copy(categoryError = "Please select a category")
            return
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, errorMessage = null)
            val transaction = TransactionEntity(
                id = state.transactionId ?: 0,
                amount = amountVal,
                type = state.type,
                categoryId = category.id,
                categoryName = category.name,
                dateTime = state.dateTimeMillis,
                paymentMethod = state.paymentMethod,
                description = state.description.trim(),
                tags = state.tags.trim(),
                isCreditCardBillPayment = state.isCreditCardBillPayment,
                isExcludedFromBudget = state.isCreditCardBillPayment || state.type == TransactionType.TRANSFER
            )

            if (state.isEditing) {
                repository.updateTransaction(transaction)
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
            } else {
                val result = repository.insertTransaction(transaction)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSaving = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Failed to save transaction"
                    )
                }
            }
        }
    }

    fun forceSaveDuplicate() {
        val state = _uiState.value
        val amountVal = state.amount.toDoubleOrNull() ?: return
        val category = state.selectedCategory ?: return

        viewModelScope.launch {
            val transaction = TransactionEntity(
                amount = amountVal,
                type = state.type,
                categoryId = category.id,
                categoryName = category.name,
                dateTime = state.dateTimeMillis,
                paymentMethod = state.paymentMethod,
                description = state.description.trim(),
                tags = state.tags.trim(),
                isCreditCardBillPayment = state.isCreditCardBillPayment
            )
            repository.insertTransaction(transaction, allowDuplicate = true)
            _uiState.value = _uiState.value.copy(saveSuccess = true)
        }
    }

    fun deleteTransaction(onComplete: () -> Unit) {
        val id = _uiState.value.transactionId ?: return
        viewModelScope.launch {
            repository.deleteTransactionById(id)
            onComplete()
        }
    }

    class Factory(
        private val repository: ExpenseRepository,
        private val transactionId: Long?,
        private val type: TransactionType?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddEditTransactionViewModel(repository, transactionId, type) as T
        }
    }
}

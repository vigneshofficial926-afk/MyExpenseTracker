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

import kotlinx.coroutines.flow.update

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

    private val isEditMode = initialTransactionId != null && initialTransactionId > 0
    private val _uiState = MutableStateFlow(
        AddEditTransactionUiState(
            transactionId = initialTransactionId,
            isEditing = isEditMode,
            type = initialType ?: TransactionType.EXPENSE
        )
    )
    val uiState: StateFlow<AddEditTransactionUiState> = _uiState.asStateFlow()

    private var allCategories: List<CategoryEntity> = emptyList()
    private var original: TransactionEntity? = null

    init {
        viewModelScope.launch {
            repository.allCategories.collect { cats ->
                allCategories = cats
                _uiState.update { s ->
                    val filtered = cats.filter { it.type == s.type.categoryGroup }
                    s.copy(
                        availableCategories = filtered,
                        selectedCategory = filtered.firstOrNull { it.id == s.selectedCategory?.id }
                            ?: filtered.firstOrNull()
                    )
                }
            }
        }
        if (isEditMode) viewModelScope.launch {
            val tx = repository.getTransactionById(initialTransactionId!!).firstOrNull() ?: return@launch
            original = tx
            val cat = repository.getAllCategoriesSync().firstOrNull { it.id == tx.categoryId }
            _uiState.update { s ->
                s.copy(
                    transactionId = tx.id, isEditing = true,
                    amount = if (tx.amount % 1.0 == 0.0) tx.amount.toLong().toString() else tx.amount.toString(),
                    type = tx.type,
                    availableCategories = allCategories.filter { it.type == tx.type.categoryGroup },
                    selectedCategory = cat ?: s.selectedCategory,
                    dateTimeMillis = tx.dateTime, paymentMethod = tx.paymentMethod,
                    description = tx.description, tags = tx.tags,
                    isCreditCardBillPayment = tx.isCreditCardBillPayment
                )
            }
        }
    }

    fun onTypeChanged(newType: TransactionType) = _uiState.update { s ->
        val filtered = allCategories.filter { it.type == newType.categoryGroup }
        s.copy(
            type = newType,
            availableCategories = filtered,
            selectedCategory = s.selectedCategory?.takeIf { it.type == newType.categoryGroup } ?: filtered.firstOrNull(),
            isCreditCardBillPayment = s.isCreditCardBillPayment && newType == TransactionType.EXPENSE
        )
    }

    fun onAmountChanged(v: String) {
        if (v.isEmpty() || v.matches(Regex("^\\d*\\.?\\d{0,2}$"))) _uiState.update { it.copy(amount = v, amountError = null) }
    }
    fun onCategorySelected(c: CategoryEntity) = _uiState.update { it.copy(selectedCategory = c, categoryError = null) }
    fun onPaymentMethodChanged(m: PaymentMethod) = _uiState.update { it.copy(paymentMethod = m) }
    fun onDateTimeChanged(ms: Long) = _uiState.update { it.copy(dateTimeMillis = ms) }
    fun onDescriptionChanged(d: String) = _uiState.update { it.copy(description = d) }
    fun onTagsChanged(t: String) = _uiState.update { it.copy(tags = t) }
    fun onCreditCardBillPaymentToggled(e: Boolean) = _uiState.update { it.copy(isCreditCardBillPayment = e) }
    fun setShowDeleteConfirmation(show: Boolean) = _uiState.update { it.copy(showDeleteConfirmation = show) }
    fun clearError() = _uiState.update { it.copy(errorMessage = null) }

    fun saveTransaction(allowDuplicate: Boolean = false) {
        val s = _uiState.value
        if (s.isSaving || (isEditMode && original == null)) return
        val amount = s.amount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            _uiState.update { it.copy(amountError = "Please enter a valid amount greater than ₹0") }; return
        }
        val category = s.selectedCategory ?: run {
            _uiState.update { it.copy(categoryError = "Please select a category") }; return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }   // set synchronously: blocks double-tap

        viewModelScope.launch {
            try {
                val isCc = s.isCreditCardBillPayment && s.type == TransactionType.EXPENSE
                val base = original ?: TransactionEntity(
                    amount = amount, type = s.type, categoryId = category.id, categoryName = category.name,
                    dateTime = s.dateTimeMillis, paymentMethod = s.paymentMethod
                )
                val tx = base.copy(
                    amount = amount, type = s.type, categoryId = category.id, categoryName = category.name,
                    dateTime = s.dateTimeMillis, paymentMethod = s.paymentMethod,
                    description = s.description.trim(), tags = s.tags.trim(),
                    isCreditCardBillPayment = isCc,
                    isExcludedFromBudget = isCc || s.type == TransactionType.TRANSFER
                )
                if (s.isEditing) {
                    repository.updateTransaction(tx)
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                } else {
                    repository.insertTransaction(tx, allowDuplicate).fold(
                        onSuccess = { _uiState.update { it.copy(isSaving = false, saveSuccess = true) } },
                        onFailure = { e -> _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Failed to save") } }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Failed to save") }
            }
        }
    }

    fun deleteTransaction(onComplete: () -> Unit) {
        val id = _uiState.value.transactionId ?: return
        viewModelScope.launch { repository.deleteTransactionById(id); onComplete() }
    }

    class Factory(
        private val repository: ExpenseRepository,
        private val transactionId: Long?,
        private val type: TransactionType?
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            AddEditTransactionViewModel(repository, transactionId, type) as T
    }
}

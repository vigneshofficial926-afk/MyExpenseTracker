package com.expensetracker.app.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CategoryUiState(
    val selectedType: TransactionType = TransactionType.EXPENSE,
    val categories: List<CategoryEntity> = emptyList(),
    val isAddEditDialogOpen: Boolean = false,
    val editingCategory: CategoryEntity? = null,
    val error: String? = null
)

class CategoryViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _selectedType = MutableStateFlow(TransactionType.EXPENSE)
    private val _isAddEditDialogOpen = MutableStateFlow(false)
    private val _editingCategory = MutableStateFlow<CategoryEntity?>(null)
    private val _error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CategoryUiState> = combine(
        repository.allCategories,
        _selectedType,
        _isAddEditDialogOpen,
        _editingCategory,
        _error
    ) { categories, type, isDialog, editing, error ->
        val filtered = categories.filter { it.type == type }
        CategoryUiState(type, filtered, isDialog, editing, error)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CategoryUiState())

    fun selectType(type: TransactionType) { _selectedType.value = type }

    fun openAddDialog() {
        _editingCategory.value = null
        _error.value = null
        _isAddEditDialogOpen.value = true
    }

    fun openEditDialog(category: CategoryEntity) {
        _editingCategory.value = category
        _error.value = null
        _isAddEditDialogOpen.value = true
    }

    fun closeDialog() { _isAddEditDialogOpen.value = false; _editingCategory.value = null; _error.value = null }

    fun saveCategory(name: String, iconName: String, colorHex: String) {
        val clean = name.trim()
        val editing = _editingCategory.value
        val type = editing?.type ?: _selectedType.value
        viewModelScope.launch {
            val dup = repository.getAllCategoriesSync().any { it.type == type && it.id != editing?.id && it.name.equals(clean, true) }
            when {
                clean.isEmpty() -> _error.value = "Name can't be empty."
                dup -> _error.value = "A category named \"$clean\" already exists."
                else -> {
                    if (editing != null) repository.updateCategory(editing.copy(name = clean, iconName = iconName, colorHex = colorHex))
                    else repository.insertCategory(CategoryEntity(name = clean, type = type, iconName = iconName, colorHex = colorHex))
                    closeDialog()
                }
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CategoryViewModel(repository) as T
        }
    }
}

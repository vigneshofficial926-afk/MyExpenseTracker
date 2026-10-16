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
    val editingCategory: CategoryEntity? = null
)

class CategoryViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _selectedType = MutableStateFlow(TransactionType.EXPENSE)
    private val _isAddEditDialogOpen = MutableStateFlow(false)
    private val _editingCategory = MutableStateFlow<CategoryEntity?>(null)

    val uiState: StateFlow<CategoryUiState> = combine(
        repository.allCategories,
        _selectedType,
        _isAddEditDialogOpen,
        _editingCategory
    ) { categories, type, isDialog, editing ->
        val filtered = categories.filter { it.type == type }
        CategoryUiState(
            selectedType = type,
            categories = filtered,
            isAddEditDialogOpen = isDialog,
            editingCategory = editing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoryUiState()
    )

    fun selectType(type: TransactionType) { _selectedType.value = type }

    fun openAddDialog() {
        _editingCategory.value = null
        _isAddEditDialogOpen.value = true
    }

    fun openEditDialog(category: CategoryEntity) {
        _editingCategory.value = category
        _isAddEditDialogOpen.value = true
    }

    fun closeDialog() {
        _isAddEditDialogOpen.value = false
        _editingCategory.value = null
    }

    fun saveCategory(name: String, iconName: String, colorHex: String) {
        val editing = _editingCategory.value
        val type = _selectedType.value
        viewModelScope.launch {
            if (editing != null) {
                repository.updateCategory(editing.copy(name = name, iconName = iconName, colorHex = colorHex))
            } else {
                repository.insertCategory(
                    CategoryEntity(
                        name = name,
                        type = type,
                        iconName = iconName,
                        colorHex = colorHex,
                        isDefault = false
                    )
                )
            }
            closeDialog()
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

package com.expensetracker.app.ui.screens.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.entity.SavingsGoalEntity
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.domain.FinancialCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SavingsGoalUiModel(
    val goal: SavingsGoalEntity,
    val progressPercent: Float,
    val monthlySavingsNeeded: Double,
    val isCompleted: Boolean
)

data class SavingsGoalsUiState(
    val goals: List<SavingsGoalUiModel> = emptyList(),
    val totalSaved: Double = 0.0,
    val totalTarget: Double = 0.0,
    val isAddDialogOpen: Boolean = false,
    val isDepositDialogOpen: Boolean = false,
    val selectedGoalForDeposit: SavingsGoalEntity? = null
)

class SavingsGoalsViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _isAddDialogOpen = MutableStateFlow(false)
    private val _isDepositDialogOpen = MutableStateFlow(false)
    private val _selectedGoalForDeposit = MutableStateFlow<SavingsGoalEntity?>(null)

    val uiState: StateFlow<SavingsGoalsUiState> = repository.allSavingsGoals.map { goalsList ->
        val models = goalsList.map { goal ->
            val progress = if (goal.targetAmount > 0) ((goal.savedAmount / goal.targetAmount) * 100f).toFloat() else 0f
            val neededPerMonth = FinancialCalculator.calculateRequiredMonthlySavings(
                targetAmount = goal.targetAmount,
                savedAmount = goal.savedAmount,
                targetEpochMillis = goal.targetDate
            )
            SavingsGoalUiModel(
                goal = goal,
                progressPercent = progress.coerceIn(0f, 100f),
                monthlySavingsNeeded = neededPerMonth,
                isCompleted = goal.savedAmount >= goal.targetAmount
            )
        }

        SavingsGoalsUiState(
            goals = models,
            totalSaved = goalsList.sumOf { it.savedAmount },
            totalTarget = goalsList.sumOf { it.targetAmount },
            isAddDialogOpen = _isAddDialogOpen.value,
            isDepositDialogOpen = _isDepositDialogOpen.value,
            selectedGoalForDeposit = _selectedGoalForDeposit.value
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SavingsGoalsUiState()
    )

    fun openAddGoalDialog() { _isAddDialogOpen.value = true }
    fun closeAddGoalDialog() { _isAddDialogOpen.value = false }

    fun openDepositDialog(goal: SavingsGoalEntity) {
        _selectedGoalForDeposit.value = goal
        _isDepositDialogOpen.value = true
    }
    fun closeDepositDialog() {
        _isDepositDialogOpen.value = false
        _selectedGoalForDeposit.value = null
    }

    fun addGoal(name: String, targetAmount: Double, initialSaved: Double, targetDateMillis: Long, notes: String) {
        viewModelScope.launch {
            val goal = SavingsGoalEntity(
                name = name,
                targetAmount = targetAmount,
                savedAmount = initialSaved,
                targetDate = targetDateMillis,
                notes = notes,
                isCompleted = initialSaved >= targetAmount
            )
            repository.insertGoal(goal)
            closeAddGoalDialog()
        }
    }

    fun depositFunds(goalId: Long, amount: Double) {
        viewModelScope.launch {
            repository.updateSavedAmount(goalId, amount)
            closeDepositDialog()
        }
    }

    fun deleteGoal(goal: SavingsGoalEntity) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SavingsGoalsViewModel(repository) as T
        }
    }
}

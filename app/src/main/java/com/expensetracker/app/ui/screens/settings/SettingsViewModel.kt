package com.expensetracker.app.ui.screens.settings

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.repository.ExpenseRepository
import com.expensetracker.app.util.BackupRestoreHelper
import com.expensetracker.app.util.CsvExporter
import com.expensetracker.app.util.CurrencyFormatter
import com.expensetracker.app.util.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val currencySymbol: String = "₹ (INR)",
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val isPinLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val userPin: String = "",
    val isSettingPinDialogOpen: Boolean = false,
    val isClearDataConfirmationOpen: Boolean = false,
    val isClearDataFinalConfirmationOpen: Boolean = false,
    val operationMessage: String? = null
)

class SettingsViewModel(
    private val repository: ExpenseRepository,
    private val database: ExpenseDatabase,
    private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name),
            firstDayOfWeek = DayOfWeek.valueOf(prefs.getString("first_day_of_week", DayOfWeek.MONDAY.name) ?: DayOfWeek.MONDAY.name),
            isPinLockEnabled = prefs.getBoolean("pin_lock_enabled", false),
            isBiometricEnabled = prefs.getBoolean("biometric_enabled", false),
            userPin = prefs.getString("user_pin", "") ?: ""
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setFirstDayOfWeek(day: DayOfWeek) {
        prefs.edit().putString("first_day_of_week", day.name).apply()
        _uiState.value = _uiState.value.copy(firstDayOfWeek = day)
    }

    fun toggleBiometric(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _uiState.value = _uiState.value.copy(isBiometricEnabled = enabled)
    }

    fun openPinDialog() { _uiState.value = _uiState.value.copy(isSettingPinDialogOpen = true) }
    fun closePinDialog() { _uiState.value = _uiState.value.copy(isSettingPinDialogOpen = false) }

    fun setPin(pin: String) {
        prefs.edit().putString("user_pin", pin).putBoolean("pin_lock_enabled", pin.isNotEmpty()).apply()
        _uiState.value = _uiState.value.copy(
            userPin = pin,
            isPinLockEnabled = pin.isNotEmpty(),
            isSettingPinDialogOpen = false
        )
    }

    fun disablePin() {
        prefs.edit().remove("user_pin").putBoolean("pin_lock_enabled", false).apply()
        _uiState.value = _uiState.value.copy(userPin = "", isPinLockEnabled = false)
    }

    fun exportDatabaseBackup() {
        viewModelScope.launch {
            BackupRestoreHelper.exportAndShareBackup(context, database)
        }
    }

    fun exportTransactionsCsv() {
        viewModelScope.launch {
            val transactions = repository.getAllTransactionsSync()
            CsvExporter.exportAndShareCsv(context, transactions)
        }
    }

    fun generateAndShareMonthlyReport() {
        viewModelScope.launch {
            val (start, end) = DateUtils.getMonthStartAndEnd()
            val transactions = repository.getTransactionsBetweenSync(start, end)
            val totalInc = transactions.filter { it.type.name == "INCOME" }.sumOf { it.amount }
            val totalExp = transactions.filter { it.type.name == "EXPENSE" && !it.isCreditCardBillPayment }.sumOf { it.amount }

            val sb = StringBuilder()
            sb.append("=== VIGNESH EXPENSE TRACKER - MONTHLY REPORT ===\n")
            sb.append("Date: ${DateUtils.formatDateTime(System.currentTimeMillis())}\n")
            sb.append("Total Income: ${CurrencyFormatter.format(totalInc)}\n")
            sb.append("Total Expense: ${CurrencyFormatter.format(totalExp)}\n")
            sb.append("Net Savings: ${CurrencyFormatter.format(totalInc - totalExp)}\n\n")
            sb.append("--- Category Breakdown ---\n")

            val byCat = transactions.filter { it.type.name == "EXPENSE" }
                .groupBy { it.categoryName }
                .mapValues { it.value.sumOf { tx -> tx.amount } }

            byCat.forEach { (cat, amt) ->
                sb.append("• $cat: ${CurrencyFormatter.format(amt)}\n")
            }

            BackupRestoreHelper.shareTextReport(context, sb.toString())
        }
    }

    fun openClearDataConfirmation() {
        _uiState.value = _uiState.value.copy(isClearDataConfirmationOpen = true)
    }

    fun closeClearDataConfirmation() {
        _uiState.value = _uiState.value.copy(
            isClearDataConfirmationOpen = false,
            isClearDataFinalConfirmationOpen = false
        )
    }

    fun proceedToFinalClearConfirmation() {
        _uiState.value = _uiState.value.copy(
            isClearDataConfirmationOpen = false,
            isClearDataFinalConfirmationOpen = true
        )
    }

    fun clearAllData() {
        viewModelScope.launch {
            try {
                repository.clearAllData()
                closeClearDataConfirmation()
                _uiState.value = _uiState.value.copy(operationMessage = "All application data has been permanently cleared.")
            } catch (e: Exception) {
                closeClearDataConfirmation()
                _uiState.value = _uiState.value.copy(operationMessage = "Failed to clear data: ${e.message}")
            }
        }
    }

    class Factory(
        private val repository: ExpenseRepository,
        private val database: ExpenseDatabase,
        private val context: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository, database, context) as T
        }
    }
}

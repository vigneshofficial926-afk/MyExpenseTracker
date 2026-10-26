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
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.security.SecureRandom
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
    val isSmsTrackingEnabled: Boolean = false,
    val isSmsConsentDialogOpen: Boolean = false,
    val isSmsTestDialogOpen: Boolean = false,
    val isScanningSms: Boolean = false,
    val operationMessage: String? = null
)

class SettingsViewModel(
    private val repository: ExpenseRepository,
    private val database: ExpenseDatabase,
    private val context: Context
) : ViewModel() {

    private val prefs: SharedPreferences = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
    init { migrateLegacyPin() }                       // must sit BEFORE _uiState

    private val _uiState = MutableStateFlow(
        SettingsUiState(
            themeMode = ThemeMode.valueOf(prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name),
            firstDayOfWeek = DayOfWeek.valueOf(prefs.getString("first_day_of_week", DayOfWeek.MONDAY.name) ?: DayOfWeek.MONDAY.name),
            isPinLockEnabled = prefs.getBoolean("pin_lock_enabled", false),
            isBiometricEnabled = prefs.getBoolean("biometric_enabled", false),
            userPin = prefs.getString("pin_hash", "") ?: "",   // now holds the HASH; non-empty = PIN set
            isSmsTrackingEnabled = prefs.getBoolean("sms_tracking_enabled", false)
        )
    )
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init { DateUtils.firstDayOfWeek = _uiState.value.firstDayOfWeek }

    private val _sessionUnlocked = MutableStateFlow(false)
    val sessionUnlocked: StateFlow<Boolean> = _sessionUnlocked.asStateFlow()
    fun unlock() { _sessionUnlocked.value = true }
    fun lock() { _sessionUnlocked.value = false }
    fun consumeMessage() = _uiState.update { it.copy(operationMessage = null) }

    private fun hashPin(pin: String, salt: String) =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }
    private fun newSalt() = ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    private fun storePin(pin: String) {
        val salt = newSalt()
        prefs.edit().putString("pin_salt", salt).putString("pin_hash", hashPin(pin, salt)).putBoolean("pin_lock_enabled", true).apply()
    }
    private fun migrateLegacyPin() {
        val legacy = prefs.getString("user_pin", null) ?: return
        if (legacy.isNotEmpty()) storePin(legacy)
        prefs.edit().remove("user_pin").apply()
    }
    fun verifyPin(pin: String): Boolean =
        hashPin(pin, prefs.getString("pin_salt", "") ?: "") == prefs.getString("pin_hash", "")

    fun setPin(pin: String) {
        storePin(pin)
        _uiState.update { it.copy(userPin = prefs.getString("pin_hash", "") ?: "", isPinLockEnabled = true, isSettingPinDialogOpen = false) }
        _sessionUnlocked.value = true
    }
    fun disablePin() {
        prefs.edit().remove("pin_hash").remove("pin_salt").putBoolean("pin_lock_enabled", false).putBoolean("biometric_enabled", false).apply()
        _uiState.update { it.copy(userPin = "", isPinLockEnabled = false, isBiometricEnabled = false) }
    }
    fun setFirstDayOfWeek(day: DayOfWeek) {
        prefs.edit().putString("first_day_of_week", day.name).apply()
        DateUtils.firstDayOfWeek = day
        _uiState.update { it.copy(firstDayOfWeek = day) }
    }
    fun restoreBackup(uri: Uri) {
        viewModelScope.launch {
            val r = BackupRestoreHelper.restoreBackup(context, database, uri)
            _uiState.update {
                it.copy(operationMessage = r.fold({ n -> "Restored $n transactions from backup." }, { e -> "Restore failed: ${e.message}" }))
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _uiState.update { it.copy(themeMode = mode) }
    }

    fun toggleBiometric(enabled: Boolean) {
        prefs.edit().putBoolean("biometric_enabled", enabled).apply()
        _uiState.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun openPinDialog() { _uiState.update { it.copy(isSettingPinDialogOpen = true) } }
    fun closePinDialog() { _uiState.update { it.copy(isSettingPinDialogOpen = false) } }

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

    // --- Automatic Bank SMS Tracking ---
    fun openSmsConsentDialog() {
        _uiState.value = _uiState.value.copy(isSmsConsentDialogOpen = true)
    }

    fun closeSmsConsentDialog() {
        _uiState.value = _uiState.value.copy(isSmsConsentDialogOpen = false)
    }

    fun openSmsTestDialog() {
        _uiState.value = _uiState.value.copy(isSmsTestDialogOpen = true)
    }

    fun closeSmsTestDialog() {
        _uiState.value = _uiState.value.copy(isSmsTestDialogOpen = false)
    }

    fun setSmsTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("sms_tracking_enabled", enabled).apply()
        _uiState.value = _uiState.value.copy(isSmsTrackingEnabled = enabled)
    }

    fun scanSmsInbox(onComplete: (Int) -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isScanningSms = true)
            val result = com.expensetracker.app.util.SmsImportHelper.importFromSmsInbox(context, lookbackDays = 30)
            _uiState.value = _uiState.value.copy(isScanningSms = false)
            val count = result.getOrDefault(0)
            _uiState.value = _uiState.value.copy(
                operationMessage = if (result.isSuccess) "Imported $count bank transaction(s) from SMS."
                else "Failed to scan SMS: ${result.exceptionOrNull()?.message}"
            )
            onComplete(count)
        }
    }

    fun importSingleSms(rawText: String, onComplete: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = com.expensetracker.app.util.SmsImportHelper.importSingleSmsText(context, rawText)
            if (result.isSuccess) {
                val tx = result.getOrNull()!!
                _uiState.value = _uiState.value.copy(
                    operationMessage = "Transaction of ₹${String.format("%.2f", tx.amount)} successfully recorded."
                )
                onComplete(true, "Transaction of ₹${String.format("%.2f", tx.amount)} recorded successfully!")
            } else {
                val msg = result.exceptionOrNull()?.message ?: "Failed to parse SMS."
                onComplete(false, msg)
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

package com.expensetracker.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.BudgetEntity
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.RecurringTransactionEntity
import com.expensetracker.app.data.local.entity.SavingsGoalEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileReader
import java.io.FileWriter

import android.net.Uri
import androidx.room.withTransaction

data class AppBackupData(
    val exportDate: String,
    val appVersion: String = "1.0.0",
    val transactions: List<TransactionEntity>?,
    val categories: List<CategoryEntity>?,
    val budgets: List<BudgetEntity>?,
    val accounts: List<AccountEntity>?,
    val recurring: List<RecurringTransactionEntity>?,
    val savingsGoals: List<SavingsGoalEntity>?
)

object BackupRestoreHelper {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun createBackupJson(database: ExpenseDatabase): String = withContext(Dispatchers.IO) {
        gson.toJson(
            AppBackupData(
                exportDate = DateUtils.formatDateTime(System.currentTimeMillis()),
                transactions = database.transactionDao().getAllTransactionsList(),
                categories = database.categoryDao().getAllCategoriesList(),
                budgets = database.budgetDao().getAllBudgetsList(),
                accounts = database.accountDao().getAllAccountsList(),
                recurring = database.recurringTransactionDao().getAllRecurringList(),
                savingsGoals = database.savingsGoalDao().getAllGoalsList()
            )
        )
    }

    suspend fun exportAndShareBackup(context: Context, database: ExpenseDatabase) {
        val jsonString = createBackupJson(database)
        val exportDir = File(context.cacheDir, "reports")
        if (!exportDir.exists()) exportDir.mkdirs()

        val file = File(exportDir, "expense_tracker_backup_${System.currentTimeMillis()}.json")
        withContext(Dispatchers.IO) {
            FileWriter(file).use { it.write(jsonString) }
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Vignesh Expense Tracker - Data Backup")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Database Backup")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /** Replaces ALL data atomically; on any error nothing changes. */
    suspend fun restoreBackup(context: Context, database: ExpenseDatabase, uri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("Cannot open file")
                val b = gson.fromJson(json, AppBackupData::class.java) ?: error("Invalid backup file")
                if (b.categories.orEmpty().isEmpty()) error("This does not look like a valid backup.")
                database.withTransaction {
                    database.transactionDao().deleteAllTransactions()
                    database.budgetDao().deleteAllBudgets()
                    database.recurringTransactionDao().deleteAllRecurring()
                    database.savingsGoalDao().deleteAllGoals()
                    database.categoryDao().deleteAllCategories()
                    database.accountDao().deleteAllAccounts()
                    database.categoryDao().insertAll(b.categories.orEmpty())
                    database.accountDao().insertAll(b.accounts.orEmpty())
                    database.budgetDao().insertAll(b.budgets.orEmpty())
                    database.transactionDao().insertAll(b.transactions.orEmpty())
                    database.recurringTransactionDao().insertAll(b.recurring.orEmpty())
                    database.savingsGoalDao().insertAll(b.savingsGoals.orEmpty())
                }
                b.transactions.orEmpty().size
            }
        }

    fun shareTextReport(context: Context, reportContent: String, title: String = "Monthly Expense Report") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, reportContent)
        }
        val chooser = Intent.createChooser(intent, "Share Report")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

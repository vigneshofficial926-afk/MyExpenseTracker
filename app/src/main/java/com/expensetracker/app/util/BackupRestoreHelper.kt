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

data class AppBackupData(
    val exportDate: String,
    val appVersion: String = "1.0.0",
    val transactions: List<TransactionEntity>,
    val categories: List<CategoryEntity>,
    val budgets: List<BudgetEntity>,
    val accounts: List<AccountEntity>,
    val recurring: List<RecurringTransactionEntity>,
    val savingsGoals: List<SavingsGoalEntity>
)

object BackupRestoreHelper {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    suspend fun createBackupJson(database: ExpenseDatabase): String = withContext(Dispatchers.IO) {
        val backup = AppBackupData(
            exportDate = DateUtils.formatDateTime(System.currentTimeMillis()),
            transactions = database.transactionDao().getAllTransactionsList(),
            categories = database.categoryDao().getAllCategoriesList(),
            budgets = database.budgetDao().getBudgetsForMonthSync(""),
            accounts = database.accountDao().getAllAccountsList(),
            recurring = database.recurringTransactionDao().getDueRecurringSync(Long.MAX_VALUE),
            savingsGoals = database.savingsGoalDao().getAllGoalsList()
        )
        gson.toJson(backup)
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

    suspend fun restoreBackupFromJson(database: ExpenseDatabase, jsonString: String): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            val backup = gson.fromJson(jsonString, AppBackupData::class.java)
            if (backup != null) {
                if (backup.categories.isNotEmpty()) {
                    database.categoryDao().insertAll(backup.categories)
                }
                if (backup.accounts.isNotEmpty()) {
                    database.accountDao().insertAll(backup.accounts)
                }
                if (backup.budgets.isNotEmpty()) {
                    database.budgetDao().insertAll(backup.budgets)
                }
                if (backup.transactions.isNotEmpty()) {
                    database.transactionDao().insertAll(backup.transactions)
                }
                if (backup.recurring.isNotEmpty()) {
                    database.recurringTransactionDao().insertAll(backup.recurring)
                }
                if (backup.savingsGoals.isNotEmpty()) {
                    database.savingsGoalDao().insertAll(backup.savingsGoals)
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
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

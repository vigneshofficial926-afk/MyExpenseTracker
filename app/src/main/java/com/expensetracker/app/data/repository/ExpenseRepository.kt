package com.expensetracker.app.data.repository

import android.content.Context
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.BudgetEntity
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.RecurringTransactionEntity
import com.expensetracker.app.data.local.entity.SavingsGoalEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.RecurrenceFrequency
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.domain.FinancialCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class ExpenseRepository(
    private val database: ExpenseDatabase,
    private val context: Context
) {
    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()
    private val budgetDao = database.budgetDao()
    private val accountDao = database.accountDao()
    private val recurringDao = database.recurringTransactionDao()
    private val savingsGoalDao = database.savingsGoalDao()

    // --- Transactions ---
    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactions()
    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionEntity>> =
        transactionDao.getRecentTransactions(limit)

    fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>> =
        transactionDao.getTransactionsBetween(startTime, endTime)

    suspend fun getTransactionsBetweenSync(startTime: Long, endTime: Long): List<TransactionEntity> =
        transactionDao.getTransactionsBetweenSync(startTime, endTime)

    fun searchTransactions(query: String): Flow<List<TransactionEntity>> =
        transactionDao.searchTransactions(query)

    fun getTransactionById(id: Long): Flow<TransactionEntity?> =
        transactionDao.getTransactionById(id)

    suspend fun insertTransaction(transaction: TransactionEntity, allowDuplicate: Boolean = false): Result<Long> {
        return withContext(Dispatchers.IO) {
            if (!allowDuplicate) {
                val recentTransactions = transactionDao.getRecentTransactions(20)
                // Get sync list for quick duplicate check
                val isDup = FinancialCalculator.isDuplicateTransaction(
                    transaction,
                    transactionDao.getAllTransactionsList().take(30)
                )
                if (isDup) {
                    return@withContext Result.failure(
                        Exception("A transaction with identical details was recorded less than a minute ago. Tap again to confirm.")
                    )
                }
            }
            val id = transactionDao.insertTransaction(transaction)
            Result.success(id)
        }
    }

    suspend fun updateTransaction(transaction: TransactionEntity) {
        withContext(Dispatchers.IO) {
            transactionDao.updateTransaction(transaction)
        }
    }

    suspend fun deleteTransaction(transaction: TransactionEntity) {
        withContext(Dispatchers.IO) {
            transactionDao.deleteTransaction(transaction)
        }
    }

    suspend fun deleteTransactionById(id: Long) {
        withContext(Dispatchers.IO) {
            transactionDao.deleteTransactionById(id)
        }
    }

    // --- Categories ---
    val allCategories: Flow<List<CategoryEntity>> = categoryDao.getAllCategories()

    fun getCategoriesByType(type: TransactionType): Flow<List<CategoryEntity>> =
        categoryDao.getCategoriesByType(type)

    suspend fun insertCategory(category: CategoryEntity): Long =
        withContext(Dispatchers.IO) { categoryDao.insertCategory(category) }

    suspend fun updateCategory(category: CategoryEntity) =
        withContext(Dispatchers.IO) { categoryDao.updateCategory(category) }

    suspend fun deleteCategory(category: CategoryEntity) =
        withContext(Dispatchers.IO) { categoryDao.deleteCategory(category) }

    // --- Budgets ---
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(monthYear)

    fun getOverallBudget(monthYear: String): Flow<BudgetEntity?> =
        budgetDao.getOverallBudget(monthYear)

    suspend fun setBudget(budget: BudgetEntity): Long =
        withContext(Dispatchers.IO) { budgetDao.insertOrUpdateBudget(budget) }

    suspend fun deleteBudget(budget: BudgetEntity) =
        withContext(Dispatchers.IO) { budgetDao.deleteBudget(budget) }

    val allBudgetMonths: Flow<List<String>> = budgetDao.getAllBudgetMonths()

    // --- Accounts ---
    val allAccounts: Flow<List<AccountEntity>> = accountDao.getAllAccounts()

    suspend fun insertAccount(account: AccountEntity): Long =
        withContext(Dispatchers.IO) { accountDao.insertAccount(account) }

    suspend fun updateAccount(account: AccountEntity) =
        withContext(Dispatchers.IO) { accountDao.updateAccount(account) }

    // --- Recurring Transactions ---
    val allRecurring: Flow<List<RecurringTransactionEntity>> = recurringDao.getAllRecurring()

    suspend fun insertRecurring(recurring: RecurringTransactionEntity): Long =
        withContext(Dispatchers.IO) { recurringDao.insertRecurring(recurring) }

    suspend fun updateRecurring(recurring: RecurringTransactionEntity) =
        withContext(Dispatchers.IO) { recurringDao.updateRecurring(recurring) }

    suspend fun deleteRecurring(recurring: RecurringTransactionEntity) =
        withContext(Dispatchers.IO) { recurringDao.deleteRecurring(recurring) }

    /**
     * Checks and processes any due recurring transactions up to current time,
     * creating real transaction entries without creating duplicate items.
     */
    suspend fun processDueRecurringTransactions(): Int = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val dueRules = recurringDao.getDueRecurringSync(now)
        var createdCount = 0

        for (rule in dueRules) {
            // Avoid duplicate processing
            val newTx = TransactionEntity(
                amount = rule.amount,
                type = rule.type,
                categoryId = rule.categoryId,
                categoryName = rule.categoryName,
                dateTime = rule.nextDueDate,
                paymentMethod = rule.paymentMethod,
                description = "[Recurring] ${rule.title}" + if (rule.description.isNotBlank()) " - ${rule.description}" else "",
                tags = "recurring",
                recurringRuleId = rule.id
            )
            transactionDao.insertTransaction(newTx)
            createdCount++

            // Calculate next due date
            val nextDate = calculateNextDueDate(rule.nextDueDate, rule.frequency)
            val updatedRule = rule.copy(
                lastProcessedDate = rule.nextDueDate,
                nextDueDate = nextDate
            )
            recurringDao.updateRecurring(updatedRule)
        }
        createdCount
    }

    private fun calculateNextDueDate(currentDueDateMillis: Long, frequency: RecurrenceFrequency): Long {
        val zone = ZoneId.systemDefault()
        val currentLocalDate = Instant.ofEpochMilli(currentDueDateMillis).atZone(zone).toLocalDate()
        val nextLocalDate = when (frequency) {
            RecurrenceFrequency.DAILY -> currentLocalDate.plusDays(1)
            RecurrenceFrequency.WEEKLY -> currentLocalDate.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> currentLocalDate.plusMonths(1)
        }
        return nextLocalDate.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    // --- Savings Goals ---
    val allSavingsGoals: Flow<List<SavingsGoalEntity>> = savingsGoalDao.getAllGoals()

    suspend fun insertGoal(goal: SavingsGoalEntity): Long =
        withContext(Dispatchers.IO) { savingsGoalDao.insertGoal(goal) }

    suspend fun updateGoal(goal: SavingsGoalEntity) =
        withContext(Dispatchers.IO) { savingsGoalDao.updateGoal(goal) }

    suspend fun deleteGoal(goal: SavingsGoalEntity) =
        withContext(Dispatchers.IO) { savingsGoalDao.deleteGoal(goal) }

    suspend fun updateSavedAmount(goalId: Long, additionalAmount: Double) = withContext(Dispatchers.IO) {
        val goal = savingsGoalDao.getGoalByIdSync(goalId) ?: return@withContext
        val newTotal = (goal.savedAmount + additionalAmount).coerceAtLeast(0.0)
        val isCompleted = newTotal >= goal.targetAmount
        savingsGoalDao.updateSavedAmount(goalId, newTotal, isCompleted)
    }

    // --- App Reset / Clear Data ---
    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        transactionDao.deleteAllTransactions()
        budgetDao.deleteAllBudgets()
        recurringDao.deleteAllRecurring()
        savingsGoalDao.deleteAllGoals()
        categoryDao.deleteAllCategories()
        accountDao.deleteAllAccounts()
        ExpenseDatabase.populateCleanDefaultData(database)
    }

    // --- Direct Export Data Access ---
    suspend fun getAllTransactionsSync(): List<TransactionEntity> =
        withContext(Dispatchers.IO) { transactionDao.getAllTransactionsList() }

    suspend fun getAllCategoriesSync(): List<CategoryEntity> =
        withContext(Dispatchers.IO) { categoryDao.getAllCategoriesList() }

    suspend fun getAllAccountsSync(): List<AccountEntity> =
        withContext(Dispatchers.IO) { accountDao.getAllAccountsList() }

    suspend fun getAllGoalsSync(): List<SavingsGoalEntity> =
        withContext(Dispatchers.IO) { savingsGoalDao.getAllGoalsList() }
}

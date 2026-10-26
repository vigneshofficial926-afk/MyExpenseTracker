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
import androidx.room.withTransaction
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

    suspend fun insertTransaction(transaction: TransactionEntity, allowDuplicate: Boolean = false): Result<Long> =
        withContext(Dispatchers.IO) {
            if (!allowDuplicate) {
                val w = 60_000L
                val near = transactionDao.findPotentialDuplicates(
                    transaction.amount, transaction.type, transaction.categoryId,
                    transaction.dateTime - w, transaction.dateTime + w
                )
                if (FinancialCalculator.isDuplicateTransaction(transaction, near))
                    return@withContext Result.failure(Exception("A transaction with identical details was recorded less than a minute ago. Save anyway?"))
            }
            Result.success(transactionDao.insertTransaction(transaction))
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

    suspend fun updateCategory(category: CategoryEntity) = withContext(Dispatchers.IO) {
        database.withTransaction {
            categoryDao.updateCategory(category)
            transactionDao.renameCategory(category.id, category.name)
            recurringDao.renameCategory(category.id, category.name)
        }
    }

    suspend fun deleteCategory(category: CategoryEntity) =
        withContext(Dispatchers.IO) { categoryDao.deleteCategory(category) }

    // --- Budgets ---
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>> =
        budgetDao.getBudgetsForMonth(monthYear)

    fun getOverallBudget(monthYear: String): Flow<BudgetEntity?> =
        budgetDao.getOverallBudget(monthYear)

    suspend fun setBudget(budget: BudgetEntity): Long = withContext(Dispatchers.IO) {
        if (budget.categoryId == null) {
            val existing = budgetDao.getOverallBudgetSync(budget.monthYear)
            if (existing != null) {
                budgetDao.updateBudget(budget.copy(id = existing.id))
                existing.id
            } else {
                budgetDao.insertOrUpdateBudget(budget)
            }
        } else {
            val existing = budgetDao.getCategoryBudgetSync(budget.monthYear, budget.categoryId)
            if (existing != null) {
                budgetDao.updateBudget(budget.copy(id = existing.id))
                existing.id
            } else {
                budgetDao.insertOrUpdateBudget(budget)
            }
        }
    }

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
        val zone = ZoneId.systemDefault()
        var created = 0
        database.withTransaction {
            for (rule in recurringDao.getDueRecurringSync(now)) {
                val anchorDay = Instant.ofEpochMilli(rule.startDate).atZone(zone).dayOfMonth
                var due = rule.nextDueDate
                var last: Long? = null
                var guard = 0
                while (due <= now && guard++ < 366) {          // catch up every missed period
                    transactionDao.insertTransaction(
                        TransactionEntity(
                            amount = rule.amount, type = rule.type, categoryId = rule.categoryId,
                            categoryName = rule.categoryName, dateTime = due, paymentMethod = rule.paymentMethod,
                            description = "[Recurring] ${rule.title}" + if (rule.description.isNotBlank()) " - ${rule.description}" else "",
                            tags = "recurring", recurringRuleId = rule.id
                        )
                    )
                    created++; last = due
                    due = nextDueDate(due, rule.frequency, anchorDay, zone)
                }
                recurringDao.updateRecurring(rule.copy(lastProcessedDate = last ?: rule.lastProcessedDate, nextDueDate = due))
            }
        }
        created
    }

    private fun nextDueDate(current: Long, f: RecurrenceFrequency, anchorDay: Int, zone: ZoneId): Long {
        val d = Instant.ofEpochMilli(current).atZone(zone).toLocalDate()
        val next = when (f) {
            RecurrenceFrequency.DAILY -> d.plusDays(1)
            RecurrenceFrequency.WEEKLY -> d.plusWeeks(1)
            RecurrenceFrequency.MONTHLY -> d.plusMonths(1).withDayOfMonth(1).let { it.withDayOfMonth(minOf(anchorDay, it.lengthOfMonth())) }
        }
        return next.atStartOfDay(zone).toInstant().toEpochMilli()
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

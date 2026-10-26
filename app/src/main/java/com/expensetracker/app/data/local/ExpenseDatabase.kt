package com.expensetracker.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.expensetracker.app.data.local.converter.Converters
import com.expensetracker.app.data.local.dao.AccountDao
import com.expensetracker.app.data.local.dao.BudgetDao
import com.expensetracker.app.data.local.dao.CategoryDao
import com.expensetracker.app.data.local.dao.RecurringTransactionDao
import com.expensetracker.app.data.local.dao.SavingsGoalDao
import com.expensetracker.app.data.local.dao.TransactionDao
import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.BudgetEntity
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.RecurringTransactionEntity
import com.expensetracker.app.data.local.entity.SavingsGoalEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.RecurrenceFrequency
import com.expensetracker.app.data.model.TransactionType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        BudgetEntity::class,
        AccountEntity::class,
        RecurringTransactionEntity::class,
        SavingsGoalEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ExpenseDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun accountDao(): AccountDao
    abstract fun recurringTransactionDao(): RecurringTransactionDao
    abstract fun savingsGoalDao(): SavingsGoalDao

    companion object {
        @Volatile
        private var INSTANCE: ExpenseDatabase? = null

        fun getInstance(context: Context): ExpenseDatabase = getDatabase(context)

        fun getDatabase(context: Context): ExpenseDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ExpenseDatabase::class.java,
                    "my_expense_tracker.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun populateInitialData(database: ExpenseDatabase) {
            val categoryDao = database.categoryDao()
            val accountDao = database.accountDao()
            val budgetDao = database.budgetDao()
            val savingsGoalDao = database.savingsGoalDao()
            val recurringDao = database.recurringTransactionDao()

            // 1. Default Expense Categories
            val defaultExpenseCategories = listOf(
                CategoryEntity(name = "Food and Groceries", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#F97316", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Hotel and Restaurants", type = TransactionType.EXPENSE, iconName = "local_cafe", colorHex = "#EA580C", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Tea, Coffee and Snacks", type = TransactionType.EXPENSE, iconName = "coffee", colorHex = "#B45309", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Transportation and Fuel", type = TransactionType.EXPENSE, iconName = "directions_car", colorHex = "#3B82F6", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "Rent", type = TransactionType.EXPENSE, iconName = "home", colorHex = "#6366F1", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "Electricity and Utilities", type = TransactionType.EXPENSE, iconName = "bolt", colorHex = "#EAB308", isDefault = true, sortOrder = 6),
                CategoryEntity(name = "Mobile and Internet", type = TransactionType.EXPENSE, iconName = "phone_android", colorHex = "#06B6D4", isDefault = true, sortOrder = 7),
                CategoryEntity(name = "Shopping", type = TransactionType.EXPENSE, iconName = "shopping_bag", colorHex = "#EC4899", isDefault = true, sortOrder = 8),
                CategoryEntity(name = "Health and Medical", type = TransactionType.EXPENSE, iconName = "local_hospital", colorHex = "#EF4444", isDefault = true, sortOrder = 9),
                CategoryEntity(name = "Entertainment", type = TransactionType.EXPENSE, iconName = "movie", colorHex = "#8B5CF6", isDefault = true, sortOrder = 10),
                CategoryEntity(name = "Travel", type = TransactionType.EXPENSE, iconName = "flight", colorHex = "#14B8A6", isDefault = true, sortOrder = 11),
                CategoryEntity(name = "Education", type = TransactionType.EXPENSE, iconName = "school", colorHex = "#10B981", isDefault = true, sortOrder = 12),
                CategoryEntity(name = "EMI and Loan Payments", type = TransactionType.EXPENSE, iconName = "credit_card", colorHex = "#D97706", isDefault = true, sortOrder = 13),
                CategoryEntity(name = "Family Expenses", type = TransactionType.EXPENSE, iconName = "groups", colorHex = "#F43F5E", isDefault = true, sortOrder = 14),
                CategoryEntity(name = "Subscriptions", type = TransactionType.EXPENSE, iconName = "subscriptions", colorHex = "#A855F7", isDefault = true, sortOrder = 15),
                CategoryEntity(name = "Other Expenses", type = TransactionType.EXPENSE, iconName = "more_horiz", colorHex = "#64748B", isDefault = true, sortOrder = 16)
            )
            categoryDao.insertAll(defaultExpenseCategories)

            // 2. Default Income Categories
            val defaultIncomeCategories = listOf(
                CategoryEntity(name = "Salary", type = TransactionType.INCOME, iconName = "account_balance_wallet", colorHex = "#10B981", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Freelance Income", type = TransactionType.INCOME, iconName = "laptop_mac", colorHex = "#0EA5E9", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Business Income", type = TransactionType.INCOME, iconName = "trending_up", colorHex = "#8B5CF6", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Interest", type = TransactionType.INCOME, iconName = "savings", colorHex = "#14B8A6", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "Gifts", type = TransactionType.INCOME, iconName = "card_giftcard", colorHex = "#EC4899", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "Other Income", type = TransactionType.INCOME, iconName = "attach_money", colorHex = "#64748B", isDefault = true, sortOrder = 6)
            )
            categoryDao.insertAll(defaultIncomeCategories)

            // 3. Default Accounts
            val defaultAccounts = listOf(
                AccountEntity(name = "Primary Bank (HDFC)", accountType = "BANK", openingBalance = 45000.0, currentBalance = 45000.0, colorHex = "#0D9488"),
                AccountEntity(name = "Cash Wallet", accountType = "CASH", openingBalance = 5000.0, currentBalance = 5000.0, colorHex = "#0284C7"),
                AccountEntity(name = "Credit Card", accountType = "CREDIT_CARD", openingBalance = 0.0, currentBalance = 0.0, colorHex = "#F43F5E")
            )
            accountDao.insertAll(defaultAccounts)

            // 4. Default Current Month Budget
            val currentMonthYear = YearMonth.now().format(DateTimeFormatter.ofPattern("MM-yyyy"))
            budgetDao.insertOrUpdateBudget(
                BudgetEntity(monthYear = currentMonthYear, categoryId = null, budgetAmount = 35000.0, alertThresholdPercent = 80)
            )

            // 5. Default Savings Goal
            val targetDate = LocalDate.now().plusMonths(6).toEpochDay() * 86400000L
            savingsGoalDao.insertGoal(
                SavingsGoalEntity(
                    name = "Emergency Fund",
                    targetAmount = 100000.0,
                    savedAmount = 35000.0,
                    targetDate = targetDate,
                    colorHex = "#0D9488",
                    iconName = "savings",
                    notes = "Target 6 months living expenses"
                )
            )

            // 6. Sample Recurring Transaction
            val nextMonthFirst = LocalDate.now().plusMonths(1).withDayOfMonth(1).toEpochDay() * 86400000L
            recurringDao.insertRecurring(
                RecurringTransactionEntity(
                    title = "House Rent",
                    amount = 12000.0,
                    type = TransactionType.EXPENSE,
                    categoryId = 5,
                    categoryName = "Rent",
                    paymentMethod = PaymentMethod.BANK_TRANSFER,
                    frequency = RecurrenceFrequency.MONTHLY,
                    startDate = LocalDate.now().toEpochDay() * 86400000L,
                    nextDueDate = nextMonthFirst,
                    isActive = true,
                    description = "Monthly apartment rent"
                )
            )
        }

        suspend fun populateCleanDefaultData(database: ExpenseDatabase) {
            val categoryDao = database.categoryDao()
            val accountDao = database.accountDao()

            val defaultExpenseCategories = listOf(
                CategoryEntity(name = "Food and Groceries", type = TransactionType.EXPENSE, iconName = "restaurant", colorHex = "#F97316", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Hotel and Restaurants", type = TransactionType.EXPENSE, iconName = "local_cafe", colorHex = "#EA580C", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Tea, Coffee and Snacks", type = TransactionType.EXPENSE, iconName = "coffee", colorHex = "#B45309", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Transportation and Fuel", type = TransactionType.EXPENSE, iconName = "directions_car", colorHex = "#3B82F6", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "Rent", type = TransactionType.EXPENSE, iconName = "home", colorHex = "#6366F1", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "Electricity and Utilities", type = TransactionType.EXPENSE, iconName = "bolt", colorHex = "#EAB308", isDefault = true, sortOrder = 6),
                CategoryEntity(name = "Mobile and Internet", type = TransactionType.EXPENSE, iconName = "phone_android", colorHex = "#06B6D4", isDefault = true, sortOrder = 7),
                CategoryEntity(name = "Shopping", type = TransactionType.EXPENSE, iconName = "shopping_bag", colorHex = "#EC4899", isDefault = true, sortOrder = 8),
                CategoryEntity(name = "Health and Medical", type = TransactionType.EXPENSE, iconName = "local_hospital", colorHex = "#EF4444", isDefault = true, sortOrder = 9),
                CategoryEntity(name = "Entertainment", type = TransactionType.EXPENSE, iconName = "movie", colorHex = "#8B5CF6", isDefault = true, sortOrder = 10),
                CategoryEntity(name = "Travel", type = TransactionType.EXPENSE, iconName = "flight", colorHex = "#14B8A6", isDefault = true, sortOrder = 11),
                CategoryEntity(name = "Education", type = TransactionType.EXPENSE, iconName = "school", colorHex = "#10B981", isDefault = true, sortOrder = 12),
                CategoryEntity(name = "EMI and Loan Payments", type = TransactionType.EXPENSE, iconName = "credit_card", colorHex = "#D97706", isDefault = true, sortOrder = 13),
                CategoryEntity(name = "Family Expenses", type = TransactionType.EXPENSE, iconName = "groups", colorHex = "#F43F5E", isDefault = true, sortOrder = 14),
                CategoryEntity(name = "Subscriptions", type = TransactionType.EXPENSE, iconName = "subscriptions", colorHex = "#A855F7", isDefault = true, sortOrder = 15),
                CategoryEntity(name = "Other Expenses", type = TransactionType.EXPENSE, iconName = "more_horiz", colorHex = "#64748B", isDefault = true, sortOrder = 16)
            )
            categoryDao.insertAll(defaultExpenseCategories)

            val defaultIncomeCategories = listOf(
                CategoryEntity(name = "Salary", type = TransactionType.INCOME, iconName = "account_balance_wallet", colorHex = "#10B981", isDefault = true, sortOrder = 1),
                CategoryEntity(name = "Freelance Income", type = TransactionType.INCOME, iconName = "laptop_mac", colorHex = "#0EA5E9", isDefault = true, sortOrder = 2),
                CategoryEntity(name = "Business Income", type = TransactionType.INCOME, iconName = "trending_up", colorHex = "#8B5CF6", isDefault = true, sortOrder = 3),
                CategoryEntity(name = "Interest", type = TransactionType.INCOME, iconName = "savings", colorHex = "#14B8A6", isDefault = true, sortOrder = 4),
                CategoryEntity(name = "Gifts", type = TransactionType.INCOME, iconName = "card_giftcard", colorHex = "#EC4899", isDefault = true, sortOrder = 5),
                CategoryEntity(name = "Other Income", type = TransactionType.INCOME, iconName = "attach_money", colorHex = "#64748B", isDefault = true, sortOrder = 6)
            )
            categoryDao.insertAll(defaultIncomeCategories)

            val defaultAccounts = listOf(
                AccountEntity(name = "Primary Bank (HDFC)", accountType = "BANK", openingBalance = 0.0, currentBalance = 0.0, colorHex = "#0D9488"),
                AccountEntity(name = "Cash Wallet", accountType = "CASH", openingBalance = 0.0, currentBalance = 0.0, colorHex = "#0284C7"),
                AccountEntity(name = "Credit Card", accountType = "CREDIT_CARD", openingBalance = 0.0, currentBalance = 0.0, colorHex = "#F43F5E")
            )
            accountDao.insertAll(defaultAccounts)
        }
    }
}

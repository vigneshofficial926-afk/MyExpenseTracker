package com.expensetracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.app.data.local.entity.BudgetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear ORDER BY id DESC")
    fun getBudgetsForMonth(monthYear: String): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear ORDER BY id DESC")
    suspend fun getBudgetsForMonthSync(monthYear: String): List<BudgetEntity>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear AND categoryId IS NULL ORDER BY id DESC LIMIT 1")
    fun getOverallBudget(monthYear: String): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear AND categoryId IS NULL ORDER BY id DESC LIMIT 1")
    suspend fun getOverallBudgetSync(monthYear: String): BudgetEntity?

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear AND categoryId = :categoryId ORDER BY id DESC LIMIT 1")
    fun getCategoryBudget(monthYear: String, categoryId: Long): Flow<BudgetEntity?>

    @Query("SELECT * FROM budgets WHERE monthYear = :monthYear AND categoryId = :categoryId ORDER BY id DESC LIMIT 1")
    suspend fun getCategoryBudgetSync(monthYear: String, categoryId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets ORDER BY monthYear DESC")
    fun getAllBudgets(): Flow<List<BudgetEntity>>

    @Query("SELECT DISTINCT monthYear FROM budgets ORDER BY monthYear DESC")
    fun getAllBudgetMonths(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateBudget(budget: BudgetEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(budgets: List<BudgetEntity>)

    @Update
    suspend fun updateBudget(budget: BudgetEntity)

    @Delete
    suspend fun deleteBudget(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE id = :id")
    suspend fun deleteBudgetById(id: Long)

    @Query("DELETE FROM budgets")
    suspend fun deleteAllBudgets()
}

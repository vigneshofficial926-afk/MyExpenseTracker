package com.expensetracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.app.data.local.entity.SavingsGoalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavingsGoalDao {

    @Query("SELECT * FROM savings_goals ORDER BY isCompleted ASC, targetDate ASC")
    fun getAllGoals(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals ORDER BY isCompleted ASC, targetDate ASC")
    suspend fun getAllGoalsList(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE id = :id LIMIT 1")
    fun getGoalById(id: Long): Flow<SavingsGoalEntity?>

    @Query("SELECT * FROM savings_goals WHERE id = :id LIMIT 1")
    suspend fun getGoalByIdSync(id: Long): SavingsGoalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: SavingsGoalEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(goals: List<SavingsGoalEntity>)

    @Update
    suspend fun updateGoal(goal: SavingsGoalEntity)

    @Query("UPDATE savings_goals SET savedAmount = :newSavedAmount, isCompleted = :isCompleted WHERE id = :id")
    suspend fun updateSavedAmount(id: Long, newSavedAmount: Double, isCompleted: Boolean)

    @Delete
    suspend fun deleteGoal(goal: SavingsGoalEntity)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun deleteGoalById(id: Long)

    @Query("DELETE FROM savings_goals")
    suspend fun deleteAllGoals()
}

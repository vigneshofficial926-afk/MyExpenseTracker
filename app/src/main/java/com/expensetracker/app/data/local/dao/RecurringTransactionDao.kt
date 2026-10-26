package com.expensetracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.app.data.local.entity.RecurringTransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringTransactionDao {

    @Query("SELECT * FROM recurring_transactions ORDER BY nextDueDate ASC")
    fun getAllRecurring(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions")
    suspend fun getAllRecurringList(): List<RecurringTransactionEntity>

    @Query("UPDATE recurring_transactions SET categoryName = :newName WHERE categoryId = :categoryId")
    suspend fun renameCategory(categoryId: Long, newName: String)

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 ORDER BY nextDueDate ASC")
    fun getActiveRecurring(): Flow<List<RecurringTransactionEntity>>

    @Query("SELECT * FROM recurring_transactions WHERE isActive = 1 AND nextDueDate <= :currentTimestamp")
    suspend fun getDueRecurringSync(currentTimestamp: Long): List<RecurringTransactionEntity>

    @Query("SELECT * FROM recurring_transactions WHERE id = :id LIMIT 1")
    fun getRecurringById(id: Long): Flow<RecurringTransactionEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecurring(recurring: RecurringTransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(recurringList: List<RecurringTransactionEntity>)

    @Update
    suspend fun updateRecurring(recurring: RecurringTransactionEntity)

    @Delete
    suspend fun deleteRecurring(recurring: RecurringTransactionEntity)

    @Query("DELETE FROM recurring_transactions WHERE id = :id")
    suspend fun deleteRecurringById(id: Long)

    @Query("DELETE FROM recurring_transactions")
    suspend fun deleteAllRecurring()
}

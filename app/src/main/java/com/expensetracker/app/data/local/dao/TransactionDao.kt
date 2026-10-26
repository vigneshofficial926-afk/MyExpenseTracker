package com.expensetracker.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions ORDER BY dateTime DESC, id DESC")
    fun getAllTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY dateTime DESC, id DESC")
    suspend fun getAllTransactionsList(): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY dateTime DESC, id DESC LIMIT :limit")
    suspend fun getRecentTransactionsList(limit: Int): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE amount = :amount AND type = :type AND categoryId = :categoryId AND dateTime BETWEEN :from AND :to")
    suspend fun findPotentialDuplicates(amount: Double, type: TransactionType, categoryId: Long, from: Long, to: Long): List<TransactionEntity>

    @Query("UPDATE transactions SET categoryName = :newName WHERE categoryId = :categoryId")
    suspend fun renameCategory(categoryId: Long, newName: String)

    @Query("SELECT * FROM transactions WHERE dateTime >= :startTime AND dateTime <= :endTime ORDER BY dateTime DESC, id DESC")
    fun getTransactionsBetween(startTime: Long, endTime: Long): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE dateTime >= :startTime AND dateTime <= :endTime ORDER BY dateTime DESC, id DESC")
    suspend fun getTransactionsBetweenSync(startTime: Long, endTime: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY dateTime DESC, id DESC LIMIT :limit")
    fun getRecentTransactions(limit: Int = 10): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    fun getTransactionById(id: Long): Flow<TransactionEntity?>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionByIdSync(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE description LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' OR categoryName LIKE '%' || :query || '%' ORDER BY dateTime DESC")
    fun searchTransactions(query: String): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    @Update
    suspend fun updateTransaction(transaction: TransactionEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}

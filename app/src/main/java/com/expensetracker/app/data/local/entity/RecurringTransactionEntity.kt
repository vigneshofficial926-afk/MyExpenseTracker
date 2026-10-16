package com.expensetracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.RecurrenceFrequency
import com.expensetracker.app.data.model.TransactionType

@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val categoryName: String,
    val paymentMethod: PaymentMethod,
    val frequency: RecurrenceFrequency,
    val startDate: Long,
    val nextDueDate: Long,
    val lastProcessedDate: Long? = null,
    val isActive: Boolean = true,
    val description: String = ""
)

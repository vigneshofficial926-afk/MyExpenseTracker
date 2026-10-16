package com.expensetracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.TransactionType

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["dateTime"]),
        Index(value = ["categoryId"]),
        Index(value = ["type"]),
        Index(value = ["paymentMethod"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val amount: Double,
    val type: TransactionType,
    val categoryId: Long,
    val categoryName: String,
    val dateTime: Long, // Epoch timestamp in milliseconds
    val paymentMethod: PaymentMethod,
    val description: String = "",
    val tags: String = "", // Comma-separated tags
    val sourceAccountId: Long? = null,
    val destinationAccountId: Long? = null,
    val isCreditCardBillPayment: Boolean = false,
    val isExcludedFromBudget: Boolean = false,
    val recurringRuleId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

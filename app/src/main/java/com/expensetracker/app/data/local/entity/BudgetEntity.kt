package com.expensetracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    indices = [Index(value = ["monthYear", "categoryId"], unique = true)]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val monthYear: String, // Format: MM-yyyy, e.g., "10-2026"
    val categoryId: Long? = null, // null for total overall monthly budget
    val budgetAmount: Double,
    val alertThresholdPercent: Int = 80
)

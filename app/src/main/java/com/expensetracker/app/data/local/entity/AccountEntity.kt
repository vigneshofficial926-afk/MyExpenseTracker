package com.expensetracker.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val accountType: String, // CASH, BANK, CREDIT_CARD
    val openingBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val colorHex: String = "#0D9488"
)

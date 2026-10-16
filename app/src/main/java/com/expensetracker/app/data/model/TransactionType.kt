package com.expensetracker.app.data.model

enum class TransactionType(val displayName: String) {
    EXPENSE("Expense"),
    INCOME("Income"),
    TRANSFER("Transfer"),
    REFUND("Refund");

    companion object {
        fun fromString(value: String): TransactionType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: EXPENSE
        }
    }
}

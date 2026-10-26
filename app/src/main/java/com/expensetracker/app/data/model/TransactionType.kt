package com.expensetracker.app.data.model

enum class TransactionType(val displayName: String) {
    EXPENSE("Expense"), INCOME("Income"), TRANSFER("Transfer"), REFUND("Refund");

    /** Categories exist only for EXPENSE/INCOME; TRANSFER and REFUND reuse expense categories. */
    val categoryGroup: TransactionType get() = if (this == INCOME) INCOME else EXPENSE

    companion object {
        fun fromString(value: String): TransactionType =
            entries.find { it.name.equals(value, ignoreCase = true) } ?: EXPENSE
    }
}

package com.expensetracker.app

import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.domain.FinancialCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FinancialCalculatorTest {

    @Test
    fun calculateTotalBalance_withOpeningBalanceIncomeAndExpense() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "Bank", accountType = "BANK", openingBalance = 50000.0),
            AccountEntity(id = 2, name = "Cash", accountType = "CASH", openingBalance = 5000.0)
        )
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                amount = 25000.0,
                type = TransactionType.INCOME,
                categoryId = 1,
                categoryName = "Salary",
                dateTime = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.BANK_TRANSFER
            ),
            TransactionEntity(
                id = 2,
                amount = 12000.0,
                type = TransactionType.EXPENSE,
                categoryId = 2,
                categoryName = "Rent",
                dateTime = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.BANK_TRANSFER
            ),
            TransactionEntity(
                id = 3,
                amount = 1500.0,
                type = TransactionType.EXPENSE,
                categoryId = 3,
                categoryName = "Groceries",
                dateTime = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.UPI
            )
        )

        // Opening balance: 50,000 + 5,000 = 55,000
        // + Income: 25,000 -> 80,000
        // - Expenses: 12,000 + 1,500 = 13,500 -> 66,500
        val balance = FinancialCalculator.calculateTotalBalance(accounts, transactions)
        assertEquals(66500.0, balance, 0.001)
    }

    @Test
    fun calculateTotalBalance_excludesAccountTransfers() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "Bank", accountType = "BANK", openingBalance = 40000.0)
        )
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                amount = 5000.0,
                type = TransactionType.TRANSFER, // Personal internal transfer
                categoryId = 0,
                categoryName = "Transfer",
                dateTime = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.BANK_TRANSFER
            )
        )
        // Internal transfer between own accounts must not change overall net balance
        val balance = FinancialCalculator.calculateTotalBalance(accounts, transactions)
        assertEquals(40000.0, balance, 0.001)
    }

    @Test
    fun calculateTotalBalance_creditCardBillPaymentNotDoubleCounted() {
        val accounts = listOf(
            AccountEntity(id = 1, name = "Bank", accountType = "BANK", openingBalance = 50000.0)
        )
        val transactions = listOf(
            // Individual CC purchase (already counted as expense)
            TransactionEntity(
                id = 1,
                amount = 4000.0,
                type = TransactionType.EXPENSE,
                categoryId = 1,
                categoryName = "Shopping",
                dateTime = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.CREDIT_CARD,
                isCreditCardBillPayment = false
            ),
            // Credit card bill payment (must not count as second expense)
            TransactionEntity(
                id = 2,
                amount = 4000.0,
                type = TransactionType.EXPENSE,
                categoryId = 2,
                categoryName = "Credit Card Bill",
                dateTime = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.BANK_TRANSFER,
                isCreditCardBillPayment = true
            )
        )

        // 50,000 - 4,000 (purchase) = 46,000 (bill settlement is excluded)
        val balance = FinancialCalculator.calculateTotalBalance(accounts, transactions)
        assertEquals(46000.0, balance, 0.001)
    }

    @Test
    fun calculateMonthlyExpenses_withRefundAdjustment() {
        val transactions = listOf(
            TransactionEntity(
                id = 1,
                amount = 3000.0,
                type = TransactionType.EXPENSE,
                categoryId = 1,
                categoryName = "Shopping",
                dateTime = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.UPI
            ),
            TransactionEntity(
                id = 2,
                amount = 500.0,
                type = TransactionType.REFUND, // Refund for returned product
                categoryId = 1,
                categoryName = "Shopping",
                dateTime = System.currentTimeMillis(),
                paymentMethod = PaymentMethod.UPI
            )
        )

        // Net expense: 3,000 - 500 = 2,500
        val netExpense = FinancialCalculator.calculateMonthlyExpenses(transactions)
        assertEquals(2500.0, netExpense, 0.001)
    }

    @Test
    fun calculateBudgetRemainingAndUtilization() {
        val budget = 40000.0
        val spent = 32000.0

        val remaining = FinancialCalculator.calculateBudgetRemaining(budget, spent)
        val utilization = FinancialCalculator.calculateBudgetUtilization(budget, spent)

        assertEquals(8000.0, remaining, 0.001)
        assertEquals(80.0f, utilization, 0.01f)
    }

    @Test
    fun calculateRequiredMonthlySavings() {
        val targetAmount = 60000.0
        val savedAmount = 10000.0
        val today = LocalDate.of(2026, 1, 1)
        // Target date 5 months later: 2026-06-01
        val targetMillis = LocalDate.of(2026, 6, 1).toEpochDay() * 86400000L

        // Remaining to save: 50,000 over 5 months = 10,000 per month
        val monthlyNeeded = FinancialCalculator.calculateRequiredMonthlySavings(
            targetAmount = targetAmount,
            savedAmount = savedAmount,
            targetEpochMillis = targetMillis,
            currentDate = today
        )
        assertEquals(10000.0, monthlyNeeded, 0.01)
    }

    @Test
    fun isDuplicateTransaction_detectsDuplicateWithinOneMinute() {
        val now = System.currentTimeMillis()
        val original = TransactionEntity(
            id = 1,
            amount = 150.0,
            type = TransactionType.EXPENSE,
            categoryId = 3,
            categoryName = "Lunch",
            dateTime = now,
            paymentMethod = PaymentMethod.UPI
        )
        val candidate = TransactionEntity(
            id = 0,
            amount = 150.0,
            type = TransactionType.EXPENSE,
            categoryId = 3,
            categoryName = "Lunch",
            dateTime = now + 15_000L, // 15 seconds later
            paymentMethod = PaymentMethod.UPI
        )

        val isDup = FinancialCalculator.isDuplicateTransaction(candidate, listOf(original))
        assertTrue(isDup)

        // Different category -> Not duplicate
        val differentCat = candidate.copy(categoryId = 4)
        assertFalse(FinancialCalculator.isDuplicateTransaction(differentCat, listOf(original)))

        // Outside time window (e.g. 5 minutes later) -> Not duplicate
        val later = candidate.copy(dateTime = now + 300_000L)
        assertFalse(FinancialCalculator.isDuplicateTransaction(later, listOf(original)))
    }
}

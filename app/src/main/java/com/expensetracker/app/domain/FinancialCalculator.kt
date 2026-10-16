package com.expensetracker.app.domain

import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object FinancialCalculator {

    /**
     * Total balance = sum of account opening balances + total valid income - total valid expenses + net refunds
     * Transfers between personal accounts are excluded.
     * Credit card bill payments are excluded from expenses (as individual purchases were already counted).
     */
    fun calculateTotalBalance(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>
    ): Double {
        val openingBalanceSum = accounts
            .filter { it.accountType != "CREDIT_CARD" }
            .sumOf { it.openingBalance }

        var incomeTotal = BigDecimal.ZERO
        var expenseTotal = BigDecimal.ZERO

        for (tx in transactions) {
            when (tx.type) {
                TransactionType.INCOME -> {
                    incomeTotal = incomeTotal.add(BigDecimal.valueOf(tx.amount))
                }
                TransactionType.EXPENSE -> {
                    // Credit card bill payments must not count as a second expense
                    if (!tx.isCreditCardBillPayment) {
                        expenseTotal = expenseTotal.add(BigDecimal.valueOf(tx.amount))
                    }
                }
                TransactionType.REFUND -> {
                    // Refund adds back to balance (acts as income or negative expense)
                    incomeTotal = incomeTotal.add(BigDecimal.valueOf(tx.amount))
                }
                TransactionType.TRANSFER -> {
                    // Transfers between own accounts do not affect total net balance
                }
            }
        }

        val total = BigDecimal.valueOf(openingBalanceSum)
            .add(incomeTotal)
            .subtract(expenseTotal)
            .setScale(2, RoundingMode.HALF_EVEN)

        return total.toDouble()
    }

    /**
     * Calculates monthly total income for eligible transactions.
     * Excludes transfers and CC bill payments.
     */
    fun calculateMonthlyIncome(transactions: List<TransactionEntity>): Double {
        var total = BigDecimal.ZERO
        for (tx in transactions) {
            if (tx.type == TransactionType.INCOME) {
                total = total.add(BigDecimal.valueOf(tx.amount))
            }
        }
        return total.setScale(2, RoundingMode.HALF_EVEN).toDouble()
    }

    /**
     * Calculates monthly total expenses.
     * Excludes transfers and credit card bill payments.
     * Deducts refunds if treated as expense adjustments.
     */
    fun calculateMonthlyExpenses(transactions: List<TransactionEntity>): Double {
        var expenseTotal = BigDecimal.ZERO
        var refundTotal = BigDecimal.ZERO

        for (tx in transactions) {
            if (tx.type == TransactionType.EXPENSE && !tx.isCreditCardBillPayment) {
                expenseTotal = expenseTotal.add(BigDecimal.valueOf(tx.amount))
            } else if (tx.type == TransactionType.REFUND) {
                refundTotal = refundTotal.add(BigDecimal.valueOf(tx.amount))
            }
        }

        val netExpenses = expenseTotal.subtract(refundTotal).coerceAtLeast(BigDecimal.ZERO)
        return netExpenses.setScale(2, RoundingMode.HALF_EVEN).toDouble()
    }

    /**
     * Calculates remaining monthly budget:
     * Budget remaining = monthly budget - eligible expenses
     */
    fun calculateBudgetRemaining(budgetAmount: Double, eligibleExpenses: Double): Double {
        val remaining = BigDecimal.valueOf(budgetAmount)
            .subtract(BigDecimal.valueOf(eligibleExpenses))
            .setScale(2, RoundingMode.HALF_EVEN)
        return remaining.toDouble()
    }

    /**
     * Budget utilization percentage (0.0 to 100.0+)
     */
    fun calculateBudgetUtilization(budgetAmount: Double, eligibleExpenses: Double): Float {
        if (budgetAmount <= 0.0) return 0f
        val pct = (eligibleExpenses / budgetAmount) * 100f
        return pct.toFloat()
    }

    /**
     * Required monthly savings for a savings goal:
     * monthlySavings = (targetAmount - savedAmount) / remainingMonths
     */
    fun calculateRequiredMonthlySavings(
        targetAmount: Double,
        savedAmount: Double,
        targetEpochMillis: Long,
        currentDate: LocalDate = LocalDate.now()
    ): Double {
        val remainingToSave = targetAmount - savedAmount
        if (remainingToSave <= 0.0) return 0.0

        val targetDate = LocalDate.ofEpochDay(targetEpochMillis / 86400000L)
        val monthsRemaining = ChronoUnit.MONTHS.between(currentDate, targetDate).coerceAtLeast(1)

        val monthly = BigDecimal.valueOf(remainingToSave)
            .divide(BigDecimal.valueOf(monthsRemaining), 2, RoundingMode.HALF_UP)
        return monthly.toDouble()
    }

    /**
     * Duplicate check: checks whether a transaction with the same amount, category, type,
     * and within 60 seconds already exists to prevent accidental double-tap entries.
     */
    fun isDuplicateTransaction(
        candidate: TransactionEntity,
        existingList: List<TransactionEntity>,
        timeWindowMillis: Long = 60_000L
    ): Boolean {
        return existingList.any { existing ->
            existing.id != candidate.id &&
            existing.amount == candidate.amount &&
            existing.categoryId == candidate.categoryId &&
            existing.type == candidate.type &&
            Math.abs(existing.dateTime - candidate.dateTime) <= timeWindowMillis
        }
    }
}

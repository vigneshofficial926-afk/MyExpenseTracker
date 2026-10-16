package com.expensetracker.app.util

import java.text.DecimalFormat
import java.text.NumberFormat
import java.util.Locale

object CurrencyFormatter {

    private val indianFormat: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }

    private val decimalFormat = DecimalFormat("#,##,##0.00")

    fun format(amount: Double, includeDecimalsIfZero: Boolean = false): String {
        val sign = if (amount < 0) "-" else ""
        val absAmount = Math.abs(amount)
        return if (!includeDecimalsIfZero && absAmount % 1.0 == 0.0) {
            val noDecFormat = DecimalFormat("#,##,##0")
            "$sign₹${noDecFormat.format(absAmount)}"
        } else {
            "$sign₹${decimalFormat.format(absAmount)}"
        }
    }

    fun formatCompact(amount: Double): String {
        val abs = Math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            abs >= 10_000_000 -> "$sign₹${String.format(Locale.US, "%.1f", abs / 10_000_000.0)} Cr"
            abs >= 100_000 -> "$sign₹${String.format(Locale.US, "%.1f", abs / 100_000.0)} L"
            abs >= 1_000 -> "$sign₹${String.format(Locale.US, "%.1f", abs / 1_000.0)} k"
            else -> "$sign₹${DecimalFormat("#,##0").format(abs)}"
        }
    }
}

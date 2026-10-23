package com.expensetracker.app

import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.util.SmsTransactionParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsTransactionParserTest {

    @Test
    fun parse_hdfcUpiDebit_extractsCorrectDetails() {
        val sms = "Dear Customer, Rs.450.00 has been debited from your A/c **4321 on 23-OCT-26 to VPA swiggy@icici via UPI. UPI Ref 429712345678. Avl Bal Rs.32450.00 - HDFC Bank"
        val parsed = SmsTransactionParser.parse("VM-HDFCBK", sms)

        assertNotNull(parsed)
        assertEquals(450.0, parsed!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(PaymentMethod.UPI, parsed.paymentMethod)
        assertEquals("A/c **4321", parsed.accountOrCardMasked)
        assertEquals("HDFC Bank", parsed.bankName)
        assertEquals("429712345678", parsed.referenceNumber)
        assertEquals("Food and Groceries", parsed.suggestedCategory)
        assertEquals("Swiggy", parsed.merchantOrSender)
    }

    @Test
    fun parse_sbiSalaryCredit_extractsIncome() {
        val sms = "Your A/c ending 9876 has been credited by Rs. 75,000.00 on 01-OCT-26 by Salary transfer from TECH CORP PVT LTD. Ref: SAL20261001. Avl Bal: INR 95,400 - SBI"
        val parsed = SmsTransactionParser.parse("AD-SBIINB", sms)

        assertNotNull(parsed)
        assertEquals(75000.0, parsed!!.amount, 0.001)
        assertEquals(TransactionType.INCOME, parsed.type)
        assertEquals("Salary", parsed.suggestedCategory)
        assertEquals("SBI", parsed.bankName)
        assertEquals("SAL20261001", parsed.referenceNumber)
    }

    @Test
    fun parse_uberRideDebitCard_extractsTransportCategory() {
        val sms = "Rs 285 spent on your ICICI Bank Debit Card ending with 1122 at UBER INDIA on 22-OCT-26. UTR 98234710."
        val parsed = SmsTransactionParser.parse("ICICIB", sms)

        assertNotNull(parsed)
        assertEquals(285.0, parsed!!.amount, 0.001)
        assertEquals(TransactionType.EXPENSE, parsed.type)
        assertEquals(PaymentMethod.DEBIT_CARD, parsed.paymentMethod)
        assertEquals("Transportation and Fuel", parsed.suggestedCategory)
        assertEquals("ICICI Bank", parsed.bankName)
    }

    @Test
    fun parse_orderRefund_extractsRefundType() {
        val sms = "Refund of Rs. 1,299.00 has been credited to your HDFC Bank A/c **4321 towards cancelled order from Amazon India. Ref no 58392019."
        val parsed = SmsTransactionParser.parse("HDFCBK", sms)

        assertNotNull(parsed)
        assertEquals(1299.0, parsed!!.amount, 0.001)
        assertEquals(TransactionType.REFUND, parsed.type)
        assertEquals("58392019", parsed.referenceNumber)
    }

    @Test
    fun parse_transferBetweenOwnAccounts_extractsTransferType() {
        val sms = "Rs 15,000.00 transferred to your own linked A/c **8899 on 20-OCT-26 from Axis Bank A/c **1234. Ref 77889900."
        val parsed = SmsTransactionParser.parse("AXISBK", sms)

        assertNotNull(parsed)
        assertEquals(15000.0, parsed!!.amount, 0.001)
        assertEquals(TransactionType.TRANSFER, parsed.type)
        assertTrue(parsed.isTransferBetweenOwnAccounts)
    }

    @Test
    fun parse_creditCardBillPayment_marksAsCcBill() {
        val sms = "Payment received of Rs 12,450.00 for your HDFC Bank Credit Card ending 5544. Thank you for your payment."
        val parsed = SmsTransactionParser.parse("HDFCBK", sms)

        assertNotNull(parsed)
        assertEquals(12450.0, parsed!!.amount, 0.001)
        assertTrue(parsed.isCreditCardBillPayment)
        assertEquals("EMI and Loan Payments", parsed.suggestedCategory)
    }

    @Test
    fun parse_otpMessage_isIgnored() {
        val sms = "492103 is your secret OTP for transaction of Rs. 2,450.00 on your HDFC Bank Card ending 1234. Valid for 10 minutes. Do not share with anyone."
        val parsed = SmsTransactionParser.parse("HDFCBK", sms)

        assertNull("OTP message must be discarded", parsed)
    }

    @Test
    fun parse_promotionalOffer_isIgnored() {
        val sms = "Congratulations! You are pre-approved for a personal loan of Rs 5,00,000 at flat 10.5% interest. Apply now at hdfc.bank/loan or use code FESTIVE50."
        val parsed = SmsTransactionParser.parse("HDFCBK", sms)

        assertNull("Promotional message must be discarded", parsed)
    }

    @Test
    fun parse_balanceOnlyAlert_isIgnored() {
        val sms = "Dear Customer, the available balance in your SBI A/c **1234 is Rs. 14,250.75 as of 23-OCT-2026."
        val parsed = SmsTransactionParser.parse("SBIINB", sms)

        assertNull("Balance-only alert without transaction action must be discarded", parsed)
    }
}

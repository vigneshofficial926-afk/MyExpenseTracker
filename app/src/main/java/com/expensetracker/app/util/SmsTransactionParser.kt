package com.expensetracker.app.util

import com.expensetracker.app.data.model.PaymentMethod
import com.expensetracker.app.data.model.TransactionType
import java.util.regex.Pattern

data class ParsedSmsTransaction(
    val amount: Double,
    val type: TransactionType,
    val merchantOrSender: String,
    val accountOrCardMasked: String?,
    val bankName: String?,
    val paymentMethod: PaymentMethod,
    val referenceNumber: String?,
    val suggestedCategory: String,
    val isCreditCardBillPayment: Boolean = false,
    val isTransferBetweenOwnAccounts: Boolean = false,
    val dateTime: Long = System.currentTimeMillis(),
    val rawSnippet: String = ""
)

object SmsTransactionParser {

    // Regex to match OTP, authentication codes, or promotional spam that must be ignored
    private val OTP_PATTERN = Pattern.compile(
        "(?i)\\b(otp|one[ -]time[ -]password|verification[ -]code|security[ -]code|secret[ -]code|do not share|valid for|is your code|login code)\\b"
    )

    private val PROMO_PATTERN = Pattern.compile(
        "(?i)\\b(pre-approved|apply now|cashback offer|cashback points|discount coupon|flat discount|congratulations|congrats|win up to|loan up to|voucher code|use code|avail now)\\b"
    )

    private val BALANCE_ONLY_PATTERN = Pattern.compile(
        "(?i)^[^\\n]*(available bal|avl bal|clear balance|total balance|a/c balance)[^\\n]*$"
    )

    // Keywords that indicate financial debit/credit movements
    private val TRANSACTION_ACTION_PATTERN = Pattern.compile(
        "(?i)\\b(debited|spent|paid|withdrawn|transferred|sent|charged|credited|received|deposited|refunded|reversed)\\b"
    )

    // Amount patterns
    // e.g. "Rs. 1,450.50", "INR 500", "Rs 250", "₹1200", "credited with Rs.500.00", "paid 450.00 INR"
    private val AMOUNT_PATTERN_PREFIX = Pattern.compile(
        "(?i)(?:rs\\.?|inr|₹|inr\\.)\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)"
    )
    private val AMOUNT_PATTERN_SUFFIX = Pattern.compile(
        "(?i)([0-9]+(?:,[0-9]+)*(?:\\.[0-9]{1,2})?)\\s*(?:rs\\.?|inr|₹)"
    )

    // Account / Card mask pattern: e.g., "A/c **1234", "Card ending 5678", "Account XX9876", "A/c no. ...1234"
    private val ACCOUNT_CARD_PATTERN = Pattern.compile(
        "(?i)(?:a/c|acct|account|card|ending with|ending)\\s*(?:no\\.?)?\\s*[*xX.-]*([0-9]{3,4})"
    )

    // Reference number pattern: UTR, Ref no, UPI Ref, Txn ID, IMPS Ref
    private val REF_PATTERN = Pattern.compile(
        "(?i)(?:upi ref|ref no|ref|utr|txn id|txn#|txn|imps ref|reference)[:\\s-]*([a-zA-Z0-9]{6,22})"
    )

    // Merchant / Beneficiary patterns
    private val BENEFICIARY_TO_VPA = Pattern.compile(
        "(?i)\\b(?:to\\s+(?:vpa\\s+)?|vpa\\s+)([a-zA-Z0-9._-]+@[a-zA-Z0-9]+)"
    )
    private val BENEFICIARY_TO_NAME = Pattern.compile(
        "(?i)\\b(?:to|paid to|sent to|at|transferred to|info:\\s*upi/p2[ap]/)\\s*([a-zA-Z0-9&.,' -]{2,30}?)(?:\\s+on|\\s+dated|\\s+ref|\\s+utr|\\s+avl|\\s+bal|\\.|,|$)"
    )
    private val SENDER_FROM_NAME = Pattern.compile(
        "(?i)\\b(?:credited\\s+by|received\\s+from|from|transferred\\s+from)\\s+([a-zA-Z0-9&.,' -]{2,30}?)(?:\\s+on|\\s+dated|\\s+ref|\\s+avl|\\s+bal|\\.|,|$)"
    )

    /**
     * Parses an SMS message.
     * Returns a ParsedSmsTransaction if it is a genuine financial transaction SMS, or null if it
     * is an OTP, promo, balance enquiry, or unsupported message.
     */
    fun parse(sender: String?, body: String, timestamp: Long = System.currentTimeMillis()): ParsedSmsTransaction? {
        val cleanBody = body.trim()
        if (cleanBody.length < 15) return null

        // 1. Filter out OTPs and Security codes
        if (OTP_PATTERN.matcher(cleanBody).find()) {
            return null
        }

        // 2. Filter out promotions and loan offers
        if (PROMO_PATTERN.matcher(cleanBody).find()) {
            return null
        }

        // 3. Must have a transaction action verb (debited, credited, etc.)
        if (!TRANSACTION_ACTION_PATTERN.matcher(cleanBody).find()) {
            return null
        }

        // 4. Filter out balance-only alerts without explicit debit or credit
        val lower = cleanBody.lowercase()
        val hasDebit = lower.contains("debited") || lower.contains("spent") || lower.contains("paid") ||
                lower.contains("withdrawn") || lower.contains("charged") || lower.contains("sent")
        val hasCredit = lower.contains("credited") || lower.contains("received") || lower.contains("deposited")
        val hasRefund = lower.contains("refund") || lower.contains("reversed") || lower.contains("reversal")

        if (!hasDebit && !hasCredit && !hasRefund && BALANCE_ONLY_PATTERN.matcher(cleanBody).matches()) {
            return null
        }

        // 5. Extract Amount
        val amount = extractAmount(cleanBody) ?: return null
        if (amount <= 0.0) return null

        // 6. Detect Bank name from sender ID or message content
        val bankName = detectBank(sender, cleanBody)

        // 7. Detect Account / Card Mask
        val accountOrCard = extractAccountOrCard(cleanBody)

        // 8. Detect Reference Number (UTR / Txn ID)
        val referenceNumber = extractReference(cleanBody)

        // 9. Detect Transaction Type (Credit, Debit, Refund, Transfer)
        val isTransferOwn = detectTransferOwnAccounts(lower)
        val isCcBill = detectCreditCardBillPayment(lower)

        val type: TransactionType = when {
            isTransferOwn -> TransactionType.TRANSFER
            hasRefund -> TransactionType.REFUND
            hasCredit && !hasDebit -> TransactionType.INCOME
            hasDebit -> TransactionType.EXPENSE
            else -> TransactionType.EXPENSE
        }

        // 10. Extract Payment Method
        val paymentMethod = detectPaymentMethod(lower)

        // 11. Extract Merchant / Sender / Beneficiary Name
        val merchantOrSender = extractMerchantOrBeneficiary(cleanBody, type, bankName)

        // 12. Smart Category Suggestion
        val category = suggestCategory(lower, merchantOrSender, type, isCcBill, isTransferOwn)

        return ParsedSmsTransaction(
            amount = amount,
            type = type,
            merchantOrSender = merchantOrSender,
            accountOrCardMasked = accountOrCard,
            bankName = bankName,
            paymentMethod = paymentMethod,
            referenceNumber = referenceNumber,
            suggestedCategory = category,
            isCreditCardBillPayment = isCcBill,
            isTransferBetweenOwnAccounts = isTransferOwn,
            dateTime = timestamp,
            rawSnippet = cleanBody.take(160)
        )
    }

    private fun extractAmount(text: String): Double? {
        val m1 = AMOUNT_PATTERN_PREFIX.matcher(text)
        if (m1.find()) {
            val str = m1.group(1)?.replace(",", "")
            val parsed = str?.toDoubleOrNull()
            if (parsed != null) return parsed
        }

        val m2 = AMOUNT_PATTERN_SUFFIX.matcher(text)
        if (m2.find()) {
            val str = m2.group(1)?.replace(",", "")
            val parsed = str?.toDoubleOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun extractAccountOrCard(text: String): String? {
        val m = ACCOUNT_CARD_PATTERN.matcher(text)
        if (m.find()) {
            val digits = m.group(1)
            val lower = text.lowercase()
            return if (lower.contains("card") && !lower.contains("a/c")) "Card **$digits" else "A/c **$digits"
        }
        return null
    }

    private fun extractReference(text: String): String? {
        val m = REF_PATTERN.matcher(text)
        if (m.find()) {
            return m.group(1)?.trim()
        }
        return null
    }

    private fun detectBank(sender: String?, text: String): String? {
        val s = (sender ?: "").uppercase()
        val bodyUpper = text.uppercase()

        return when {
            s.contains("HDFC") || bodyUpper.contains("HDFC BANK") -> "HDFC Bank"
            s.contains("SBI") || bodyUpper.contains("STATE BANK OF INDIA") || bodyUpper.contains("SBI") -> "SBI"
            s.contains("ICICI") || bodyUpper.contains("ICICI BANK") -> "ICICI Bank"
            s.contains("AXIS") || bodyUpper.contains("AXIS BANK") -> "Axis Bank"
            s.contains("KOTAK") || bodyUpper.contains("KOTAK BANK") -> "Kotak Mahindra Bank"
            s.contains("PAYTM") || bodyUpper.contains("PAYTM PAYMENTS BANK") -> "Paytm Bank"
            s.contains("PNB") || s.contains("PUNB") || bodyUpper.contains("PUNJAB NATIONAL BANK") -> "Punjab National Bank"
            s.contains("BOB") || bodyUpper.contains("BANK OF BARODA") -> "Bank of Baroda"
            s.contains("CANARA") || bodyUpper.contains("CANARA BANK") -> "Canara Bank"
            s.contains("INDUS") || bodyUpper.contains("INDUSIND") -> "IndusInd Bank"
            s.contains("YES") || bodyUpper.contains("YES BANK") -> "Yes Bank"
            s.contains("UNION") || bodyUpper.contains("UNION BANK") -> "Union Bank"
            s.contains("IDFC") || bodyUpper.contains("IDFC FIRST") -> "IDFC FIRST Bank"
            s.contains("CRED") -> "CRED"
            else -> null
        }
    }

    private fun detectTransferOwnAccounts(lower: String): Boolean {
        return (lower.contains("transferred to your own") ||
                lower.contains("transfer to own a/c") ||
                lower.contains("transfer to self") ||
                lower.contains("self account transfer") ||
                lower.contains("internal transfer"))
    }

    private fun detectCreditCardBillPayment(lower: String): Boolean {
        return (lower.contains("credit card bill payment") ||
                lower.contains("payment received for credit card") ||
                lower.contains("credit card payment received") ||
                (lower.contains("credit card") && lower.contains("bill") && lower.contains("paid")))
    }

    private fun detectPaymentMethod(lower: String): PaymentMethod {
        return when {
            lower.contains("upi") || lower.contains("@") || lower.contains("vpa") ||
                    lower.contains("googlepay") || lower.contains("phonepe") || lower.contains("paytm") -> PaymentMethod.UPI
            lower.contains("credit card") || lower.contains("cc ending") || lower.contains("card ending") && lower.contains("spent") -> PaymentMethod.CREDIT_CARD
            lower.contains("debit card") || lower.contains("dc ending") -> PaymentMethod.DEBIT_CARD
            lower.contains("neft") || lower.contains("rtgs") || lower.contains("imps") || lower.contains("net banking") || lower.contains("bank transfer") -> PaymentMethod.BANK_TRANSFER
            else -> PaymentMethod.UPI
        }
    }

    private fun extractMerchantOrBeneficiary(text: String, type: TransactionType, bankName: String?): String {
        // Check VPA first: e.g. to VPA swiggy@icici
        val vpaMatch = BENEFICIARY_TO_VPA.matcher(text)
        if (vpaMatch.find()) {
            val vpa = vpaMatch.group(1)?.trim()
            if (!vpa.isNullOrBlank()) {
                val namePart = vpa.substringBefore("@").replace(".", " ").replace("-", " ")
                return formatMerchantName(namePart)
            }
        }

        if (type == TransactionType.EXPENSE || type == TransactionType.TRANSFER) {
            val m = BENEFICIARY_TO_NAME.matcher(text)
            if (m.find()) {
                val candidate = cleanCandidate(m.group(1))
                if (candidate.isNotBlank() && !isIgnoredMerchantWord(candidate)) {
                    return formatMerchantName(candidate)
                }
            }
        } else {
            val m = SENDER_FROM_NAME.matcher(text)
            if (m.find()) {
                val candidate = cleanCandidate(m.group(1))
                if (candidate.isNotBlank() && !isIgnoredMerchantWord(candidate)) {
                    return formatMerchantName(candidate)
                }
            }
        }

        return when (type) {
            TransactionType.INCOME -> if (bankName != null) "Credit via $bankName" else "Incoming Transfer"
            TransactionType.REFUND -> "Merchant Refund"
            TransactionType.TRANSFER -> "Own Account Transfer"
            TransactionType.EXPENSE -> if (bankName != null) "Payment via $bankName" else "Bank Expense"
        }
    }

    private fun cleanCandidate(raw: String?): String {
        if (raw == null) return ""
        return raw
            .replace("(?i)^vpa\\s+".toRegex(), "")
            .replace("(?i)^upi/".toRegex(), "")
            .replace("[/\\*#_]".toRegex(), " ")
            .trim()
            .trimEnd('.', ',', '-', ' ')
    }

    private fun isIgnoredMerchantWord(candidate: String): Boolean {
        val lower = candidate.lowercase().trim()
        return lower in setOf(
            "your a/c", "your account", "bank", "account", "vpa", "upi", "inr", "rs",
            "the", "on", "dated", "ref", "available", "avl bal", "balance", "card"
        )
    }

    private fun formatMerchantName(name: String): String {
        val trimmed = name.trim()
        if (trimmed.length > 28) return trimmed.take(28)
        // Capitalize each word
        return trimmed.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
    }

    private fun suggestCategory(
        lower: String,
        merchant: String,
        type: TransactionType,
        isCcBill: Boolean,
        isTransferOwn: Boolean
    ): String {
        if (isCcBill) return "EMI and Loan Payments"
        if (isTransferOwn) return "Other Expenses"

        if (type == TransactionType.INCOME || type == TransactionType.REFUND) {
            return when {
                lower.contains("salary") || lower.contains("payroll") || lower.contains("stipend") -> "Salary"
                lower.contains("interest") || lower.contains("dividend") -> "Interest"
                lower.contains("freelance") || lower.contains("upwork") || lower.contains("fiverr") -> "Freelance Income"
                lower.contains("business") || lower.contains("client") -> "Business Income"
                else -> "Other Income"
            }
        }

        val combined = "$lower ${merchant.lowercase()}"

        return when {
            // Food and Groceries
            combined.contains("swiggy") || combined.contains("zomato") || combined.contains("blinkit") ||
                    combined.contains("zepto") || combined.contains("bigbasket") || combined.contains("grocer") ||
                    combined.contains("supermarket") || combined.contains("dmart") || combined.contains("spencer") ||
                    combined.contains("more retail") || combined.contains("instamart") -> "Food and Groceries"

            // Hotel & Restaurants
            combined.contains("kfc") || combined.contains("mcdonald") || combined.contains("starbucks") ||
                    combined.contains("dominos") || combined.contains("pizza") || combined.contains("burger") ||
                    combined.contains("restaurant") || combined.contains("hotel") || combined.contains("cafe") ||
                    combined.contains("dineout") || combined.contains("barbeque") -> "Hotel and Restaurants"

            // Tea & Snacks
            combined.contains("chai") || combined.contains("tea") || combined.contains("coffee") ||
                    combined.contains("bakery") || combined.contains("samosa") || combined.contains("snacks") -> "Tea, Coffee and Snacks"

            // Transportation and Fuel
            combined.contains("uber") || combined.contains("ola") || combined.contains("rapido") ||
                    combined.contains("petrol") || combined.contains("fuel") || combined.contains("diesel") ||
                    combined.contains("indian oil") || combined.contains("hpcl") || combined.contains("bpcl") ||
                    combined.contains("shell") || combined.contains("metro") || combined.contains("irctc") ||
                    combined.contains("fastag") || combined.contains("toll") -> "Transportation and Fuel"

            // Rent
            combined.contains("rent") || combined.contains("landlord") || combined.contains("society maintenance") -> "Rent"

            // Electricity and Utilities
            combined.contains("electricity") || combined.contains("bescom") || combined.contains("tneb") ||
                    combined.contains("gas bill") || combined.contains("water bill") || combined.contains("indane") ||
                    combined.contains("hp gas") || combined.contains("bharat gas") -> "Electricity and Utilities"

            // Mobile and Internet
            combined.contains("airtel") || combined.contains("jio") || combined.contains("vodafone") ||
                    combined.contains("vi prepaid") || combined.contains("broadband") || combined.contains("act fibernet") ||
                    combined.contains("tata play") || combined.contains("dth") || combined.contains("recharge") -> "Mobile and Internet"

            // Shopping
            combined.contains("amazon") || combined.contains("flipkart") || combined.contains("myntra") ||
                    combined.contains("ajio") || combined.contains("meesho") || combined.contains("zara") ||
                    combined.contains("h&m") || combined.contains("retail") || combined.contains("croma") ||
                    combined.contains("reliance digital") || combined.contains("mall") || combined.contains("decathlon") -> "Shopping"

            // Health and Medical
            combined.contains("apollo") || combined.contains("pharmacy") || combined.contains("chemist") ||
                    combined.contains("hospital") || combined.contains("medplus") || combined.contains("1mg") ||
                    combined.contains("pharmeasy") || combined.contains("clinic") || combined.contains("doctor") ||
                    combined.contains("diagnostic") || combined.contains("lab") -> "Health and Medical"

            // Entertainment
            combined.contains("netflix") || combined.contains("spotify") || combined.contains("prime video") ||
                    combined.contains("hotstar") || combined.contains("bookmyshow") || combined.contains("pvr") ||
                    combined.contains("inox") || combined.contains("cinema") || combined.contains("movie") ||
                    combined.contains("youtube") -> "Entertainment"

            // Travel
            combined.contains("flight") || combined.contains("airline") || combined.contains("indigo") ||
                    combined.contains("air india") || combined.contains("makemytrip") || combined.contains("goibibo") ||
                    combined.contains("yatra") || combined.contains("cleartrip") || combined.contains("airbnb") -> "Travel"

            // Education
            combined.contains("school") || combined.contains("college") || combined.contains("university") ||
                    combined.contains("udemy") || combined.contains("coursera") || combined.contains("tuition") ||
                    combined.contains("fees") -> "Education"

            // Subscriptions
            combined.contains("subscription") || combined.contains("membership") -> "Subscriptions"

            // EMI and Loan
            combined.contains("emi") || combined.contains("loan") || combined.contains("bajaj finance") -> "EMI and Loan Payments"

            else -> "Other Expenses"
        }
    }
}

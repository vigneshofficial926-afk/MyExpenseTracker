package com.expensetracker.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.domain.FinancialCalculator
import com.expensetracker.app.util.CurrencyFormatter
import com.expensetracker.app.util.NotificationHelper
import com.expensetracker.app.util.ParsedSmsTransaction
import com.expensetracker.app.util.SmsTransactionParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SmsBroadcastReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // 1. Verify that the user has explicitly consented and enabled SMS tracking
        val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("sms_tracking_enabled", false)
        if (!isEnabled) {
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group multi-part messages by sender
        val sender = messages[0].displayOriginatingAddress ?: ""
        val bodyBuilder = StringBuilder()
        var timestamp = messages[0].timestampMillis
        for (msg in messages) {
            bodyBuilder.append(msg.displayMessageBody)
            if (msg.timestampMillis > 0) timestamp = msg.timestampMillis
        }
        val fullBody = bodyBuilder.toString()

        val parsed = SmsTransactionParser.parse(sender, fullBody, timestamp) ?: return

        // Process and save in Room database safely
        val pendingResult = goAsync()
        scope.launch {
            try {
                saveTransactionIfUnique(context, parsed)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun saveTransactionIfUnique(context: Context, parsed: ParsedSmsTransaction) {
        val database = ExpenseDatabase.getInstance(context)
        val transactionDao = database.transactionDao()
        val categoryDao = database.categoryDao()
        val accountDao = database.accountDao()

        // 1. Duplicate check via Reference Number / UTR
        val existingRecent = transactionDao.getAllTransactionsList().take(50)
        if (!parsed.referenceNumber.isNullOrBlank()) {
            val isRefDuplicate = existingRecent.any { tx ->
                tx.tags.contains(parsed.referenceNumber, ignoreCase = true) ||
                        tx.description.contains(parsed.referenceNumber, ignoreCase = true)
            }
            if (isRefDuplicate) return
        }

        // 2. Resolve Category Entity
        val allCategories = categoryDao.getAllCategoriesList()
        val matchingCategory = allCategories.find {
            it.name.equals(parsed.suggestedCategory, ignoreCase = true) && it.type == parsed.type
        } ?: allCategories.find {
            it.type == parsed.type
        } ?: return

        // 3. Resolve Account Entity
        val allAccounts = accountDao.getAllAccountsList()
        val matchingAccount = if (parsed.bankName != null) {
            allAccounts.find { it.name.contains(parsed.bankName, ignoreCase = true) }
        } else null ?: allAccounts.firstOrNull()

        // 4. Build Transaction Description and Tags
        val description = buildString {
            append(parsed.merchantOrSender)
            if (!parsed.accountOrCardMasked.isNullOrBlank()) {
                append(" (${parsed.accountOrCardMasked})")
            }
        }

        val tagsList = mutableListOf("sms-import")
        if (!parsed.referenceNumber.isNullOrBlank()) {
            tagsList.add("ref:${parsed.referenceNumber}")
        }
        if (!parsed.bankName.isNullOrBlank()) {
            tagsList.add("bank:${parsed.bankName}")
        }

        val candidate = TransactionEntity(
            amount = parsed.amount,
            type = parsed.type,
            categoryId = matchingCategory.id,
            categoryName = matchingCategory.name,
            dateTime = parsed.dateTime,
            paymentMethod = parsed.paymentMethod,
            description = description,
            tags = tagsList.joinToString(", "),
            sourceAccountId = matchingAccount?.id,
            destinationAccountId = null,
            isCreditCardBillPayment = parsed.isCreditCardBillPayment,
            isExcludedFromBudget = parsed.isCreditCardBillPayment || parsed.type == TransactionType.TRANSFER
        )

        // 5. Duplicate Check via amount + time window (5 minutes)
        val isDuplicate = FinancialCalculator.isDuplicateTransaction(
            candidate = candidate,
            existingList = existingRecent,
            timeWindowMillis = 300_000L
        )
        if (isDuplicate) return

        // 6. Save to Room database
        transactionDao.insertTransaction(candidate)

        // 7. Show Notification to inform user of recorded transaction
        val typePrefix = when (parsed.type) {
            TransactionType.INCOME -> "Income Received"
            TransactionType.EXPENSE -> "Expense Logged"
            TransactionType.REFUND -> "Refund Credited"
            TransactionType.TRANSFER -> "Account Transfer"
        }
        val formattedAmount = CurrencyFormatter.format(parsed.amount)
        NotificationHelper.showTransactionImportedNotification(
            context = context,
            title = "Auto-Tracked $typePrefix: $formattedAmount",
            message = "$description automatically recorded in your personal finance tracker."
        )
    }
}

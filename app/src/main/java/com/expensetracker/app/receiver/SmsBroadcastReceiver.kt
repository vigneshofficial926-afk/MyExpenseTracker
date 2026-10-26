package com.expensetracker.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.util.CurrencyFormatter
import com.expensetracker.app.util.NotificationHelper
import com.expensetracker.app.util.ParsedSmsTransaction
import com.expensetracker.app.util.SmsImportHelper
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
        val db = ExpenseDatabase.getInstance(context)
        val dao = db.transactionDao()
        val saved = SmsImportHelper.saveIfUnique(
            dao, db.categoryDao().getAllCategoriesList(), db.accountDao().getAllAccountsList(),
            parsed, dao.getRecentTransactionsList(100).toMutableList()
        ) ?: return
        val prefix = when (parsed.type) {
            TransactionType.INCOME -> "Income Received"; TransactionType.EXPENSE -> "Expense Logged"
            TransactionType.REFUND -> "Refund Credited"; TransactionType.TRANSFER -> "Account Transfer"
        }
        NotificationHelper.showTransactionImportedNotification(
            context, "Auto-Tracked $prefix: ${CurrencyFormatter.format(parsed.amount)}",
            "${saved.description} automatically recorded in your personal finance tracker."
        )
    }
}

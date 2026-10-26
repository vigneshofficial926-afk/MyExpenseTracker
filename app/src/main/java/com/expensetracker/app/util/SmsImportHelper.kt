package com.expensetracker.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.local.dao.TransactionDao
import com.expensetracker.app.data.local.entity.AccountEntity
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.TransactionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

object SmsImportHelper {

    private fun List<CategoryEntity>.resolveFor(p: ParsedSmsTransaction): CategoryEntity? {
        val g = p.type.categoryGroup
        return firstOrNull { it.type == g && it.name.equals(p.suggestedCategory, true) }
            ?: firstOrNull { it.type == g && it.name.startsWith("Other", true) }
            ?: firstOrNull { it.type == g }
    }

    /** Saves unless duplicate. Updates [existing] so batch scans de-duplicate themselves. */
    suspend fun saveIfUnique(
        dao: TransactionDao,
        categories: List<CategoryEntity>,
        accounts: List<AccountEntity>,
        p: ParsedSmsTransaction,
        existing: MutableList<TransactionEntity>
    ): TransactionEntity? {
        val ref = p.referenceNumber?.takeIf { it.isNotBlank() }
        val duplicate = if (ref != null)
            existing.any { it.tags.contains(ref, true) || it.description.contains(ref, true) }
        else
            existing.any { it.amount == p.amount && it.type == p.type && abs(it.dateTime - p.dateTime) <= 300_000L }
        if (duplicate) return null

        val category = categories.resolveFor(p) ?: return null
        val account = p.bankName?.let { b -> accounts.firstOrNull { it.name.contains(b, true) } }
            ?: accounts.firstOrNull()
        val description = buildString {
            append(p.merchantOrSender)
            p.accountOrCardMasked?.takeIf { it.isNotBlank() }?.let { append(" ($it)") }
        }
        val tags = buildList {
            add("sms-import"); ref?.let { add("ref:$it") }; p.bankName?.let { add("bank:$it") }
        }.joinToString(", ")

        val entity = TransactionEntity(
            amount = p.amount, type = p.type, categoryId = category.id, categoryName = category.name,
            dateTime = p.dateTime, paymentMethod = p.paymentMethod, description = description, tags = tags,
            sourceAccountId = account?.id, isCreditCardBillPayment = p.isCreditCardBillPayment,
            isExcludedFromBudget = p.isCreditCardBillPayment || p.type == TransactionType.TRANSFER
        )
        val saved = entity.copy(id = dao.insertTransaction(entity))
        existing.add(0, saved)
        return saved
    }

    suspend fun importFromSmsInbox(context: Context, lookbackDays: Int = 30): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED)
                    error("READ_SMS permission not granted.")
                val db = ExpenseDatabase.getInstance(context)
                val dao = db.transactionDao()
                val categories = db.categoryDao().getAllCategoriesList()
                val accounts = db.accountDao().getAllAccountsList()
                val existing = dao.getAllTransactionsList().toMutableList()
                val cutoff = System.currentTimeMillis() - lookbackDays * 86_400_000L
                var imported = 0
                context.contentResolver.query(
                    Uri.parse("content://sms/inbox"), arrayOf("address", "body", "date"),
                    "date >= ?", arrayOf(cutoff.toString()), "date ASC"
                )?.use { c ->
                    val a = c.getColumnIndexOrThrow("address"); val b = c.getColumnIndexOrThrow("body"); val d = c.getColumnIndexOrThrow("date")
                    while (c.moveToNext()) {
                        val parsed = SmsTransactionParser.parse(c.getString(a), c.getString(b) ?: "", c.getLong(d)) ?: continue
                        if (saveIfUnique(dao, categories, accounts, parsed, existing) != null) imported++
                    }
                } ?: error("Cannot access SMS inbox.")
                imported
            }
        }

    suspend fun importSingleSmsText(context: Context, rawSmsText: String, sender: String? = null): Result<TransactionEntity> =
        withContext(Dispatchers.IO) {
            val parsed = SmsTransactionParser.parse(sender, rawSmsText)
                ?: return@withContext Result.failure(Exception("Could not extract a valid financial transaction. Make sure this is a bank/UPI debit or credit message, not an OTP or promo."))
            val db = ExpenseDatabase.getInstance(context)
            val saved = saveIfUnique(
                db.transactionDao(), db.categoryDao().getAllCategoriesList(), db.accountDao().getAllAccountsList(),
                parsed, db.transactionDao().getAllTransactionsList().toMutableList()
            )
            if (saved != null) Result.success(saved)
            else Result.failure(Exception("Already recorded (same reference/amount/time) or no matching category."))
        }
}

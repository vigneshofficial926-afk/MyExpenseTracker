package com.expensetracker.app.util

import android.content.Context
import android.net.Uri
import com.expensetracker.app.data.local.ExpenseDatabase
import com.expensetracker.app.data.local.entity.TransactionEntity
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.domain.FinancialCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsImportHelper {

    suspend fun importFromSmsInbox(
        context: Context,
        lookbackDays: Int = 30
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val database = ExpenseDatabase.getInstance(context)
            val transactionDao = database.transactionDao()
            val categoryDao = database.categoryDao()
            val accountDao = database.accountDao()

            val allCategories = categoryDao.getAllCategoriesSync()
            val allAccounts = accountDao.getAllAccountsSync()
            val existingRecent = transactionDao.getAllTransactionsList()

            val cutoffMillis = System.currentTimeMillis() - (lookbackDays * 86_400_000L)
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("address", "body", "date")
            val selection = "date >= ?"
            val selectionArgs = arrayOf(cutoffMillis.toString())
            val sortOrder = "date DESC"

            val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
                ?: return@withContext Result.failure(Exception("Cannot access SMS inbox. Please check SMS permissions."))

            var importedCount = 0

            cursor.use { c ->
                val addressIdx = c.getColumnIndexOrThrow("address")
                val bodyIdx = c.getColumnIndexOrThrow("body")
                val dateIdx = c.getColumnIndexOrThrow("date")

                while (c.moveToNext()) {
                    val address = c.getString(addressIdx) ?: ""
                    val body = c.getString(bodyIdx) ?: ""
                    val date = c.getLong(dateIdx)

                    val parsed = SmsTransactionParser.parse(address, body, date) ?: continue

                    // Duplicate check via reference number
                    if (!parsed.referenceNumber.isNullOrBlank()) {
                        val isRefDuplicate = existingRecent.any { tx ->
                            tx.tags.contains(parsed.referenceNumber, ignoreCase = true) ||
                                    tx.description.contains(parsed.referenceNumber, ignoreCase = true)
                        }
                        if (isRefDuplicate) continue
                    }

                    // Matching category
                    val matchingCategory = allCategories.find {
                        it.name.equals(parsed.suggestedCategory, ignoreCase = true) && it.type == parsed.type
                    } ?: allCategories.find { it.type == parsed.type } ?: continue

                    // Matching account
                    val matchingAccount = if (parsed.bankName != null) {
                        allAccounts.find { it.name.contains(parsed.bankName, ignoreCase = true) }
                    } else null ?: allAccounts.firstOrNull()

                    val description = buildString {
                        append(parsed.merchantOrSender)
                        if (!parsed.accountOrCardMasked.isNullOrBlank()) {
                            append(" (${parsed.accountOrCardMasked})")
                        }
                    }

                    val tagsList = mutableListOf("sms-import")
                    if (!parsed.referenceNumber.isNullOrBlank()) tagsList.add("ref:${parsed.referenceNumber}")
                    if (!parsed.bankName.isNullOrBlank()) tagsList.add("bank:${parsed.bankName}")

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

                    // Duplicate check via amount + time window
                    val isDuplicate = FinancialCalculator.isDuplicateTransaction(
                        candidate = candidate,
                        existingList = existingRecent,
                        timeWindowMillis = 300_000L
                    )
                    if (isDuplicate) continue

                    transactionDao.insertTransaction(candidate)
                    importedCount++
                }
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importSingleSmsText(
        context: Context,
        rawSmsText: String,
        sender: String? = null
    ): Result<TransactionEntity> = withContext(Dispatchers.IO) {
        val parsed = SmsTransactionParser.parse(sender, rawSmsText)
            ?: return@withContext Result.failure(Exception("Could not extract a valid financial transaction. Ensure this is a bank or UPI debit/credit message and not an OTP or promotional alert."))

        val database = ExpenseDatabase.getInstance(context)
        val transactionDao = database.transactionDao()
        val categoryDao = database.categoryDao()
        val accountDao = database.accountDao()

        val allCategories = categoryDao.getAllCategoriesSync()
        val allAccounts = accountDao.getAllAccountsSync()

        val matchingCategory = allCategories.find {
            it.name.equals(parsed.suggestedCategory, ignoreCase = true) && it.type == parsed.type
        } ?: allCategories.find { it.type == parsed.type }
        ?: return@withContext Result.failure(Exception("No matching category found."))

        val matchingAccount = if (parsed.bankName != null) {
            allAccounts.find { it.name.contains(parsed.bankName, ignoreCase = true) }
        } else null ?: allAccounts.firstOrNull()

        val description = buildString {
            append(parsed.merchantOrSender)
            if (!parsed.accountOrCardMasked.isNullOrBlank()) {
                append(" (${parsed.accountOrCardMasked})")
            }
        }

        val tagsList = mutableListOf("sms-import")
        if (!parsed.referenceNumber.isNullOrBlank()) tagsList.add("ref:${parsed.referenceNumber}")
        if (!parsed.bankName.isNullOrBlank()) tagsList.add("bank:${parsed.bankName}")

        val transaction = TransactionEntity(
            amount = parsed.amount,
            type = parsed.type,
            categoryId = matchingCategory.id,
            categoryName = matchingCategory.name,
            dateTime = parsed.dateTime,
            paymentMethod = parsed.paymentMethod,
            description = description,
            tags = tagsList.joinToString(", "),
            sourceAccountId = matchingAccount?.id,
            isCreditCardBillPayment = parsed.isCreditCardBillPayment,
            isExcludedFromBudget = parsed.isCreditCardBillPayment || parsed.type == TransactionType.TRANSFER
        )

        val id = transactionDao.insertTransaction(transaction)
        Result.success(transaction.copy(id = id))
    }
}

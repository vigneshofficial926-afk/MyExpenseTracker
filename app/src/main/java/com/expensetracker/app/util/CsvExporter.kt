package com.expensetracker.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.expensetracker.app.data.local.entity.TransactionEntity
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object CsvExporter {

    private val timeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

    fun generateCsvString(transactions: List<TransactionEntity>): String {
        val sb = StringBuilder()
        // Header
        sb.append("ID,Date,Time,Type,Category,Amount (INR),Payment Method,Description,Tags\n")

        for (tx in transactions) {
            val zone = ZoneId.systemDefault()
            val zonedDateTime = Instant.ofEpochMilli(tx.dateTime).atZone(zone)
            val dateStr = zonedDateTime.toLocalDate().format(DateUtils.DATE_FORMATTER_DD_MM_YYYY)
            val timeStr = zonedDateTime.toLocalTime().format(timeFormatter)

            val escapedDesc = tx.description.replace("\"", "\"\"")
            val escapedTags = tx.tags.replace("\"", "\"\"")

            sb.append("${tx.id},")
            sb.append("\"$dateStr\",")
            sb.append("\"$timeStr\",")
            sb.append("\"${tx.type.displayName}\",")
            sb.append("\"${tx.categoryName}\",")
            sb.append("${tx.amount},")
            sb.append("\"${tx.paymentMethod.displayName}\",")
            sb.append("\"$escapedDesc\",")
            sb.append("\"$escapedTags\"\n")
        }
        return sb.toString()
    }

    fun exportAndShareCsv(context: Context, transactions: List<TransactionEntity>, fileName: String = "transactions_export.csv") {
        val exportDir = File(context.cacheDir, "reports")
        if (!exportDir.exists()) {
            exportDir.mkdirs()
        }
        val file = File(exportDir, fileName)
        FileWriter(file).use { writer ->
            writer.write(generateCsvString(transactions))
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_SUBJECT, "My Expense Tracker - Export")
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(intent, "Share Transactions CSV")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}

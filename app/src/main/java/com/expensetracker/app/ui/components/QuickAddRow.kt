package com.expensetracker.app.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.expensetracker.app.data.model.PaymentMethod

data class QuickAddTemplate(
    val title: String,
    val amount: Double,
    val categoryName: String,
    val paymentMethod: PaymentMethod = PaymentMethod.UPI
)

val defaultQuickAddTemplates = listOf(
    QuickAddTemplate("Chai / Tea", 20.0, "Tea, Coffee and Snacks", PaymentMethod.UPI),
    QuickAddTemplate("Auto / Metro", 50.0, "Transportation and Fuel", PaymentMethod.UPI),
    QuickAddTemplate("Coffee", 40.0, "Tea, Coffee and Snacks", PaymentMethod.CASH),
    QuickAddTemplate("Snacks", 60.0, "Tea, Coffee and Snacks", PaymentMethod.UPI),
    QuickAddTemplate("Lunch", 150.0, "Hotel and Restaurants", PaymentMethod.UPI),
    QuickAddTemplate("Groceries", 350.0, "Food and Groceries", PaymentMethod.UPI),
    QuickAddTemplate("Petrol", 500.0, "Transportation and Fuel", PaymentMethod.UPI)
)

@Composable
fun QuickAddRow(
    onTemplateClick: (QuickAddTemplate) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        defaultQuickAddTemplates.forEach { template ->
            AssistChip(
                onClick = { onTemplateClick(template) },
                label = {
                    Text(
                        text = "${template.title} ₹${template.amount.toInt()}",
                        style = MaterialTheme.typography.labelSmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.padding(2.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    labelColor = MaterialTheme.colorScheme.onSurface
                ),
                border = null
            )
        }
    }
}

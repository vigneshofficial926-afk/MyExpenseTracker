package com.expensetracker.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import com.expensetracker.app.ui.theme.WarningAmber
import com.expensetracker.app.util.CurrencyFormatter

data class ChartSlice(
    val label: String,
    val value: Double,
    val color: Color
)

fun List<Pair<String, Double>>.toSlices(palette: List<Color>, maxSlices: Int = 5): List<ChartSlice> {
    val top = take(maxSlices)
    val rest = drop(maxSlices).sumOf { it.second }
    return (if (rest > 0) top + ("Others" to rest) else top)
        .mapIndexed { i, (n, v) -> ChartSlice(n, v, palette[i % palette.size]) }
}

@Composable
fun DonutPieChart(
    slices: List<ChartSlice>,
    totalAmount: Double,
    modifier: Modifier = Modifier
) {
    val total = if (totalAmount > 0) totalAmount else slices.sumOf { it.value }.coerceAtLeast(1.0)
    val animationProgress = remember { Animatable(0f) }

    LaunchedEffect(slices) {
        animationProgress.animateTo(1f, animationSpec = tween(durationMillis = 800))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(190.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(170.dp)) {
                var startAngle = -90f
                val strokeWidth = 32.dp.toPx()
                val arcTopLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

                if (slices.isEmpty() || totalAmount == 0.0) {
                    drawArc(
                        color = Color.LightGray.copy(alpha = 0.3f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = arcTopLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth)
                    )
                } else {
                    for (slice in slices) {
                        val sweepAngle = ((slice.value / total) * 360f * animationProgress.value).toFloat()
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = false,
                            topLeft = arcTopLeft,
                            size = arcSize,
                            style = Stroke(width = strokeWidth)
                        )
                        startAngle += sweepAngle
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = CurrencyFormatter.formatCompact(totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Legend list
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            slices.take(6).forEach { slice ->
                val pct = if (total > 0) (slice.value / total) * 100 else 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(slice.color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = slice.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${String.format("%.1f", pct)}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = CurrencyFormatter.format(slice.value),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyBarChart(
    days: List<String>, // e.g. ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
    values: List<Double>,
    highlightIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    val maxVal = (values.maxOrNull() ?: 1.0).coerceAtLeast(100.0)
    val barColor = MaterialTheme.colorScheme.primary
    val highlightColor = MaterialTheme.colorScheme.secondary

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            days.forEachIndexed { index, day ->
                val value = values.getOrElse(index) { 0.0 }
                val heightPct = (value / maxVal).coerceIn(0.05, 1.0).toFloat()
                val isToday = index == highlightIndex

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    if (value > 0) {
                        Text(
                            text = CurrencyFormatter.formatCompact(value),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Box(
                        modifier = Modifier
                            .width(20.dp)
                            .height((100 * heightPct).dp)
                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                            .background(if (isToday) highlightColor else barColor.copy(alpha = 0.85f))
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = day,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) highlightColor else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun IncomeExpenseComparison(
    income: Double,
    expense: Double,
    modifier: Modifier = Modifier
) {
    val max = Math.max(income, expense).coerceAtLeast(1.0)
    val incomePct = (income / max).toFloat()
    val expensePct = (expense / max).toFloat()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
    ) {
        // Income Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Income",
                modifier = Modifier.width(70.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(incomePct)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(IncomeGreen)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = CurrencyFormatter.format(income),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = IncomeGreen
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Expense Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Expense",
                modifier = Modifier.width(70.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(expensePct)
                        .height(20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(ExpenseRed)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = CurrencyFormatter.format(expense),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = ExpenseRed
            )
        }
    }
}

@Composable
fun BudgetProgressBar(
    spent: Double,
    budget: Double,
    modifier: Modifier = Modifier
) {
    val progress = if (budget > 0) (spent / budget).toFloat() else 0f
    val color = when {
        progress >= 1f -> ExpenseRed
        progress >= 0.8f -> WarningAmber
        else -> IncomeGreen
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Budget Utilization (${String.format("%.0f", progress * 100)}%)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${CurrencyFormatter.format(spent)} / ${CurrencyFormatter.format(budget)}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

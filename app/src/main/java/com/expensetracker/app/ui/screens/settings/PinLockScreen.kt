package com.expensetracker.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.ui.theme.ExpenseRed

@Composable
fun PinLockScreen(
    verifyPin: (String) -> Boolean,
    onPinSuccess: () -> Unit,
    onBiometricClick: (() -> Unit)? = null
) {
    var entered by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var failures by remember { mutableIntStateOf(0) }
    var blockedUntil by remember { mutableLongStateOf(0L) }
    val isError = message != null
    val accent = if (isError) ExpenseRed else MaterialTheme.colorScheme.primary

    fun press(key: String) {
        if (System.currentTimeMillis() < blockedUntil || entered.length >= 4) return
        message = null
        entered += key
        if (entered.length == 4) {
            if (verifyPin(entered)) onPinSuccess() else {
                failures++
                entered = ""
                message = if (failures % 5 == 0) {
                    blockedUntil = System.currentTimeMillis() + 30_000L
                    "Too many attempts. Try again in 30 seconds."
                } else "Incorrect PIN, please try again"
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
        ) {
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp)) }
            Spacer(Modifier.height(20.dp))
            Text("Enter Security PIN", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                message ?: "Unlock your expense tracker", style = MaterialTheme.typography.bodyMedium,
                color = if (isError) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(32.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    Box(
                        Modifier.size(18.dp).clip(CircleShape)
                            .background(if (i < entered.length) accent else Color.Transparent)
                            .border(2.dp, accent, CircleShape)
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del")).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { key ->
                            when (key) {
                                "bio" -> if (onBiometricClick != null)
                                    KeyCell(onClick = onBiometricClick) { Icon(Icons.Default.Fingerprint, "Use biometrics", tint = MaterialTheme.colorScheme.primary) }
                                else Spacer(Modifier.size(72.dp))
                                "del" -> KeyCell(onClick = { if (entered.isNotEmpty()) { entered = entered.dropLast(1); message = null } }) {
                                    Icon(Icons.Default.Backspace, "Delete", tint = MaterialTheme.colorScheme.onSurface)
                                }
                                else -> KeyCell(filled = true, onClick = { press(key) }) {
                                    Text(key, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyCell(filled: Boolean = false, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(72.dp).clip(CircleShape)
            .background(if (filled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { content() }
}

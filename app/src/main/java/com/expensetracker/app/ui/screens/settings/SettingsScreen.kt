package com.expensetracker.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.expensetracker.app.ui.components.ConfirmationDialog
import com.expensetracker.app.ui.theme.ExpenseRed
import com.expensetracker.app.ui.theme.IncomeGreen
import com.expensetracker.app.util.CurrencyFormatter
import com.expensetracker.app.util.SmsTransactionParser
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateToCategories: () -> Unit,
    onNavigateToRecurring: () -> Unit,
    onNavigateToGoals: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(uiState.operationMessage) {
        uiState.operationMessage?.let { snackbarHostState.showSnackbar(it); viewModel.consumeMessage() }
    }

    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var testSmsInput by remember { mutableStateOf("") }

    var pendingRestore by remember { mutableStateOf<Uri?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { pendingRestore = it }
    pendingRestore?.let { uri ->
        ConfirmationDialog(
            title = "Restore backup?", message = "This replaces ALL current data with the backup file.",
            confirmButtonText = "Restore", onConfirm = { viewModel.restoreBackup(uri); pendingRestore = null },
            onDismiss = { pendingRestore = null }
        )
    }

    // Launcher for SMS permissions with rationale
    val smsPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { p ->
        val recv = p[Manifest.permission.RECEIVE_SMS] == true
        val read = p[Manifest.permission.READ_SMS] == true
        viewModel.setSmsTrackingEnabled(recv)
        scope.launch {
            snackbarHostState.showSnackbar(
                when {
                    recv && read -> "Bank SMS auto-tracking enabled!"
                    recv -> "Live tracking on. Allow 'Read SMS' to scan old messages."
                    else -> "SMS permission not granted. Tracking disabled."
                }
            )
        }
    }

    // Privacy & Consent Dialog for SMS Tracking
    if (uiState.isSmsConsentDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closeSmsConsentDialog() },
            icon = {
                Icon(
                    imageVector = Icons.Default.Sms,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "Automatic SMS Tracking Consent",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enable automated expense & income logging directly from bank transaction alerts.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "• 100% On-Device: All processing is offline. No SMS or financial data ever leaves your device.\n" +
                                "• Strict Privacy: We ignore OTPs, verification codes, personal chats, and spam offers.\n" +
                                "• Full Control: You can turn this off anytime and edit or delete any imported transactions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.closeSmsConsentDialog()
                        val perms = buildList {
                            add(Manifest.permission.RECEIVE_SMS); add(Manifest.permission.READ_SMS)
                            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
                        }.toTypedArray()
                        smsPermissionLauncher.launch(perms)
                    }
                ) {
                    Text("Agree & Allow")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeSmsConsentDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Test / Paste SMS Dialog
    if (uiState.isSmsTestDialogOpen) {
        val parsedPreview = remember(testSmsInput) {
            if (testSmsInput.isNotBlank()) SmsTransactionParser.parse("BANK-SMS", testSmsInput) else null
        }

        AlertDialog(
            onDismissRequest = { viewModel.closeSmsTestDialog() },
            title = { Text("Paste & Test Bank SMS", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Paste a transaction SMS from HDFC, SBI, ICICI, Axis, or UPI to test:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = testSmsInput,
                        onValueChange = { testSmsInput = it },
                        placeholder = { Text("e.g. Rs.450 debited from A/c **1234 to VPA swiggy@icici via UPI Ref 123456") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (parsedPreview != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Extracted Details:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Amount: ${CurrencyFormatter.format(parsedPreview.amount)} (${parsedPreview.type.displayName})", style = MaterialTheme.typography.bodySmall)
                                Text("Merchant: ${parsedPreview.merchantOrSender}", style = MaterialTheme.typography.bodySmall)
                                Text("Category: ${parsedPreview.suggestedCategory}", style = MaterialTheme.typography.bodySmall)
                                Text("Method: ${parsedPreview.paymentMethod.displayName}", style = MaterialTheme.typography.bodySmall)
                                if (parsedPreview.referenceNumber != null) {
                                    Text("Ref No: ${parsedPreview.referenceNumber}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    } else if (testSmsInput.isNotBlank()) {
                        Text(
                            text = "No valid financial transaction detected. OTPs and promotions are automatically ignored.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = parsedPreview != null,
                    onClick = {
                        viewModel.importSingleSms(testSmsInput) { success, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                            if (success) {
                                testSmsInput = ""
                                viewModel.closeSmsTestDialog()
                            }
                        }
                    }
                ) {
                    Text("Import Transaction")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closeSmsTestDialog() }) {
                    Text("Close")
                }
            }
        )
    }

    // PIN Setup Dialog
    if (uiState.isSettingPinDialogOpen) {
        AlertDialog(
            onDismissRequest = { viewModel.closePinDialog() },
            title = { Text("Set 4-Digit Security PIN") },
            text = {
                Column {
                    Text(
                        text = "Enter a 4-digit PIN to lock your personal financial records:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = {
                            if (it.length <= 4 && it.all { char -> char.isDigit() }) {
                                pinInput = it
                                pinError = null
                            }
                        },
                        label = { Text("4-Digit PIN") },
                        isError = pinError != null,
                        supportingText = pinError?.let { { Text(it, color = ExpenseRed) } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinInput.length == 4) {
                            viewModel.setPin(pinInput)
                            pinInput = ""
                        } else {
                            pinError = "PIN must be exactly 4 digits."
                        }
                    }
                ) {
                    Text("Save PIN")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.closePinDialog() }) { Text("Cancel") }
            }
        )
    }

    // First Clear Confirmation
    if (uiState.isClearDataConfirmationOpen) {
        ConfirmationDialog(
            title = "Reset All Data?",
            message = "This will erase all your entered transactions, custom categories, and budgets. Are you sure you want to proceed?",
            confirmButtonText = "Continue",
            isDestructive = true,
            onConfirm = { viewModel.proceedToFinalClearConfirmation() },
            onDismiss = { viewModel.closeClearDataConfirmation() }
        )
    }

    // Final Clear Confirmation
    if (uiState.isClearDataFinalConfirmationOpen) {
        ConfirmationDialog(
            title = "Final Warning: Irreversible Action",
            message = "All financial records will be permanently deleted from this device and cannot be recovered. Do you really want to clear everything?",
            confirmButtonText = "Erase Everything",
            isDestructive = true,
            onConfirm = { viewModel.clearAllData() },
            onDismiss = { viewModel.closeClearDataConfirmation() }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("App Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Privacy & Offline Notice Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "100% Offline & Private",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Your financial records never leave your phone. No external tracking or accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            // Quick Navigation: Categories & Recurring
            item {
                Text(
                    text = "Manage Features",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                )
            }

            item {
                SettingsActionCard(
                    title = "Expense & Income Categories",
                    subtitle = "Customize names, icons, and colors",
                    icon = Icons.Default.Category,
                    onClick = onNavigateToCategories
                )
            }

            item {
                SettingsActionCard(
                    title = "Recurring Bills & Salary",
                    subtitle = "Automate regular EMIs, subscriptions, and rent",
                    icon = Icons.Default.EventRepeat,
                    onClick = onNavigateToRecurring
                )
            }

            item {
                SettingsActionCard(
                    title = "Savings Goals",
                    subtitle = "Track targets and monthly savings needed",
                    icon = Icons.Default.Savings,
                    onClick = onNavigateToGoals
                )
            }

            // Appearance & Formatting
            item {
                Text(
                    text = "Preferences",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }

            // Theme Mode
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(text = "App Theme", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemeMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = uiState.themeMode == mode,
                                    onClick = { viewModel.setThemeMode(mode) },
                                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                }
            }

            // First Day of Week
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(text = "First Day of the Week", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY).forEach { day ->
                                FilterChip(
                                    selected = uiState.firstDayOfWeek == day,
                                    onClick = { viewModel.setFirstDayOfWeek(day) },
                                    label = { Text(day.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                }
            }

            // Automatic Transaction Tracking from Bank SMS
            item {
                Text(
                    text = "Automatic Tracking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sms,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Auto-Track from Bank SMS",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = if (uiState.isSmsTrackingEnabled) "Active · Logging bank & UPI alerts" else "Disabled · Tap to enable",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (uiState.isSmsTrackingEnabled) IncomeGreen else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.isSmsTrackingEnabled,
                                onCheckedChange = { enable ->
                                    if (enable) {
                                        viewModel.openSmsConsentDialog()
                                    } else {
                                        viewModel.setSmsTrackingEnabled(false)
                                    }
                                },
                                colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Action buttons: Scan Inbox & Test SMS
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.scanSmsInbox { count ->
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Scanned SMS Inbox: $count new transaction(s) recorded.")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp),
                                enabled = !uiState.isScanningSms
                            ) {
                                if (uiState.isScanningSms) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Inbox", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = { viewModel.openSmsTestDialog() },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste SMS", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Security
            item {
                Text(
                    text = "Security & App Lock",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }

            // PIN Lock Switch
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "4-Digit PIN Lock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (uiState.isPinLockEnabled) "PIN active" else "Not set",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isPinLockEnabled,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    viewModel.openPinDialog()
                                } else {
                                    viewModel.disablePin()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // Biometric Authentication
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(text = "Biometric Lock", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Fingerprint / Face Unlock",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.isBiometricEnabled,
                            enabled = uiState.isPinLockEnabled,
                            onCheckedChange = { viewModel.toggleBiometric(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // Data Backup & Export
            item {
                Text(
                    text = "Backup & Export",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }

            item {
                SettingsActionCard(
                    title = "Export Transactions to CSV",
                    subtitle = "Share or open spreadsheet file",
                    icon = Icons.Default.UploadFile,
                    onClick = { viewModel.exportTransactionsCsv() }
                )
            }

            item {
                SettingsActionCard(
                    title = "Generate Monthly Expense Report",
                    subtitle = "Create formatted summary report to share",
                    icon = Icons.Default.Description,
                    onClick = { viewModel.generateAndShareMonthlyReport() }
                )
            }

            item {
                SettingsActionCard(
                    title = "Database Backup (JSON)",
                    subtitle = "Export complete local database backup",
                    icon = Icons.Default.Share,
                    onClick = { viewModel.exportDatabaseBackup() }
                )
            }

            item {
                SettingsActionCard(
                    title = "Restore from Backup (JSON)",
                    subtitle = "Replace all data from a backup file",
                    icon = Icons.Default.Download,
                    onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*", "*/*")) }
                )
            }

            // Danger Zone: Reset
            item {
                Text(
                    text = "Danger Zone",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = ExpenseRed,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { viewModel.openClearDataConfirmation() },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ExpenseRed.copy(alpha = 0.08f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, tint = ExpenseRed)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "Clear All App Data", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = ExpenseRed)
                            Text(
                                text = "Permanently deletes all transactions and resets to defaults",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExpenseRed.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

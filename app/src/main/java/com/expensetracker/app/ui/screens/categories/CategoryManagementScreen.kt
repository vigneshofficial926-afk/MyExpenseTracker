package com.expensetracker.app.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.expensetracker.app.data.local.entity.CategoryEntity
import com.expensetracker.app.data.model.TransactionType
import com.expensetracker.app.ui.components.CategoryIconHelper
import com.expensetracker.app.ui.components.ConfirmationDialog
import com.expensetracker.app.ui.theme.ExpenseRed

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CategoryManagementScreen(
    viewModel: CategoryViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var categoryToDelete by remember { mutableStateOf<CategoryEntity?>(null) }

    categoryToDelete?.let { cat ->
        ConfirmationDialog(
            title = "Delete Category",
            message = "Are you sure you want to delete category '${cat.name}'?",
            confirmButtonText = "Delete",
            onConfirm = {
                viewModel.deleteCategory(cat)
                categoryToDelete = null
            },
            onDismiss = { categoryToDelete = null }
        )
    }

    if (uiState.isAddEditDialogOpen)
        CategoryEditDialog(uiState.editingCategory, uiState.error, viewModel::closeDialog, viewModel::saveCategory)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Category Management", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.openAddDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Category")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Expense vs Income Category Tabs
            TabRow(
                selectedTabIndex = if (uiState.selectedType == TransactionType.EXPENSE) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.selectedType == TransactionType.EXPENSE,
                    onClick = { viewModel.selectType(TransactionType.EXPENSE) },
                    text = { Text("Expense Categories") }
                )
                Tab(
                    selected = uiState.selectedType == TransactionType.INCOME,
                    onClick = { viewModel.selectType(TransactionType.INCOME) },
                    text = { Text("Income Categories") }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 88.dp)
            ) {
                items(uiState.categories, key = { it.id }) { cat ->
                    val color = try { Color(android.graphics.Color.parseColor(cat.colorHex)) } catch (e: Exception) { MaterialTheme.colorScheme.primary }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = CategoryIconHelper.getIcon(cat.iconName),
                                    contentDescription = cat.name,
                                    tint = color,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = cat.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(onClick = { viewModel.openEditDialog(cat) }) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(18.dp))
                            }

                            IconButton(onClick = { categoryToDelete = cat }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = ExpenseRed.copy(alpha = 0.7f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val CategoryColors = listOf("#F97316", "#EA580C", "#B45309", "#3B82F6", "#6366F1", "#EAB308", "#06B6D4",
    "#EC4899", "#EF4444", "#8B5CF6", "#14B8A6", "#10B981", "#D97706", "#F43F5E", "#A855F7", "#64748B")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditDialog(
    editing: CategoryEntity?, error: String?,
    onDismiss: () -> Unit, onSave: (name: String, icon: String, color: String) -> Unit
) {
    var name by remember(editing) { mutableStateOf(editing?.name ?: "") }
    var icon by remember(editing) { mutableStateOf(editing?.iconName ?: CategoryIconHelper.availableIcons.first()) }
    var color by remember(editing) { mutableStateOf(editing?.colorHex ?: CategoryColors.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) "Edit Category" else "Add Category") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it }, label = { Text("Category Name") }, singleLine = true,
                    isError = error != null, supportingText = error?.let { { Text(it, color = ExpenseRed) } },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.height(14.dp)); Text("Select Icon", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIconHelper.availableIcons.forEach { n ->
                        val sel = icon == n
                        Box(
                            Modifier.size(36.dp).clip(CircleShape)
                                .background(if (sel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .border(if (sel) 2.dp else 0.dp, if (sel) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                                .clickable { icon = n }, contentAlignment = Alignment.Center
                        ) {
                            Icon(CategoryIconHelper.getIcon(n), n, Modifier.size(18.dp),
                                tint = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp)); Text("Select Color", style = MaterialTheme.typography.labelMedium); Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryColors.forEach { hex ->
                        val sel = color.equals(hex, true)
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color(android.graphics.Color.parseColor(hex)))
                                .border(if (sel) 3.dp else 0.dp, if (sel) MaterialTheme.colorScheme.onSurface else Color.Transparent, CircleShape)
                                .clickable { color = hex }
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(name, icon, color) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

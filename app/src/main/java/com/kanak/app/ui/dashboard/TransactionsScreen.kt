package com.kanak.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanak.app.designsystem.components.CategoryIcon
import com.kanak.app.designsystem.components.KanakAmountCard
import com.kanak.app.designsystem.components.KanakCard
import com.kanak.app.designsystem.components.KanakCategorizationBadge
import com.kanak.app.designsystem.components.KanakCategoryCard
import com.kanak.app.designsystem.components.KanakPrimaryButton
import com.kanak.app.designsystem.components.KanakSourceBadge
import com.kanak.app.designsystem.components.KanakTextField
import com.kanak.app.designsystem.theme.KanakCornerRadius
import com.kanak.app.designsystem.theme.KanakSpacing
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.ui.transactions.DateGroupedTransactions
import com.kanak.app.ui.transactions.DateRangeFilter
import com.kanak.app.ui.transactions.TransactionsUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    uiState: TransactionsUiState,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onDateFilterSelected: (DateRangeFilter) -> Unit,
    onCategoryFilterSelected: (ExpenseCategory?) -> Unit,
    onExpenseClicked: (Expense) -> Unit,
    onEditAmountChanged: (String) -> Unit,
    onEditCategorySelected: (ExpenseCategory?) -> Unit,
    onEditNoteChanged: (String) -> Unit,
    onCloseEditSheet: () -> Unit,
    onSaveEdit: () -> Unit,
    onRequestDelete: (Expense) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onClearUserMessage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage = uiState.userMessage

    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            snackbarHostState.showSnackbar(userMessage)
            onClearUserMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = KanakSpacing.medium),
                verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
            ) {
                item {
                    Column(
                        modifier = Modifier.padding(top = KanakSpacing.medium),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Transactions",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        val summaryText = if (uiState.totalFilteredCount == 1) {
                            "1 transaction • ${uiState.totalFilteredAmountFormatted}"
                        } else {
                            "${uiState.totalFilteredCount} transactions • ${uiState.totalFilteredAmountFormatted}"
                        }
                        Text(
                            text = summaryText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    TransactionsSearchInput(
                        query = uiState.searchQuery,
                        onQueryChanged = onSearchQueryChanged,
                        onClearClick = onClearSearch
                    )
                }

                item {
                    FilterSection(
                        categories = uiState.availableCategories,
                        selectedDateFilter = uiState.selectedDateFilter,
                        onDateFilterSelected = onDateFilterSelected,
                        selectedCategoryFilter = uiState.selectedCategoryFilter,
                        onCategoryFilterSelected = onCategoryFilterSelected
                    )
                }

                if (uiState.groupedTransactions.isNotEmpty()) {
                    uiState.groupedTransactions.forEach { group ->
                        item(key = "header_${group.dateHeader}_${group.dateMillis}") {
                            DateGroupHeader(
                                group = group,
                                currency = uiState.currency
                            )
                        }

                        items(group.expenses, key = { it.id }) { expense ->
                            TransactionCard(
                                expense = expense,
                                onClick = { onExpenseClicked(expense) }
                            )
                        }
                    }
                } else {
                    item {
                        val isFiltered = (uiState.searchQuery.isNotBlank() ||
                                uiState.selectedDateFilter != DateRangeFilter.ALL ||
                                uiState.selectedCategoryFilter != null)

                        if (isFiltered) {
                            NoResultsState(
                                onResetFilters = {
                                    onClearSearch()
                                    onDateFilterSelected(DateRangeFilter.ALL)
                                    onCategoryFilterSelected(null)
                                }
                            )
                        } else {
                            EmptyTransactionsState()
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(KanakSpacing.large))
                }
            }

            if (uiState.selectedExpenseForEdit != null) {
                EditTransactionSheet(
                    expense = uiState.selectedExpenseForEdit,
                    uiState = uiState,
                    onEditAmountChanged = onEditAmountChanged,
                    onEditCategorySelected = onEditCategorySelected,
                    onEditNoteChanged = onEditNoteChanged,
                    onClose = onCloseEditSheet,
                    onSave = onSaveEdit,
                    onDelete = onRequestDelete
                )
            }

            if (uiState.expenseToDelete != null) {
                DeleteConfirmationDialog(
                    expense = uiState.expenseToDelete,
                    onConfirm = onConfirmDelete,
                    onDismiss = onCancelDelete
                )
            }
        }
    }
}

/**
 * Backward compatibility overload for simple list display.
 */
@Composable
fun TransactionsScreen(
    expenses: List<Expense>,
    currency: String,
    modifier: Modifier = Modifier
) {
    val sorted = remember(expenses) { expenses.sortedByDescending { it.occurredAt } }
    val grouped = remember(sorted) {
        if (sorted.isEmpty()) emptyList() else listOf(
            DateGroupedTransactions(
                dateHeader = "All Transactions",
                dateMillis = sorted.firstOrNull()?.occurredAt ?: 0L,
                totalAmountMinor = sorted.sumOf { it.amountMinor },
                expenses = sorted
            )
        )
    }
    val uiState = remember(expenses, currency, grouped) {
        TransactionsUiState(
            groupedTransactions = grouped,
            totalFilteredCount = expenses.size,
            totalFilteredAmountMinor = sorted.sumOf { it.amountMinor },
            currency = currency
        )
    }
    TransactionsScreen(
        uiState = uiState,
        onSearchQueryChanged = {},
        onClearSearch = {},
        onDateFilterSelected = {},
        onCategoryFilterSelected = {},
        onExpenseClicked = {},
        onEditAmountChanged = {},
        onEditCategorySelected = {},
        onEditNoteChanged = {},
        onCloseEditSheet = {},
        onSaveEdit = {},
        onRequestDelete = {},
        onCancelDelete = {},
        onConfirmDelete = {},
        onClearUserMessage = {},
        modifier = modifier
    )
}

@Composable
private fun TransactionsSearchInput(
    query: String,
    onQueryChanged: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(KanakCornerRadius.medium)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape)
            .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "🔍",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(end = KanakSpacing.small)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChanged,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text(
                        text = "Search note or category...",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(KanakCornerRadius.pill))
                    .clickable(onClick = onClearClick)
                    .padding(KanakSpacing.extraSmall)
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FilterSection(
    categories: List<ExpenseCategory>,
    selectedDateFilter: DateRangeFilter,
    onDateFilterSelected: (DateRangeFilter) -> Unit,
    selectedCategoryFilter: ExpenseCategory?,
    onCategoryFilterSelected: (ExpenseCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DateRangeFilter.entries.forEach { filter ->
                val selected = filter == selectedDateFilter
                val shape = RoundedCornerShape(KanakCornerRadius.pill)
                val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                Box(
                    modifier = Modifier
                        .clip(shape)
                        .background(bg)
                        .border(if (selected) 1.5.dp else 1.dp, border, shape)
                        .clickable { onDateFilterSelected(filter) }
                        .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small)
                ) {
                    Text(
                        text = filter.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val allSelected = selectedCategoryFilter == null
            val allShape = RoundedCornerShape(KanakCornerRadius.pill)
            val allBg = if (allSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
            val allBorder = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            val allTextColor = if (allSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

            Box(
                modifier = Modifier
                    .clip(allShape)
                    .background(allBg)
                    .border(if (allSelected) 1.5.dp else 1.dp, allBorder, allShape)
                    .clickable { onCategoryFilterSelected(null) }
                    .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small)
            ) {
                Text(
                    text = "All Categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = allTextColor,
                    fontWeight = if (allSelected) FontWeight.Bold else FontWeight.Medium
                )
            }

            categories.forEach { cat ->
                val selected = cat == selectedCategoryFilter
                val shape = RoundedCornerShape(KanakCornerRadius.pill)
                val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                Row(
                    modifier = Modifier
                        .clip(shape)
                        .background(bg)
                        .border(if (selected) 1.5.dp else 1.dp, border, shape)
                        .clickable {
                            onCategoryFilterSelected(if (selected) null else cat)
                        }
                        .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryIcon(category = cat, tint = textColor, size = 16.dp)
                    Text(
                        text = cat.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = textColor,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun DateGroupHeader(
    group: DateGroupedTransactions,
    currency: String,
    modifier: Modifier = Modifier
) {
    val groupTotalFormatted = Expense(amountMinor = group.totalAmountMinor, currency = currency).amountWithSymbol()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = KanakSpacing.small, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = group.dateHeader.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = groupTotalFormatted,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun TransactionCard(
    expense: Expense,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFormat = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeStr = remember(expense.occurredAt) { timeFormat.format(Date(expense.occurredAt)) }

    KanakCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
            ) {
                val category = expense.category
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(KanakCornerRadius.medium))
                        .background(
                            if (category != null) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .padding(10.dp)
                ) {
                    CategoryIcon(
                        category = category ?: ExpenseCategory.OTHER,
                        tint = if (category != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        size = 20.dp
                    )
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val titleText = expense.note.takeIf { !it.isNullOrBlank() } ?: category?.displayName ?: "Uncategorized"
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!expense.note.isNullOrBlank() && category != null) {
                            Text(
                                text = category.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = timeStr,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(KanakSpacing.small))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = expense.amountWithSymbol(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    KanakSourceBadge(source = expense.source)
                }
            }
        }
    }
}

@Composable
private fun NoResultsState(
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    KanakCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = KanakSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
        ) {
            Text(
                text = "🔎 No Matching Transactions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "No expenses match your search or filters.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(KanakSpacing.extraSmall))
            KanakPrimaryButton(
                onClick = onResetFilters,
                text = "Clear Filters",
                modifier = Modifier.fillMaxWidth(0.6f)
            )
        }
    }
}

@Composable
private fun EmptyTransactionsState(
    modifier: Modifier = Modifier
) {
    KanakCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = KanakSpacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
        ) {
            Text(
                text = "💳 No Transactions Yet",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Expenses you record will be organized here by date.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTransactionSheet(
    expense: Expense,
    uiState: TransactionsUiState,
    onEditAmountChanged: (String) -> Unit,
    onEditCategorySelected: (ExpenseCategory?) -> Unit,
    onEditNoteChanged: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onDelete: (Expense) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(KanakSpacing.medium)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transaction Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TextButton(onClick = onClose) {
                    Text("Done")
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small),
                verticalAlignment = Alignment.CenterVertically
            ) {
                KanakSourceBadge(source = expense.source)
                KanakCategorizationBadge(
                    state = if (uiState.editCategory != null) CategorizationState.CONFIRMED else expense.categorizationState
                )
            }

            KanakAmountCard(
                value = uiState.editAmountText,
                onValueChange = onEditAmountChanged,
                currencySymbol = Expense.getCurrencySymbol(uiState.currency),
                isError = uiState.editAmountError != null,
                errorMessage = uiState.editAmountError,
                voiceInputAvailable = false
            )

            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                Text(
                    text = "CATEGORY",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small)
                ) {
                    uiState.availableCategories.forEach { cat ->
                        KanakCategoryCard(
                            category = cat,
                            selected = uiState.editCategory == cat,
                            onClick = { onEditCategorySelected(cat) }
                        )
                    }
                }
            }

            KanakTextField(
                value = uiState.editNote,
                onValueChange = onEditNoteChanged,
                label = "Note",
                placeholder = "Add a description..."
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = KanakSpacing.small),
                horizontalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
            ) {
                OutlinedButton(
                    onClick = { onDelete(expense) },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = RoundedCornerShape(KanakCornerRadius.medium),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }

                KanakPrimaryButton(
                    onClick = onSave,
                    enabled = !uiState.isSavingEdit,
                    text = if (uiState.isSavingEdit) "Saving..." else "Save Changes",
                    modifier = Modifier.weight(1.5f)
                )
            }

            Spacer(modifier = Modifier.height(KanakSpacing.medium))
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    expense: Expense,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete Transaction?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to delete this transaction of ${expense.amountWithSymbol()}? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = "Delete",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

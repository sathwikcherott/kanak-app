package com.kanak.app.ui.entry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.voice.MathExpressionParser

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QuickEntryScreen(
    uiState: QuickEntryUiState,
    onAmountChanged: (String) -> Unit,
    onCategorySelected: (ExpenseCategory?) -> Unit,
    onNoteChanged: (String) -> Unit,
    onDateOptionSelected: (ExpenseDateOption) -> Unit,
    onDateChosenFromPicker: (Long) -> Unit,
    onDismissDatePicker: () -> Unit,
    onMicTapped: () -> Unit,
    onVoicePermissionDenied: () -> Unit,
    onSaveClicked: () -> Unit,
    onClearUserMessage: () -> Unit,
    modifier: Modifier = Modifier,
    autoStartVoice: Boolean = false,
    onOpenNewCategory: () -> Unit = {},
    onNewCategoryNameChanged: (String) -> Unit = {},
    onCreateCategory: () -> Unit = {},
    onCancelNewCategory: () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val calculationKeyboard = rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onMicTapped()
        } else {
            onVoicePermissionDenied()
        }
    }

    fun handleMicClick() {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            onMicTapped()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Automatic voice entry uses the same permission path as the microphone button.
    // The sheet host enables this only after the opening transition has completed.
    LaunchedEffect(autoStartVoice) {
        if (autoStartVoice) handleMicClick()
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onClearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Kanak",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                actions = {
                    KanakSourceBadge(source = uiState.source, modifier = Modifier.padding(end = KanakSpacing.medium))
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
            .navigationBarsPadding()
            .imePadding()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = KanakSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
        ) {
            item {
                AnimatedVisibility(visible = uiState.isListening) {
                    Text(
                        text = "Listening... Speak amount & category (e.g. '250 for lunch')",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = KanakSpacing.extraSmall)
                    )
                }

                KanakAmountCard(
                    value = uiState.amountText,
                    onValueChange = onAmountChanged,
                    currencySymbol = Expense.getCurrencySymbol(uiState.currency),
                    isError = uiState.amountError != null,
                    errorMessage = uiState.amountError,
                    isListening = uiState.isListening,
                    voiceInputAvailable = uiState.voiceInputAvailable,
                    onMicTapped = { handleMicClick() },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (calculationKeyboard.value) KeyboardType.Ascii else KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val amount = uiState.amountMinor
                    if (MathExpressionParser.hasOperator(uiState.amountText) && amount > 0L) {
                        Text(
                            text = "= ${Expense(amountMinor = amount, currency = uiState.currency).amountWithSymbol()}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    TextButton(onClick = { calculationKeyboard.value = !calculationKeyboard.value }) {
                        Text(if (calculationKeyboard.value) "Number keyboard" else "Calculate")
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "EXPENSE DATE",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = uiState.selectedDateFormatted,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small)
                    ) {
                        ExpenseDateOption.entries.forEach { option ->
                            val selected = option == uiState.selectedDateOption
                            val shape = RoundedCornerShape(KanakCornerRadius.pill)
                            val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                            val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                            val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                            val displayText = when (option) {
                                ExpenseDateOption.TODAY -> "Today"
                                ExpenseDateOption.YESTERDAY -> "Yesterday"
                                ExpenseDateOption.CHOSEN_DATE -> if (selected) uiState.selectedDateFormatted else "Choose date"
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(bg)
                                    .border(if (selected) 1.5.dp else 1.dp, border, shape)
                                    .clickable { onDateOptionSelected(option) }
                                    .padding(vertical = KanakSpacing.small, horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = displayText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    if (uiState.dateError != null) {
                        Text(
                            text = uiState.dateError,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CATEGORY",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = onOpenNewCategory, enabled = !uiState.isSaving && !uiState.isCreatingCategory) { Text("+ New category") }
                            KanakCategorizationBadge(state = uiState.categorizationState)
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Category grid" }
                    ) {
                        uiState.availableCategories.forEach { category ->
                            KanakCategoryCard(
                                category = category,
                                selected = uiState.selectedCategory == category,
                                onClick = { onCategorySelected(category) }
                            )
                        }
                    }
                }
            }

            item {
                KanakTextField(
                    value = uiState.note,
                    onValueChange = onNoteChanged,
                    label = "Description",
                    placeholder = "Optional note (e.g. Coffee with team)",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSaveClicked() })
                )
            }

            item {
                Spacer(modifier = Modifier.height(KanakSpacing.small))
                KanakPrimaryButton(
                    onClick = onSaveClicked,
                    enabled = uiState.isValidInput && !uiState.isSaving
                ) {
                    if (uiState.isSaving) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.height(24.dp)
                        )
                    } else {
                        Text(
                            text = "Save Expense",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (uiState.recentExpenses.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(KanakSpacing.small))
                    Text(
                        text = "RECENT TRANSACTIONS",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                items(uiState.recentExpenses, key = { it.id }) { expense ->
                    ExpenseItemCard(expense = expense)
                }
            }
        }
    }

    if (uiState.showNewCategoryDialog) {
        NewCategoryDialog(uiState.newCategoryName, uiState.categoryError, uiState.isCreatingCategory,
            onNewCategoryNameChanged, onCreateCategory, onCancelNewCategory)
    }

    if (uiState.showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = QuickEntryViewModel.localMillisToPickerUtc(uiState.selectedDateMillis),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                    return QuickEntryViewModel.isPickerDateSelectable(utcTimeMillis)
                }
            }
        )

        DatePickerDialog(
            onDismissRequest = onDismissDatePicker,
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { utcMillis ->
                            onDateChosenFromPicker(utcMillis)
                        }
                    }
                ) {
                    Text("Select", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissDatePicker) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Backward compatibility overload for legacy callers without date callbacks.
 */
@Composable
fun QuickEntryScreen(
    uiState: QuickEntryUiState,
    onAmountChanged: (String) -> Unit,
    onCategorySelected: (ExpenseCategory?) -> Unit,
    onNoteChanged: (String) -> Unit,
    onMicTapped: () -> Unit,
    onVoicePermissionDenied: () -> Unit,
    onSaveClicked: () -> Unit,
    onClearUserMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    QuickEntryScreen(
        uiState = uiState,
        onAmountChanged = onAmountChanged,
        onCategorySelected = onCategorySelected,
        onNoteChanged = onNoteChanged,
        onDateOptionSelected = {},
        onDateChosenFromPicker = {},
        onDismissDatePicker = {},
        onMicTapped = onMicTapped,
        onVoicePermissionDenied = onVoicePermissionDenied,
        onSaveClicked = onSaveClicked,
        onClearUserMessage = onClearUserMessage,
        modifier = modifier
    )
}

@Composable
private fun ExpenseItemCard(
    expense: Expense,
    modifier: Modifier = Modifier
) {
    KanakCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
            ) {
                expense.category?.let { cat ->
                    CategoryIcon(
                        category = cat,
                        tint = MaterialTheme.colorScheme.primary,
                        size = 22.dp
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = expense.category?.displayName ?: "Uncategorized",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    expense.note?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            Text(
                text = expense.amountWithSymbol(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

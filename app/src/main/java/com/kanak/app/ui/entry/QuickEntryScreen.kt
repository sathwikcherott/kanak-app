package com.kanak.app.ui.entry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kanak.app.designsystem.components.KanakAmountInput
import com.kanak.app.designsystem.components.KanakCard
import com.kanak.app.designsystem.components.KanakCategoryChip
import com.kanak.app.designsystem.components.KanakCategorizationBadge
import com.kanak.app.designsystem.components.KanakPrimaryButton
import com.kanak.app.designsystem.components.KanakSourceBadge
import com.kanak.app.designsystem.components.KanakTextField
import com.kanak.app.designsystem.theme.KanakSpacing
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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
                        text = "Quick Expense Entry",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                actions = {
                    if (uiState.voiceInputAvailable) {
                        IconButton(
                            onClick = { handleMicClick() },
                            modifier = Modifier.semantics {
                                contentDescription = if (uiState.isListening) "Stop voice input" else "Start voice input"
                            }
                        ) {
                            if (uiState.isListening) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.height(24.dp)
                                )
                            } else {
                                Text(
                                    text = "🎤",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = KanakSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "New Transaction",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    KanakSourceBadge(source = uiState.source)
                }

                AnimatedVisibility(visible = uiState.isListening) {
                    Text(
                        text = "Listening... Speak your expense (e.g. '15 dollars for lunch')",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item {
                KanakAmountInput(
                    value = uiState.amountText,
                    onValueChange = onAmountChanged,
                    currencySymbol = uiState.currency,
                    isError = uiState.amountError != null,
                    errorMessage = uiState.amountError,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    )
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Category",
                            style = MaterialTheme.typography.titleMedium
                        )
                        KanakCategorizationBadge(state = uiState.categorizationState)
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small),
                        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small),
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { contentDescription = "Category selector chips" }
                    ) {
                        ExpenseCategory.entries.forEach { category ->
                            KanakCategoryChip(
                                selected = uiState.selectedCategory == category,
                                onClick = { onCategorySelected(category) },
                                label = category.displayName
                            )
                        }
                    }
                }
            }

            item {
                KanakTextField(
                    value = uiState.note,
                    onValueChange = onNoteChanged,
                    label = "Optional Note",
                    placeholder = "e.g. Coffee at Starbucks",
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
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }

            if (uiState.recentExpenses.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(KanakSpacing.small))
                    Text(
                        text = "Recent Offline Expenses",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                items(uiState.recentExpenses, key = { it.id }) { expense ->
                    ExpenseItemCard(expense = expense)
                }
            }
        }
    }
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = expense.category?.displayName ?: "Uncategorized",
                    style = MaterialTheme.typography.titleMedium
                )
                expense.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = "${expense.currency} ${expense.amountAsFormattedString()}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

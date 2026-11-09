package com.kanak.app.ui.settings

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import com.kanak.app.data.backup.BackupFileNames
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kanak.app.ai.AiConnectionStore
import com.kanak.app.data.backup.BackupSummary
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.designsystem.components.KanakCard
import com.kanak.app.designsystem.components.KanakPrimaryButton
import com.kanak.app.designsystem.theme.KanakCornerRadius
import com.kanak.app.designsystem.theme.KanakSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CurrencyOption(
    val code: String,
    val name: String,
    val symbol: String
)

val SupportedCurrencies = listOf(
    CurrencyOption("INR", "Indian Rupee", "₹"),
    CurrencyOption("USD", "US Dollar", "$"),
    CurrencyOption("EUR", "Euro", "€"),
    CurrencyOption("GBP", "British Pound", "£")
)

@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeSelected: (ThemePreference) -> Unit,
    onOpenCurrencyDialog: () -> Unit,
    onCloseCurrencyDialog: () -> Unit,
    onCurrencySelected: (String) -> Unit,
    onExportBackup: (Context, Uri) -> Unit,
    onRestoreFileSelected: (Context, Uri) -> Unit,
    onConfirmRestore: () -> Unit,
    onCancelRestore: () -> Unit,
    onClearBackupError: () -> Unit,
    onClearUserMessage: () -> Unit,
    modifier: Modifier = Modifier,
    onManageBackupFiles: () -> Unit = {},
    aiConnection: AiConnectionStore? = null,
    autoBackupContent: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val userMessage = uiState.userMessage

    LaunchedEffect(userMessage) {
        if (userMessage != null) {
            snackbarHostState.showSnackbar(userMessage)
            onClearUserMessage()
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            onExportBackup(context, uri)
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onRestoreFileSelected(context, uri)
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
                            text = "Settings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Application preferences & app information",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                item {
                    AppearanceSection(
                        selectedTheme = uiState.themePreference,
                        onThemeSelected = onThemeSelected
                    )
                }

                item {
                    CurrencySection(
                        selectedCurrencyCode = uiState.currency,
                        onOpenCurrencyDialog = onOpenCurrencyDialog
                    )
                }

                item {
                    BackupAndRestoreSection(
                        lastBackupTime = uiState.lastBackupExportedTime,
                        isExporting = uiState.isExportingBackup,
                        hasPreview = uiState.pendingRestoreSummary != null,
                        isRestoring = uiState.isRestoringBackup,
                        onExportClick = {
                            exportLauncher.launch(BackupFileNames.create())
                        },
                        onManageFiles = onManageBackupFiles,
                        onRestoreClick = {
                            restoreLauncher.launch(arrayOf("application/json", "*/*"))
                        }
                    )
                }

                item { autoBackupContent() }

                item {
                    if (aiConnection != null) AiCategorizationSection(aiConnection)
                }

                item {
                    AboutKanakSection(appVersion = uiState.appVersion)
                }

                item {
                    Spacer(modifier = Modifier.height(KanakSpacing.large))
                }
            }

            if (uiState.showCurrencyDialog) {
                CurrencySelectionDialog(
                    selectedCurrencyCode = uiState.currency,
                    onCurrencySelected = onCurrencySelected,
                    onDismiss = onCloseCurrencyDialog
                )
            }

            if (uiState.pendingRestoreSummary != null) {
                RestoreConfirmationDialog(
                    summary = uiState.pendingRestoreSummary,
                    isRestoring = uiState.isRestoringBackup,
                    onConfirm = onConfirmRestore,
                    onDismiss = onCancelRestore
                )
            }

            if (uiState.restoreWarning != null) {
                BackupErrorDialog(
                    errorMessage = uiState.restoreWarning,
                    title = "Expenses restored",
                    onDismiss = onClearUserMessage
                )
            }

            if (uiState.backupError != null) {
                BackupErrorDialog(
                    errorMessage = uiState.backupError,
                    onDismiss = onClearBackupError
                )
            }
        }
    }
}

@Composable
private fun AppearanceSection(
    selectedTheme: ThemePreference,
    onThemeSelected: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
    ) {
        Text(
            text = "APPEARANCE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        KanakCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Theme Preference",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Choose app color mode (Dark includes true AMOLED black)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small)
                ) {
                    ThemePreference.entries.forEach { theme ->
                        val selected = theme == selectedTheme
                        val shape = RoundedCornerShape(KanakCornerRadius.pill)
                        val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        val border = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        val textColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .background(bg)
                                .border(if (selected) 1.5.dp else 1.dp, border, shape)
                                .clickable { onThemeSelected(theme) }
                                .padding(vertical = KanakSpacing.small, horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = theme.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                color = textColor,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrencySection(
    selectedCurrencyCode: String,
    onOpenCurrencyDialog: () -> Unit,
    modifier: Modifier = Modifier
) {
    val matchedCurrency = SupportedCurrencies.find { it.code == selectedCurrencyCode }
        ?: CurrencyOption(selectedCurrencyCode, selectedCurrencyCode, "$selectedCurrencyCode ")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
    ) {
        Text(
            text = "DISPLAY CURRENCY",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        KanakCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = "Display Currency",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "${matchedCurrency.code} (${matchedCurrency.symbol}) - ${matchedCurrency.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(KanakCornerRadius.medium))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable(onClick = onOpenCurrencyDialog)
                            .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small)
                    ) {
                        Text(
                            text = "Change",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                )

                Text(
                    text = "Note: Preferred display currency formats amounts across Home, Transactions, and Analytics. Changing this setting updates formatting symbols only and does not automatically convert stored monetary values.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BackupAndRestoreSection(
    lastBackupTime: Long?,
    isExporting: Boolean,
    hasPreview: Boolean,
    isRestoring: Boolean,
    onExportClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onManageFiles: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAdvanced by rememberSaveable { mutableStateOf(false) }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val lastBackupText = if (lastBackupTime != null) {
        "Last exported: ${dateFormat.format(Date(lastBackupTime))}"
    } else {
        "No backup exported yet"
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
    ) {
        Text(
            text = "LOCAL BACKUP & RESTORE",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        KanakCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Data Backup & Restore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = lastBackupText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Export your expenses and preferences as an unencrypted JSON file. Restore replaces your current expenses and preferences. Export a backup first if you want to keep your current data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(KanakSpacing.medium)
                ) {
                    OutlinedButton(
                        onClick = onExportClick,
                        enabled = !isExporting && !isRestoring && !hasPreview,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(KanakCornerRadius.medium)
                    ) {
                        Text(
                            text = if (isExporting) "Exporting..." else "Export Backup",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }

                    KanakPrimaryButton(
                        onClick = onRestoreClick,
                        enabled = !isExporting && !isRestoring && !hasPreview,
                        text = if (isRestoring) "Reading backup..." else "Restore Backup",
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    )
                }
                TextButton(onClick = { showAdvanced = !showAdvanced }, enabled = !isExporting && !isRestoring && !hasPreview) {
                    Text(if (showAdvanced) "Hide advanced" else "Advanced", style = MaterialTheme.typography.labelMedium)
                }
                if (showAdvanced) {
                    Text("Keep your backup files together in a Kanak folder.", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = onManageFiles, enabled = !isExporting && !isRestoring && !hasPreview) { Text("Manage backup files") }
                }
            }
        }
    }
}

@Composable
private fun RestoreConfirmationDialog(
    summary: BackupSummary,
    isRestoring: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val exportDateStr = remember(summary.exportedAt) { dateFormat.format(Date(summary.exportedAt)) }

    AlertDialog(
        onDismissRequest = { if (!isRestoring) onDismiss() },
        title = {
            Text(
                text = "Restore Backup?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(KanakSpacing.medium)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(KanakCornerRadius.medium))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f))
                        .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(KanakCornerRadius.medium))
                        .padding(KanakSpacing.small)
                ) {
                    Text(
                        text = "⚠️ Warning: Restoring this backup will replace all current expenses, custom categories, and currency/theme preferences with the backup data. This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "BACKUP SUMMARY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Schema Version: v${summary.version}",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Exported Date: $exportDateStr",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Expenses Count: ${summary.totalExpenses} records",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(text = "• Custom categories saved: ${summary.totalCustomCategories}", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = "• Unique Categories: ${summary.totalCategoriesCount} categories",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "• Preferences: ${summary.defaultCurrency} • ${summary.themePreference.displayName}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isRestoring,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(KanakCornerRadius.medium)
            ) {
                Text(if (isRestoring) "Restoring..." else "Replace & Restore", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isRestoring) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(KanakCornerRadius.medium),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun BackupErrorDialog(
    errorMessage: String,
    title: String = "Backup / Restore Error",
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        },
        text = {
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(KanakCornerRadius.medium),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun CurrencySelectionDialog(
    selectedCurrencyCode: String,
    onCurrencySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Display Currency",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                SupportedCurrencies.forEach { option ->
                    val isSelected = option.code == selectedCurrencyCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(KanakCornerRadius.medium))
                            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onCurrencySelected(option.code) }
                            .padding(horizontal = KanakSpacing.medium, vertical = KanakSpacing.small),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onCurrencySelected(option.code) }
                            )
                            Column {
                                Text(
                                    text = "${option.code} (${option.symbol})",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = option.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(KanakCornerRadius.medium),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun AiCategorizationSection(connection: AiConnectionStore) {
    val endpoint by connection.endpoint.collectAsState()
    var showSetup by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
        Text(
            text = "AI CATEGORIZATION",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        KanakCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                Text(
                    text = endpoint?.let { "Configured for ${java.net.URI(it).host}" } ?: "Cloud suggestions are off",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Quick Entry can ask Gemini for a built-in category when local rules find no match. You choose when to share the previewed description and whether to use the suggestion.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(horizontalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                    TextButton(onClick = { showSetup = true }) {
                        Text(if (endpoint == null) "Configure" else "Edit connection")
                    }
                    if (endpoint != null) {
                        TextButton(onClick = connection::disconnect) { Text("Disconnect") }
                    }
                }
            }
        }
    }

    if (showSetup) {
        var address by remember { mutableStateOf(endpoint.orEmpty()) }
        var token by remember { mutableStateOf("") }
        var showError by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showSetup = false },
            title = { Text("Configure cloud suggestions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                    Text("Enter your private backend URL and its access token.")
                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            address = it
                            showError = false
                        },
                        singleLine = true,
                        label = { Text("HTTPS endpoint") }
                    )
                    OutlinedTextField(
                        value = token,
                        onValueChange = {
                            token = it
                            showError = false
                        },
                        singleLine = true,
                        label = { Text("Backend access token") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (showError) {
                        Text(
                            "Use an HTTPS URL ending in /categorize and a valid backend access token.",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (connection.configure(address, token)) showSetup = false else showError = true
                }) { Text("Save connection") }
            },
            dismissButton = { TextButton(onClick = { showSetup = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun AboutKanakSection(
    appVersion: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)
    ) {
        Text(
            text = "ABOUT KANAK",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        KanakCard(modifier = Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kanak Expense Tracker",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = "v$appVersion",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "Fast, privacy-focused, offline-first expense tracker built with Jetpack Compose & Room. Designed with true AMOLED black aesthetics.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

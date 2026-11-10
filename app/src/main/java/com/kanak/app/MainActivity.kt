package com.kanak.app

import android.app.Activity
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.designsystem.theme.KanakAppTheme
import com.kanak.app.ui.analytics.AnalyticsViewModel
import com.kanak.app.ui.dashboard.AnalyticsScreen
import com.kanak.app.ui.dashboard.HomeScreen
import com.kanak.app.ui.dashboard.HomeViewModel
import com.kanak.app.ui.dashboard.TransactionsScreen
import com.kanak.app.ui.entry.QuickEntrySheet
import com.kanak.app.ui.entry.QuickEntryViewModel
import com.kanak.app.ui.navigation.ExitConfirmationDialog
import com.kanak.app.ui.navigation.KanakBottomNavigation
import com.kanak.app.ui.navigation.KanakTab
import com.kanak.app.ui.settings.SettingsScreen
import com.kanak.app.ui.settings.SettingsViewModel
import com.kanak.app.ui.transactions.TransactionsViewModel
import com.kanak.app.ui.smartinbox.SmartInboxViewModel
import com.kanak.app.ui.smartinbox.SmartInboxScreen
import androidx.compose.runtime.saveable.rememberSaveable
import com.kanak.app.ui.settings.AutoBackupSection
import com.kanak.app.ui.settings.BackupFilesRoute
import com.kanak.app.ui.settings.BackupFilesViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val sharedText = MutableStateFlow<String?>(null)

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory((application as KanakApp).expenseRepository)
    }

    private val quickEntryViewModel: QuickEntryViewModel by viewModels {
        val app = application as KanakApp
        QuickEntryViewModel.Factory(
            repository = app.expenseRepository,
            voiceInputRepository = app.voiceInputRepository,
            aiCategorizer = app.cloudAiCategorizer
        )
    }

    private val transactionsViewModel: TransactionsViewModel by viewModels {
        TransactionsViewModel.Factory((application as KanakApp).expenseRepository)
    }

    private val analyticsViewModel: AnalyticsViewModel by viewModels {
        AnalyticsViewModel.Factory((application as KanakApp).expenseRepository)
    }

    private val settingsViewModel: SettingsViewModel by viewModels {
        val app = application as KanakApp
        SettingsViewModel.Factory(app.expenseRepository, app.backupRepository)
    }

    private val backupFilesViewModel: BackupFilesViewModel by viewModels {
        val app = application as KanakApp
        BackupFilesViewModel.Factory(app.backupFileCatalog, app.backupFolderAccess)
    }
    private val smartInboxViewModel: SmartInboxViewModel by viewModels {
        val app = application as KanakApp
        SmartInboxViewModel.Factory(app.smartInboxRepository, app.expenseRepository)
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        acceptSharedText(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val context = LocalContext.current
            val aiConnection = (application as KanakApp).aiConnectionStore
            val aiEndpoint by aiConnection.endpoint.collectAsState()
            var showBackupFiles by rememberSaveable { mutableStateOf(false) }
            var showSmartInbox by rememberSaveable { mutableStateOf(false) }
            var showExitDialog by remember { mutableStateOf(value = false) }

            // Sheet visibility is navigation state, not dashboard content. Keep the
            // dashboard's input object unchanged while the sheet opens and animates.
            val homeContent = remember(homeViewModel) {
                homeViewModel.uiState.map {
                    it.copy(showQuickEntry = false, triggerVoiceOnEntry = false)
                }.distinctUntilChanged()
            }
            val homeUiState by homeContent.collectAsState(
                initial = homeViewModel.uiState.value.copy(showQuickEntry = false, triggerVoiceOnEntry = false)
            )
            val quickEntryVisibility = remember(homeViewModel) {
                homeViewModel.uiState.map { it.showQuickEntry }.distinctUntilChanged()
            }
            val showQuickEntry by quickEntryVisibility.collectAsState(
                initial = homeViewModel.uiState.value.showQuickEntry
            )
            val transactionsUiState by transactionsViewModel.uiState.collectAsState()
            val analyticsUiState by analyticsViewModel.uiState.collectAsState()
            val settingsUiState by settingsViewModel.uiState.collectAsState()
            val smartInboxUiState by smartInboxViewModel.uiState.collectAsState()
            val pendingSharedText by sharedText.collectAsState()

            androidx.compose.runtime.LaunchedEffect(pendingSharedText) {
                pendingSharedText?.let { text ->
                    showSmartInbox = true
                    smartInboxViewModel.importSharedText(text)
                    sharedText.value = null
                }
            }

            val isSystemDark = isSystemInDarkTheme()
            val darkTheme = when (settingsUiState.themePreference) {
                ThemePreference.SYSTEM -> isSystemDark
                ThemePreference.DARK -> true
                ThemePreference.LIGHT -> false
            }

            BackHandler(enabled = !showQuickEntry) {
                if (showExitDialog) {
                    showExitDialog = false
                } else if (showSmartInbox) {
                    showSmartInbox = false
                } else if (homeUiState.activeTab != KanakTab.HOME) {
                    homeViewModel.selectTab(KanakTab.HOME)
                } else {
                    showExitDialog = true
                }
            }

            KanakAppTheme(darkTheme = darkTheme) {
                Scaffold(
                    bottomBar = {
                        if (!showSmartInbox) {
                            KanakBottomNavigation(
                                activeTab = homeUiState.activeTab,
                                onTabSelected = homeViewModel::selectTab
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        when (homeUiState.activeTab) {
                            KanakTab.HOME -> HomeScreen(
                                uiState = homeUiState,
                                onAddExpenseClicked = { homeViewModel.openQuickEntry(triggerVoice = false) },
                                onVoiceEntryClicked = {
                                    homeViewModel.openQuickEntry(triggerVoice = true)
                                },
                                onViewAllTransactionsClicked = { homeViewModel.selectTab(KanakTab.TRANSACTIONS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            KanakTab.TRANSACTIONS -> TransactionsScreen(
                                uiState = transactionsUiState,
                                onSearchQueryChanged = transactionsViewModel::onSearchQueryChanged,
                                onClearSearch = transactionsViewModel::clearSearch,
                                onDateFilterSelected = transactionsViewModel::onDateFilterSelected,
                                onCategoryFilterSelected = transactionsViewModel::onCategoryFilterSelected,
                                onExpenseClicked = transactionsViewModel::onExpenseClickedForEdit,
                                onEditAmountChanged = transactionsViewModel::onEditAmountChanged,
                                onEditCategorySelected = transactionsViewModel::onEditCategorySelected,
                                onEditNoteChanged = transactionsViewModel::onEditNoteChanged,
                                onCloseEditSheet = transactionsViewModel::closeEditSheet,
                                onSaveEdit = transactionsViewModel::saveEdit,
                                onRequestDelete = transactionsViewModel::requestDelete,
                                onCancelDelete = transactionsViewModel::cancelDelete,
                                onConfirmDelete = transactionsViewModel::confirmDelete,
                                onClearUserMessage = transactionsViewModel::clearUserMessage,
                                modifier = Modifier.fillMaxSize(),
                            )
                            KanakTab.ANALYTICS -> AnalyticsScreen(
                                uiState = analyticsUiState,
                                onPreviousMonthClicked = analyticsViewModel::onPreviousMonthClicked,
                                onNextMonthClicked = analyticsViewModel::onNextMonthClicked,
                                modifier = Modifier.fillMaxSize()
                            )
                            KanakTab.SETTINGS -> SettingsScreen(
                                uiState = settingsUiState,
                                onThemeSelected = settingsViewModel::onThemeSelected,
                                onOpenCurrencyDialog = settingsViewModel::openCurrencyDialog,
                                onCloseCurrencyDialog = settingsViewModel::dismissCurrencyDialog,
                                onCurrencySelected = settingsViewModel::onCurrencySelected,
                                onExportBackup = settingsViewModel::exportBackup,
                                onManageBackupFiles = { showBackupFiles = true },
                                aiConnection = aiConnection,
                                autoBackupContent = {
                                    AutoBackupSection((application as KanakApp).autoBackupController)
                                },
                                onRestoreFileSelected = settingsViewModel::onRestoreFileSelected,
                                onConfirmRestore = settingsViewModel::confirmRestore,
                                onCancelRestore = settingsViewModel::cancelRestore,
                                onClearBackupError = settingsViewModel::clearBackupError,
                                onClearUserMessage = settingsViewModel::clearUserMessage,
                                onOpenSmartInbox = { showSmartInbox = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    QuickEntrySheet(
                        homeViewModel = homeViewModel,
                        entryViewModel = quickEntryViewModel,
                        aiConfigured = aiEndpoint != null
                    )

                    if (showBackupFiles) {
                        BackupFilesRoute(backupFilesViewModel) { showBackupFiles = false }
                    }
                    if (showSmartInbox) {
                        SmartInboxScreen(
                            state = smartInboxUiState,
                            onBack = { showSmartInbox = false },
                            onApprove = smartInboxViewModel::approve,
                            onDismiss = smartInboxViewModel::dismiss,
                            onTeach = smartInboxViewModel::teach,
                            onRemoveKnowledge = smartInboxViewModel::removeKnowledge,
                            onClearMessage = smartInboxViewModel::clearMessage,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    if (showExitDialog) {
                        ExitConfirmationDialog(
                            onConfirmExit = {
                                showExitDialog = false
                                (context as? Activity)?.finish()
                            },
                            onDismiss = { showExitDialog = false }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptSharedText(intent)
    }

    private fun acceptSharedText(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotEmpty()) sharedText.value = text
    }
}

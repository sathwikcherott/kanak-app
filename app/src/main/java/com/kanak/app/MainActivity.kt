package com.kanak.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.designsystem.theme.KanakAppTheme
import com.kanak.app.ui.dashboard.AnalyticsScreen
import com.kanak.app.ui.dashboard.HomeScreen
import com.kanak.app.ui.dashboard.HomeViewModel
import com.kanak.app.ui.dashboard.SettingsPlaceholderScreen
import com.kanak.app.ui.dashboard.TransactionsScreen
import com.kanak.app.ui.entry.QuickEntryScreen
import com.kanak.app.ui.entry.QuickEntryViewModel
import com.kanak.app.ui.navigation.KanakBottomNavigation
import com.kanak.app.ui.navigation.KanakTab

class MainActivity : ComponentActivity() {

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModel.Factory((application as KanakApp).expenseRepository)
    }

    private val quickEntryViewModel: QuickEntryViewModel by viewModels {
        val app = application as KanakApp
        QuickEntryViewModel.Factory(
            repository = app.expenseRepository,
            voiceInputRepository = app.voiceInputRepository
        )
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            val homeUiState by homeViewModel.uiState.collectAsState()
            val quickEntryUiState by quickEntryViewModel.uiState.collectAsState()

            KanakAppTheme(darkTheme = quickEntryUiState.themePreference == ThemePreference.DARK) {
                Scaffold(
                    bottomBar = {
                        KanakBottomNavigation(
                            activeTab = homeUiState.activeTab,
                            onTabSelected = homeViewModel::selectTab
                        )
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
                                    quickEntryViewModel.onMicTapped()
                                },
                                onViewAllTransactionsClicked = { homeViewModel.selectTab(KanakTab.TRANSACTIONS) },
                                modifier = Modifier.fillMaxSize()
                            )
                            KanakTab.TRANSACTIONS -> TransactionsScreen(
                                expenses = homeUiState.allExpenses,
                                currency = homeUiState.currency,
                                modifier = Modifier.fillMaxSize()
                            )
                            KanakTab.ANALYTICS -> AnalyticsScreen(
                                uiState = homeUiState,
                                modifier = Modifier.fillMaxSize()
                            )
                            KanakTab.SETTINGS -> SettingsPlaceholderScreen(
                                currency = homeUiState.currency,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }

                    if (homeUiState.showQuickEntry) {
                        ModalBottomSheet(
                            onDismissRequest = { homeViewModel.closeQuickEntry() },
                            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                        ) {
                            QuickEntryScreen(
                                uiState = quickEntryUiState,
                                onAmountChanged = quickEntryViewModel::onAmountChanged,
                                onCategorySelected = quickEntryViewModel::onCategorySelected,
                                onNoteChanged = quickEntryViewModel::onNoteChanged,
                                onMicTapped = quickEntryViewModel::onMicTapped,
                                onVoicePermissionDenied = quickEntryViewModel::onVoicePermissionDenied,
                                onSaveClicked = {
                                    quickEntryViewModel.saveExpense()
                                    homeViewModel.closeQuickEntry()
                                },
                                onClearUserMessage = quickEntryViewModel::clearUserMessage,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

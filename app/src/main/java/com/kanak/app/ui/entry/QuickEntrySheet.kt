package com.kanak.app.ui.entry

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.kanak.app.ui.dashboard.HomeViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Keep entry-state collection and recomposition inside the visible sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickEntrySheet(
    homeViewModel: HomeViewModel,
    entryViewModel: QuickEntryViewModel,
    aiConfigured: Boolean = false,
) {
    val sheetRequests = remember(homeViewModel) {
        homeViewModel.uiState.map { it.showQuickEntry to it.triggerVoiceOnEntry }.distinctUntilChanged()
    }
    val request by sheetRequests.collectAsState(
        initial = homeViewModel.uiState.value.let { it.showQuickEntry to it.triggerVoiceOnEntry }
    )
    if (!request.first) return

    val entryState by entryViewModel.uiState.collectAsState()
    // Read the latest value in the transition callback without recreating sheet state
    // as saving starts/stops or as input changes.
    val isSaving by rememberUpdatedState(entryState.isSaving || entryState.isCreatingCategory)
    val confirmSheetChange = remember {
        { target: SheetValue -> target != SheetValue.Hidden || !isSaving }
    }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = confirmSheetChange
    )
    var startVoice by remember { mutableStateOf(false) }
    LaunchedEffect(sheetState, request.second) {
        if (request.second) {
            snapshotFlow {
                sheetState.currentValue == SheetValue.Expanded &&
                    sheetState.targetValue == SheetValue.Expanded
            }.first { it }
            startVoice = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = {
            if (!entryViewModel.uiState.value.isSaving && !entryViewModel.uiState.value.isCreatingCategory) {
                entryViewModel.discardDraft()
                homeViewModel.closeQuickEntry()
            }
        },
        sheetState = sheetState
    ) {
        QuickEntryScreen(
            uiState = entryState,
            onAmountChanged = entryViewModel::onAmountChanged,
            onCategorySelected = entryViewModel::onCategorySelected,
            onNoteChanged = entryViewModel::onNoteChanged,
            onDateOptionSelected = entryViewModel::onDateOptionSelected,
            onDateChosenFromPicker = entryViewModel::onDateChosenFromPicker,
            onDismissDatePicker = entryViewModel::onDismissDatePicker,
            onMicTapped = entryViewModel::onMicTapped,
            onVoicePermissionDenied = entryViewModel::onVoicePermissionDenied,
            onSaveClicked = { entryViewModel.saveExpense { homeViewModel.closeQuickEntry() } },
            onClearUserMessage = entryViewModel::clearUserMessage,
            modifier = Modifier.fillMaxSize(),
            autoStartVoice = startVoice,
            onOpenNewCategory = entryViewModel::openNewCategoryDialog,
            onNewCategoryNameChanged = entryViewModel::onNewCategoryNameChanged,
            onCreateCategory = entryViewModel::createCategory,
            onCancelNewCategory = entryViewModel::cancelNewCategory,
            aiConfigured = aiConfigured,
            onRequestAiSuggestion = entryViewModel::requestAiSuggestion,
            onApplyAiSuggestion = entryViewModel::applyAiSuggestion,
            onDismissAiSuggestion = entryViewModel::clearAiSuggestion
        )
    }
}

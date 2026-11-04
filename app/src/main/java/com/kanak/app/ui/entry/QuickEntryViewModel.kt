package com.kanak.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanak.app.data.local.datastore.ThemePreference
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.CategorizationState
import com.kanak.app.model.EntrySource
import com.kanak.app.model.Expense
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.voice.VoiceDraftParser
import com.kanak.app.voice.VoiceInputError
import com.kanak.app.voice.VoiceInputRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class QuickEntryViewModel(
    private val expenseRepository: ExpenseRepository,
    private val voiceInputRepository: VoiceInputRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        QuickEntryUiState(
            voiceInputAvailable = voiceInputRepository?.isAvailable ?: true,
            themePreference = ThemePreference.DARK
        )
    )
    val uiState: StateFlow<QuickEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            expenseRepository.getDefaultCurrency().collectLatest { currency ->
                _uiState.update { it.copy(currency = currency) }
            }
        }
        viewModelScope.launch {
            expenseRepository.getThemePreference().collectLatest { theme ->
                _uiState.update { it.copy(themePreference = theme) }
            }
        }
        viewModelScope.launch {
            expenseRepository.getAllExpenses().collectLatest { expenses ->
                _uiState.update { it.copy(recentExpenses = expenses.take(5)) }
            }
        }
        voiceInputRepository?.let { repo ->
            viewModelScope.launch {
                repo.isListening.collectLatest { listening ->
                    _uiState.update { it.copy(isListening = listening) }
                }
            }
        }
    }

    fun onAmountChanged(amount: String) {
        val cleaned = amount.filter { it.isDigit() || (it == '.') || (it == ',') }
        val minor = Expense.parseToMinorUnits(cleaned, _uiState.value.currency)
        val error = if ((cleaned.isNotEmpty()) && (minor <= 0L)) "Amount must be greater than zero" else null
        _uiState.update { it.copy(amountText = cleaned, amountError = error, source = EntrySource.TYPED) }
    }

    fun onCategorySelected(category: ExpenseCategory?) {
        _uiState.update { current ->
            val newCategory = if (current.selectedCategory == category) null else category
            val newState = if (newCategory != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED
            current.copy(selectedCategory = newCategory, categorizationState = newState)
        }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { current ->
            if (current.categorizationState == CategorizationState.CONFIRMED) {
                // User has manually confirmed category choice -> preserve user's choice!
                current.copy(note = note, source = EntrySource.TYPED)
            } else {
                // Re-infer category dynamically as description is edited
                val inferredCategory = VoiceDraftParser.inferCategory(note)
                val newState = if (inferredCategory != null) CategorizationState.AUTO_CATEGORIZED else CategorizationState.UNCATEGORIZED
                current.copy(
                    note = note,
                    selectedCategory = inferredCategory,
                    categorizationState = newState,
                    source = EntrySource.TYPED
                )
            }
        }
    }

    fun onCurrencyChanged(currency: String) {
        _uiState.update { it.copy(currency = currency) }
        viewModelScope.launch {
            expenseRepository.setDefaultCurrency(currency)
        }
    }

    fun onThemePreferenceChanged(theme: ThemePreference) {
        _uiState.update { it.copy(themePreference = theme) }
        viewModelScope.launch {
            expenseRepository.setThemePreference(theme)
        }
    }

    fun onMicTapped() {
        val repo = voiceInputRepository
        if ((repo == null) || (!repo.isAvailable)) {
            _uiState.update {
                it.copy(userMessage = "Voice recognition is not available on this device")
            }
            return
        }

        if (_uiState.value.isListening) {
            repo.stopListening()
            _uiState.update { it.copy(isListening = false) }
            return
        }

        _uiState.update { it.copy(isListening = true) }

        repo.startListening(
            onResult = { transcript ->
                val draft = VoiceDraftParser.parseTranscript(transcript)
                _uiState.update { current ->
                    val minor = Expense.parseToMinorUnits(draft.amountText, current.currency)
                    val error = if (draft.amountText.isNotEmpty() && minor <= 0L) "Amount must be greater than zero" else null
                    current.copy(
                        amountText = if (draft.amountText.isNotBlank()) draft.amountText else current.amountText,
                        selectedCategory = draft.category, // Independent initialization; NEVER inherit stale categories!
                        note = if (draft.note.isNotBlank()) draft.note else current.note,
                        source = EntrySource.VOICE,
                        categorizationState = if (draft.category != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED,
                        isListening = false,
                        amountError = error,
                        userMessage = "Voice draft populated! Review and edit if needed."
                    )
                }
            },
            onError = { error ->
                val message = when (error) {
                    VoiceInputError.PermissionDenied -> "Microphone permission is required for voice input"
                    VoiceInputError.NoSpeechDetected -> "No speech detected. Please try speaking again."
                    VoiceInputError.ServiceUnavailable -> "Speech recognition service is currently unavailable"
                    is VoiceInputError.Unknown -> "Voice input error: ${error.message}"
                }
                _uiState.update {
                    it.copy(isListening = false, userMessage = message)
                }
            }
        )
    }

    fun onVoicePermissionDenied() {
        _uiState.update {
            it.copy(isListening = false, userMessage = "Microphone permission is required for voice input")
        }
    }

    fun saveExpense() {
        val currentState = _uiState.value
        if (!currentState.isValidInput) {
            _uiState.update {
                it.copy(amountError = if (it.amountText.isBlank()) "Amount is required" else "Amount must be greater than zero")
            }
            return
        }

        if (currentState.isSaving) return

        _uiState.update { it.copy(isSaving = true, amountError = null) }

        viewModelScope.launch {
            try {
                val expense = Expense(
                    amountMinor = currentState.amountMinor,
                    currency = currentState.currency,
                    occurredAt = System.currentTimeMillis(),
                    note = currentState.note.ifBlank { null },
                    category = currentState.selectedCategory,
                    source = currentState.source,
                    categorizationState = if (currentState.selectedCategory != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED
                )
                expenseRepository.saveExpense(expense)
                _uiState.update {
                    it.copy(
                        amountText = "",
                        note = "",
                        selectedCategory = null,
                        source = EntrySource.TYPED,
                        categorizationState = CategorizationState.UNCATEGORIZED,
                        isSaving = false,
                        amountError = null,
                        userMessage = "Expense recorded successfully!"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        userMessage = "Error saving expense: ${e.localizedMessage}"
                    )
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputRepository?.destroy()
    }

    class Factory(
        private val repository: ExpenseRepository,
        private val voiceInputRepository: VoiceInputRepository? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QuickEntryViewModel(repository, voiceInputRepository) as T
        }
    }
}

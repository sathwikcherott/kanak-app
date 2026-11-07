package com.kanak.app.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanak.app.ai.AiDescription
import com.kanak.app.ai.CloudAiCategorizer
import com.kanak.app.ai.model.AiCategorizationRequest
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
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import java.util.TimeZone

class QuickEntryViewModel(
    private val expenseRepository: ExpenseRepository,
    private val voiceInputRepository: VoiceInputRepository? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
    private val aiCategorizer: CloudAiCategorizer? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        QuickEntryUiState(
            voiceInputAvailable = voiceInputRepository?.isAvailable ?: true,
            themePreference = ThemePreference.DARK,
            selectedDateOption = ExpenseDateOption.TODAY,
            selectedDateMillis = timeProvider()
        )
    )
    val uiState: StateFlow<QuickEntryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            expenseRepository.getCategories().collectLatest { categories ->
                _uiState.update { it.copy(availableCategories = categories) }
            }
        }
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
        _uiState.update { current ->
            val error = if (amount.isNotBlank() && Expense.parseToMinorUnits(amount, current.currency) <= 0L) {
                "Enter a valid amount or calculation"
            } else {
                null
            }
            current.copy(amountText = amount, amountError = error, source = EntrySource.TYPED)
        }
    }

    private var aiRequest: Job? = null

    fun requestAiSuggestion(preview: String) {
        val categorizer = aiCategorizer ?: return
        val state = _uiState.value
        if (state.selectedCategory != null || state.isSaving || state.isListening ||
            state.isRequestingAi || preview != AiDescription.forSuggestion(state.note) ||
            preview.none(Char::isLetter)
        ) return

        _uiState.update { it.copy(isRequestingAi = true, aiSuggestion = null, aiMessage = null) }
        aiRequest = viewModelScope.launch {
            val result = try {
                categorizer.categorizeExpense(AiCategorizationRequest(note = preview))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            ensureActive()
            val suggestion = result.getOrNull()?.suggestedCategory?.takeIf { it in ExpenseCategory.entries }
            _uiState.update {
                it.copy(
                    isRequestingAi = false,
                    aiSuggestion = suggestion,
                    aiMessage = when {
                        result.isFailure -> "AI is unavailable. You can still save or pick a category."
                        suggestion == null -> "AI did not find a category."
                        else -> null
                    }
                )
            }
        }
    }

    fun applyAiSuggestion() {
        val state = _uiState.value
        if (state.selectedCategory == null && !state.isSaving && !state.isListening) {
            state.aiSuggestion?.let(::onCategorySelected)
        }
    }

    fun clearAiSuggestion() {
        aiRequest?.cancel()
        aiRequest = null
        _uiState.update {
            it.copy(isRequestingAi = false, aiSuggestion = null, aiMessage = null)
        }
    }

    fun openNewCategoryDialog() {
        clearAiSuggestion()
        if (!_uiState.value.isSaving) _uiState.update { it.copy(showNewCategoryDialog = true, newCategoryName = "", categoryError = null) }
    }

    fun onNewCategoryNameChanged(name: String) {
        if (!_uiState.value.isCreatingCategory) _uiState.update { it.copy(newCategoryName = name.take(200), categoryError = null) }
    }

    fun cancelNewCategory() {
        if (!_uiState.value.isCreatingCategory) _uiState.update { it.copy(showNewCategoryDialog = false, categoryError = null) }
    }

    fun createCategory() {
        val state = _uiState.value
        if (state.isCreatingCategory || !state.showNewCategoryDialog || state.isSaving) return
        _uiState.update { it.copy(isCreatingCategory = true, categoryError = null) }
        viewModelScope.launch {
            try {
                val category = expenseRepository.createCategory(state.newCategoryName)
                _uiState.update {
                    it.copy(selectedCategory = category, categorizationState = CategorizationState.CONFIRMED,
                        showNewCategoryDialog = false, newCategoryName = "",
                        availableCategories = if (it.availableCategories.any { c -> c.name == category.name }) it.availableCategories else it.availableCategories + category)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(categoryError = e.localizedMessage ?: "Could not create category") }
            } finally {
                _uiState.update { it.copy(isCreatingCategory = false) }
            }
        }
    }

    fun onCategorySelected(category: ExpenseCategory?) {
        clearAiSuggestion()
        _uiState.update { current ->
            val newCategory = if (current.selectedCategory == category) null else category
            val newState = if (newCategory != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED
            current.copy(selectedCategory = newCategory, categorizationState = newState)
        }
    }

    fun onNoteChanged(note: String) {
        clearAiSuggestion()
        _uiState.update { current ->
            if (current.categorizationState == CategorizationState.CONFIRMED) {
                current.copy(note = note, source = EntrySource.TYPED)
            } else {
                val inferredCategory = VoiceDraftParser.inferCategory(note, current.availableCategories.filter { it.isCustom })
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

    private var voiceDraftGeneration = 0

    fun onDateOptionSelected(option: ExpenseDateOption) {
        val now = timeProvider()
        when (option) {
            ExpenseDateOption.TODAY -> {
                _uiState.update {
                    it.copy(
                        selectedDateOption = ExpenseDateOption.TODAY,
                        selectedDateMillis = now,
                        showDatePicker = false,
                        dateError = null
                    )
                }
            }
            ExpenseDateOption.YESTERDAY -> {
                val yesterday = Instant.ofEpochMilli(now).atZone(timeZone.toZoneId())
                    .minusDays(1).toInstant().toEpochMilli()
                _uiState.update {
                    it.copy(
                        selectedDateOption = ExpenseDateOption.YESTERDAY,
                        selectedDateMillis = yesterday,
                        showDatePicker = false,
                        dateError = null
                    )
                }
            }
            ExpenseDateOption.CHOSEN_DATE -> {
                _uiState.update {
                    it.copy(showDatePicker = true, selectedDateMillis = ExpenseDateRules.resolveOccurredAt(it, now, timeZone))
                }
            }
        }
    }

    fun onDateChosenFromPicker(utcMillis: Long) {
        val convertedMillis = convertPickerUtcToLocalMillis(utcMillis, timeProvider, timeZone)
        val isFuture = isDateInFuture(convertedMillis, timeProvider, timeZone)

        if (isFuture) {
            _uiState.update {
                it.copy(
                    showDatePicker = false,
                    dateError = "Future dates are not allowed"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    selectedDateOption = ExpenseDateOption.CHOSEN_DATE,
                    selectedDateMillis = convertedMillis,
                    showDatePicker = false,
                    dateError = null
                )
            }
        }
    }

    fun onDismissDatePicker() {
        _uiState.update { it.copy(showDatePicker = false) }
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

        clearAiSuggestion()
        _uiState.update { it.copy(isListening = true) }

        val generation = ++voiceDraftGeneration
        repo.startListening(
            onResult = voiceResult@{ transcript ->
                if (generation != voiceDraftGeneration) return@voiceResult
                clearAiSuggestion()
                _uiState.update { current ->
                    val draft = VoiceDraftParser.parseTranscript(
                        transcript, current.availableCategories.filter { it.isCustom }, current.currency
                    )
                    val selectedCategory = current.selectedCategory?.takeIf { it.isCustom && current.categorizationState == CategorizationState.CONFIRMED } ?: draft.category
                    val minor = Expense.parseToMinorUnits(draft.amountText, current.currency)
                    val error = when {
                        draft.isAmbiguous -> "Multiple expenses detected. Please clarify amount."
                        draft.hasUncertainArithmetic -> "Uncertain calculation - please clarify amount."
                        draft.amountText.isBlank() -> "Amount is required"
                        minor <= 0L -> "Enter a valid amount or calculation"
                        else -> null
                    }
                    val userMsg = when {
                        draft.isAmbiguous -> "Multiple expenses detected in voice input. Please clarify the amount."
                        draft.hasUncertainArithmetic -> "Could not calculate spoken expression cleanly. Please clarify amount."
                        else -> "Voice draft populated! Review and edit if needed."
                    }
                    current.copy(
                        amountText = draft.amountText,
                        selectedCategory = selectedCategory,
                        note = if (draft.note.isNotBlank()) draft.note else current.note,
                        source = EntrySource.VOICE,
                        categorizationState = if (selectedCategory != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED,
                        isListening = false,
                        amountError = error,
                        userMessage = userMsg
                    )
                }
            },
            onError = voiceError@{ error ->
                if (generation != voiceDraftGeneration) return@voiceError
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

    fun saveExpense(onSaved: () -> Unit = {}) {
        val currentState = _uiState.value
        if (!currentState.isValidInput) {
            if (currentState.amountError == null && currentState.amountMinor <= 0L) {
                _uiState.update {
                    it.copy(amountError = if (it.amountText.isBlank()) "Amount is required" else "Enter a valid amount or calculation")
                }
            }
            return
        }

        val nowAtSave = timeProvider()
        val targetOccurredAt = ExpenseDateRules.resolveOccurredAt(currentState, nowAtSave, timeZone)

        if (isDateInFuture(targetOccurredAt, { nowAtSave }, timeZone)) {
            _uiState.update { it.copy(dateError = "Future dates are not allowed") }
            return
        }

        if (currentState.isSaving) return

        clearAiSuggestion()
        _uiState.update { it.copy(isSaving = true, amountError = null, dateError = null) }

        viewModelScope.launch {
            try {
                val expense = Expense(
                    amountMinor = currentState.amountMinor,
                    currency = currentState.currency,
                    occurredAt = targetOccurredAt,
                    note = currentState.note.ifBlank { null },
                    category = currentState.selectedCategory,
                    source = currentState.source,
                    categorizationState = if (currentState.selectedCategory != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED
                )
                expenseRepository.saveExpense(expense)
                val now = timeProvider()
                _uiState.update {
                    it.copy(
                        amountText = "",
                        note = "",
                        selectedCategory = null,
                        selectedDateOption = ExpenseDateOption.TODAY,
                        selectedDateMillis = now,
                        source = EntrySource.TYPED,
                        categorizationState = CategorizationState.UNCATEGORIZED,
                        isSaving = false,
                        amountError = null,
                        dateError = null,
                        userMessage = "Expense recorded successfully!"
                    )
                }
                onSaved()
            } catch (e: CancellationException) {
                throw e
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

    fun discardDraft() {
        if (_uiState.value.isSaving || _uiState.value.isCreatingCategory) return
        clearAiSuggestion()
        voiceDraftGeneration++
        voiceInputRepository?.stopListening()
        _uiState.update {
            QuickEntryUiState(
                currency = it.currency,
                availableCategories = it.availableCategories,
                themePreference = it.themePreference,
                recentExpenses = it.recentExpenses,
                voiceInputAvailable = it.voiceInputAvailable,
                selectedDateMillis = timeProvider()
            )
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        voiceInputRepository?.destroy()
    }

    companion object {
        fun convertPickerUtcToLocalMillis(
            utcMillis: Long,
            timeProvider: () -> Long = { System.currentTimeMillis() },
            timeZone: TimeZone = TimeZone.getDefault()
        ): Long = ExpenseDateRules.convertPickerUtcToLocalMillis(utcMillis, timeProvider, timeZone)

        fun localMillisToPickerUtc(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
            ExpenseDateRules.localMillisToPickerUtc(millis, timeZone)

        fun isPickerDateSelectable(
            utcMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault()
        ): Boolean = ExpenseDateRules.isPickerDateSelectable(utcMillis, nowMillis, timeZone)

        fun isDateInFuture(
            targetMillis: Long,
            timeProvider: () -> Long = { System.currentTimeMillis() },
            timeZone: TimeZone = TimeZone.getDefault()
        ): Boolean = ExpenseDateRules.isDateInFuture(targetMillis, timeProvider, timeZone)
    }

    class Factory(
        private val repository: ExpenseRepository,
        private val voiceInputRepository: VoiceInputRepository? = null,
        private val timeProvider: () -> Long = { System.currentTimeMillis() },
        private val timeZone: TimeZone = TimeZone.getDefault(),
        private val aiCategorizer: CloudAiCategorizer? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QuickEntryViewModel(repository, voiceInputRepository, timeProvider, timeZone, aiCategorizer) as T
        }
    }
}

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
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import java.util.TimeZone

class QuickEntryViewModel(
    private val expenseRepository: ExpenseRepository,
    private val voiceInputRepository: VoiceInputRepository? = null,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val timeZone: TimeZone = TimeZone.getDefault(),
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
                current.copy(note = note, source = EntrySource.TYPED)
            } else {
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
                    it.copy(showDatePicker = true, selectedDateMillis = resolveOccurredAt(it, now, timeZone))
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

        _uiState.update { it.copy(isListening = true) }

        val generation = ++voiceDraftGeneration
        repo.startListening(
            onResult = voiceResult@{ transcript ->
                if (generation != voiceDraftGeneration) return@voiceResult
                val draft = VoiceDraftParser.parseTranscript(transcript)
                _uiState.update { current ->
                    val minor = Expense.parseToMinorUnits(draft.amountText, current.currency)
                    val error = if (draft.amountText.isNotEmpty() && minor <= 0L) "Amount must be greater than zero" else null
                    current.copy(
                        amountText = if (draft.amountText.isNotBlank()) draft.amountText else current.amountText,
                        selectedCategory = draft.category,
                        note = if (draft.note.isNotBlank()) draft.note else current.note,
                        source = EntrySource.VOICE,
                        categorizationState = if (draft.category != null) CategorizationState.CONFIRMED else CategorizationState.UNCATEGORIZED,
                        isListening = false,
                        amountError = error,
                        userMessage = "Voice draft populated! Review and edit if needed."
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
            _uiState.update {
                it.copy(amountError = if (it.amountText.isBlank()) "Amount is required" else "Amount must be greater than zero")
            }
            return
        }

        val nowAtSave = timeProvider()
        val targetOccurredAt = resolveOccurredAt(currentState, nowAtSave, timeZone)

        if (isDateInFuture(targetOccurredAt, { nowAtSave }, timeZone)) {
            _uiState.update { it.copy(dateError = "Future dates are not allowed") }
            return
        }

        if (currentState.isSaving) return

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
        if (_uiState.value.isSaving) return
        voiceDraftGeneration++
        voiceInputRepository?.stopListening()
        _uiState.update {
            QuickEntryUiState(
                currency = it.currency,
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
        ): Long {
            val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            val now = Instant.ofEpochMilli(timeProvider()).atZone(timeZone.toZoneId())
            return date.atTime(now.toLocalTime()).atZone(timeZone.toZoneId()).toInstant().toEpochMilli()
        }

        fun localMillisToPickerUtc(millis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
            Instant.ofEpochMilli(millis).atZone(timeZone.toZoneId()).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        fun isPickerDateSelectable(
            utcMillis: Long,
            nowMillis: Long = System.currentTimeMillis(),
            timeZone: TimeZone = TimeZone.getDefault()
        ): Boolean = !Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()
            .isAfter(Instant.ofEpochMilli(nowMillis).atZone(timeZone.toZoneId()).toLocalDate())

        fun isDateInFuture(
            targetMillis: Long,
            timeProvider: () -> Long = { System.currentTimeMillis() },
            timeZone: TimeZone = TimeZone.getDefault()
        ): Boolean = Instant.ofEpochMilli(targetMillis).atZone(timeZone.toZoneId()).toLocalDate()
            .isAfter(Instant.ofEpochMilli(timeProvider()).atZone(timeZone.toZoneId()).toLocalDate())

        private fun resolveOccurredAt(state: QuickEntryUiState, nowMillis: Long, timeZone: TimeZone): Long {
            val zone = timeZone.toZoneId()
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val date = when (state.selectedDateOption) {
                ExpenseDateOption.TODAY -> return nowMillis
                // Relative shortcuts remain relative if an open draft crosses midnight.
                ExpenseDateOption.YESTERDAY -> now.toLocalDate().minusDays(1)
                ExpenseDateOption.CHOSEN_DATE -> Instant.ofEpochMilli(state.selectedDateMillis)
                    .atZone(zone).toLocalDate()
            }
            // java.time moves gap times forward and uses the earlier offset for overlaps.
            return date.atTime(now.toLocalTime()).atZone(zone).toInstant().toEpochMilli()
        }
    }

    class Factory(
        private val repository: ExpenseRepository,
        private val voiceInputRepository: VoiceInputRepository? = null,
        private val timeProvider: () -> Long = { System.currentTimeMillis() },
        private val timeZone: TimeZone = TimeZone.getDefault()
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return QuickEntryViewModel(repository, voiceInputRepository, timeProvider, timeZone) as T
        }
    }
}

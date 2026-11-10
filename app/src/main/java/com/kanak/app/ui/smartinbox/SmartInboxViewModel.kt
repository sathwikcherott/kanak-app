package com.kanak.app.ui.smartinbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kanak.app.data.repository.ExpenseRepository
import com.kanak.app.model.ExpenseCategory
import com.kanak.app.smartinbox.KnowledgeEntry
import com.kanak.app.smartinbox.PendingInboxTransaction
import com.kanak.app.smartinbox.SmartInboxRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class SmartInboxUiState(
    val pending: List<PendingInboxTransaction> = emptyList(), val knowledge: List<KnowledgeEntry> = emptyList(),
    val categories: List<ExpenseCategory> = ExpenseCategory.entries.toList(), val message: String? = null
)

class SmartInboxViewModel(private val inbox: SmartInboxRepository, expenses: ExpenseRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(SmartInboxUiState())
    val uiState: StateFlow<SmartInboxUiState> = _uiState.asStateFlow()
    init { viewModelScope.launch { combine(inbox.pending(), inbox.knowledge(), expenses.getCategories()) { pending, knowledge, categories -> SmartInboxUiState(pending, knowledge, categories) }.collect { _uiState.value = it } } }
    fun approve(id: Long, category: ExpenseCategory?) = viewModelScope.launch { inbox.approve(id, category); _uiState.value = _uiState.value.copy(message = "Added to transactions") }
    fun dismiss(id: Long) = viewModelScope.launch { inbox.dismiss(id) }
    fun teach(alias: String, label: String, category: ExpenseCategory?) = viewModelScope.launch { runCatching { inbox.teach(alias, label, category) }.onSuccess { _uiState.value = _uiState.value.copy(message = "Kanak will remember that") }.onFailure { _uiState.value = _uiState.value.copy(message = it.message) } }
    fun removeKnowledge(id: Long) = viewModelScope.launch { inbox.removeKnowledge(id) }
    fun importSharedText(text: String) = viewModelScope.launch {
        val imported = inbox.captureSharedText(text)
        _uiState.value = _uiState.value.copy(message = if (imported) "Shared payment added for review" else "Kanak could not read a confirmed payment from that share")
    }
    fun clearMessage() { _uiState.value = _uiState.value.copy(message = null) }
    class Factory(private val inbox: SmartInboxRepository, private val expenses: ExpenseRepository) : ViewModelProvider.Factory { @Suppress("UNCHECKED_CAST") override fun <T : ViewModel> create(modelClass: Class<T>) = SmartInboxViewModel(inbox, expenses) as T }
}

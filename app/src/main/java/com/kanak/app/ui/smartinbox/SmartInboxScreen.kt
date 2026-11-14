package com.kanak.app.ui.smartinbox

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kanak.app.designsystem.components.KanakCard
import com.kanak.app.model.ExpenseCategory

@Composable fun SmartInboxScreen(state: SmartInboxUiState, onBack: () -> Unit, onApprove: (Long, ExpenseCategory?) -> Unit, onDismiss: (Long) -> Unit, onTeach: (String, String, ExpenseCategory?) -> Unit, onRemoveKnowledge: (Long) -> Unit, onClearMessage: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current; var alias by remember { mutableStateOf("") }; var category by remember { mutableStateOf<ExpenseCategory?>(null) }
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Smart Inbox", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); TextButton(onClick = onBack) { Text("Done") } } }
        state.message?.let { message -> item { Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary); LaunchedEffect(message) { onClearMessage() } } }
        item { KanakCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Private notification access", fontWeight = FontWeight.SemiBold); Text("Kanak reads only confirmed Paytm debits after you enable Android notification access. Credits, refunds and ambiguous messages are ignored. Nothing is added until you approve it.", style = MaterialTheme.typography.bodySmall); Button(onClick = { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }) { Text("Open notification access") } } } }
        item { Text("PENDING REVIEW", style = MaterialTheme.typography.labelLarge) }
        if (state.pending.isEmpty()) item { Text("No transactions waiting for review.", style = MaterialTheme.typography.bodyMedium) }
        items(state.pending.size) { i -> val item = state.pending[i]; KanakCard { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(item.merchant, fontWeight = FontWeight.Bold); Text("₹${item.amountMinor / 100}.${(item.amountMinor % 100).toString().padStart(2, '0')}"); item.suggestionReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }; Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = { onApprove(item.id, item.suggestedCategory) }) { Text("Approve") }; OutlinedButton(onClick = { onDismiss(item.id) }) { Text("Ignore") } } } } }
        item { Text("TEACH KANAK", style = MaterialTheme.typography.labelLarge) }
        item { KanakCard { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("Tell Kanak a merchant rule", fontWeight = FontWeight.SemiBold); OutlinedTextField(alias, { alias = it }, label = { Text("Merchant or alias, e.g. BMTC") }, modifier = Modifier.fillMaxWidth()); CategoryPicker(category, state.categories) { category = it }; Button(onClick = { onTeach(alias, alias, category); alias = "" }, enabled = alias.isNotBlank()) { Text("Save locally") } } } }
        item { Text("KNOWLEDGE LIBRARY", style = MaterialTheme.typography.labelLarge) }
        items(state.knowledge.size) { i -> val item = state.knowledge[i]; ListItem(headlineContent = { Text(item.alias) }, supportingContent = { Text(item.category?.displayName ?: "No category") }, trailingContent = { TextButton(onClick = { onRemoveKnowledge(item.id) }) { Text("Remove") } }) }
    }
    }
}
@Composable private fun CategoryPicker(selected: ExpenseCategory?, categories: List<ExpenseCategory>, onSelect: (ExpenseCategory?) -> Unit) { var expanded by remember { mutableStateOf(false) }; Box { OutlinedButton(onClick = { expanded = true }) { Text(selected?.displayName ?: "Choose category") }; DropdownMenu(expanded, { expanded = false }) { categories.forEach { category -> DropdownMenuItem({ Text(category.displayName) }, onClick = { onSelect(category); expanded = false }) } } } }

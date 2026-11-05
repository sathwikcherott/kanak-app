package com.kanak.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun BackupFilesRoute(viewModel: BackupFilesViewModel, onDismiss: () -> Unit) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val chooser = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.chooseFolder(uri.toString())
    }
    BackupFilesDialog(
        state, onDismiss, { chooser.launch(state.folder?.let(android.net.Uri::parse)) },
        viewModel::refresh, viewModel::toggle, viewModel::selectOlder,
        viewModel::requestDeletion, viewModel::confirmDeletion, viewModel::cancelDeletion
    )
}

@Composable
fun BackupFilesDialog(
    state: BackupFilesUiState,
    onDismiss: () -> Unit,
    onChooseFolder: () -> Unit,
    onRefresh: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectOlder: () -> Unit,
    onRequestDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
    onCancelDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = { if (!state.busy) onDismiss() },
        title = { Text("Manage backup files") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Choose the folder where you saved your backups. Only verified Kanak backups in that folder are listed. The newest backup is always kept.", style = MaterialTheme.typography.bodySmall)
                Text(state.folderName ?: "No folder selected", style = MaterialTheme.typography.labelLarge)
                Row {
                    TextButton(onClick = onChooseFolder, enabled = !state.busy) { Text(if (state.folder == null) "Choose folder" else "Change folder") }
                    if (state.folder != null) TextButton(onClick = onRefresh, enabled = !state.busy) { Text("Refresh") }
                }
                if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                state.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (state.skipped > 0) Text("${state.skipped} invalid or unreadable backup(s) were left out.", style = MaterialTheme.typography.bodySmall)
                if (state.folder != null && state.files.isEmpty() && !state.busy && state.error == null) {
                    Text("No verified Kanak backups found here.", style = MaterialTheme.typography.bodySmall)
                }
                if (state.files.isNotEmpty()) {
                    TextButton(onClick = onSelectOlder, enabled = !state.busy && state.files.size > 3) { Text("Select older · keep newest 3") }
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        itemsIndexed(state.files, key = { _, file -> file.document.id }) { index, file ->
                            val selectable = index != 0 && file.document.canDelete && !state.busy
                            Row(modifier = Modifier.fillMaxWidth().clickable(enabled = selectable) { onToggle(file.document.id) }.padding(vertical = 6.dp)) {
                                Checkbox(checked = file.document.id in state.selected, onCheckedChange = if (selectable) ({ onToggle(file.document.id) }) else null, enabled = selectable)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(file.document.name, style = MaterialTheme.typography.bodySmall)
                                    Text("${dateFormat.format(Date(file.exportedAt))} · ${(file.bytes + 1023) / 1024} KB", style = MaterialTheme.typography.labelSmall)
                                    if (index == 0) Text("Newest · kept", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    else if (!file.document.canDelete) Text("Read-only", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onRequestDelete, enabled = !state.busy && state.selected.isNotEmpty(), colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Delete selected (${state.selected.size})")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.busy) { Text("Done") } }
    )
    if (state.confirmation.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { if (!state.busy) onCancelDelete() },
            title = { Text("Delete ${state.confirmation.size} backup file(s)?") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("These files will be permanently deleted. Your expenses in Kanak will stay unchanged.")
                    LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                        itemsIndexed(state.confirmation) { _, file -> Text(file.document.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 4.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onConfirmDelete, enabled = !state.busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(if (state.busy) "Deleting..." else "Delete files") } },
            dismissButton = { TextButton(onClick = onCancelDelete, enabled = !state.busy) { Text("Cancel") } }
        )
    }
}

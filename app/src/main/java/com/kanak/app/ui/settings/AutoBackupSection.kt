package com.kanak.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kanak.app.data.backup.AutoBackupController
import com.kanak.app.data.backup.AutoBackupFrequency
import com.kanak.app.data.backup.AutoBackupSettings
import com.kanak.app.designsystem.components.KanakCard
import com.kanak.app.designsystem.theme.KanakSpacing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun AutoBackupSection(controller: AutoBackupController) {
    val state by controller.settings.collectAsState(initial = AutoBackupSettings())
    val scope = rememberCoroutineScope()
    var updating by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val folderName by produceState<String?>(null, state.folder) {
        value = state.folder?.let {
            try {
                controller.folderName(it)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                "Selected folder"
            }
        }
    }

    fun update(failureMessage: String, action: suspend () -> Unit) {
        if (updating) return
        updating = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error = failureMessage
            } finally {
                updating = false
            }
        }
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) update("Could not access that folder. Choose a writable folder.") {
            controller.chooseFolder(uri.toString())
        }
    }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }

    KanakCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(KanakSpacing.small)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Automatic backups", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = state.enabled,
                    enabled = !updating && (state.enabled || state.folder != null),
                    onCheckedChange = { enabled ->
                        update("Could not update the backup schedule.") {
                            controller.setSchedule(enabled, state.frequency)
                        }
                    },
                    modifier = Modifier.semantics { contentDescription = "Automatic backups" }
                )
            }

            Text(
                "Keeps the three newest automatic backups in the chosen folder. Uses the same folder as Manage backup files. Manual exports stay separate. Files are unencrypted, and Android may delay scheduled runs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            TextButton(onClick = { folderPicker.launch(null) }, enabled = !updating) {
                Text(folderName?.let { "Folder: $it" } ?: "Choose backup folder")
            }

            Box {
                TextButton(onClick = { menuExpanded = true }, enabled = !updating) {
                    Text("Schedule: ${state.frequency.label}")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    AutoBackupFrequency.entries.forEach { frequency ->
                        DropdownMenuItem(
                            text = { Text(frequency.label) },
                            onClick = {
                                menuExpanded = false
                                update("Could not update the backup schedule.") {
                                    controller.setSchedule(state.enabled, frequency)
                                }
                            }
                        )
                    }
                }
            }

            state.lastBackupAt?.let {
                Text("Last automatic backup: ${dateFormat.format(Date(it))}", style = MaterialTheme.typography.bodySmall)
            }
            (error ?: state.warning)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

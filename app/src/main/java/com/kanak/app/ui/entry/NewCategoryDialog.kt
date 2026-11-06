package com.kanak.app.ui.entry

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun NewCategoryDialog(
    name: String,
    error: String?,
    busy: Boolean,
    onNameChanged: (String) -> Unit,
    onCreate: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() },
        title = { Text("New category") },
        text = {
            Column {
                OutlinedTextField(
                    value = name, onValueChange = onNameChanged, singleLine = true,
                    label = { Text("Category name") }, enabled = !busy, isError = error != null,
                    supportingText = { Text(error ?: "Up to 40 characters. Saved for future expenses.") }
                )
            }
        },
        confirmButton = { TextButton(onClick = onCreate, enabled = !busy && name.isNotBlank()) { Text(if (busy) "Creating..." else "Create category") } },
        dismissButton = { TextButton(onClick = onCancel, enabled = !busy) { Text("Cancel") } }
    )
}

package com.kanak.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kanak.app.designsystem.theme.KanakAppTheme
import com.kanak.app.ui.entry.QuickEntryScreen
import com.kanak.app.ui.entry.QuickEntryViewModel
import com.kanak.app.ui.theme.KanakTheme

class MainActivity : ComponentActivity() {
    private val viewModel: QuickEntryViewModel by viewModels {
        val app = application as KanakApp
        QuickEntryViewModel.Factory(
            repository = app.expenseRepository,
            voiceInputRepository = app.voiceInputRepository
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KanakAppTheme {
                val uiState by viewModel.uiState.collectAsState()
                QuickEntryScreen(
                    uiState = uiState,
                    onAmountChanged = viewModel::onAmountChanged,
                    onCategorySelected = viewModel::onCategorySelected,
                    onNoteChanged = viewModel::onNoteChanged,
                    onMicTapped = viewModel::onMicTapped,
                    onVoicePermissionDenied = viewModel::onVoicePermissionDenied,
                    onSaveClicked = viewModel::saveExpense,
                    onClearUserMessage = viewModel::clearUserMessage,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    KanakTheme {
        Greeting("Android")
    }
}

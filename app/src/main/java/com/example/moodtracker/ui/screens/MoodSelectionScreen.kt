package com.example.moodtracker.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.moodtracker.model.Mood // Not needed for placeholder
import com.example.moodtracker.viewmodel.MoodSelectionViewModel
import com.example.moodtracker.viewmodel.MoodSelectionUiState
import kotlinx.coroutines.delay

// MoodSelectionViewModelFactory remains the same
class MoodSelectionViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoodSelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoodSelectionViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSelectionScreen(
    onCloseScreen: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: MoodSelectionViewModel = viewModel(
        factory = MoodSelectionViewModelFactory(application)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.moodRecorded) {
        if (uiState.moodRecorded) {
            if (uiState.promptText.contains("Timeout")) {
                delay(2000)
            }
            onCloseScreen()
        }
    }

    BackHandler {
        viewModel.handleBackPress {
            // Closure is handled by the LaunchedEffect observing uiState.moodRecorded
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Your Mood", color = MaterialTheme.colorScheme.onPrimary) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top section: Prompt and time text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 24.dp)
            ) {
                Text(
                    text = uiState.promptText,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Text(
                    text = uiState.timeText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Main content area where mood grid will go
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp)
                    )
                } else {
                    // Placeholder content
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Mood selection grid will appear here",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        Button(
                            onClick = {
                                viewModel.onMoodSelected("N/A") {
                                    // Handled by LaunchedEffect
                                }
                            }
                        ) {
                            Text("Record 'N/A' and Close")
                        }
                    }
                }
            }

            // Timeout indicator at bottom
            if (!uiState.isLoading && !uiState.moodRecorded) {
                Text(
                    text = "Auto-closes in 60 seconds if no mood selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
        }
    }
}
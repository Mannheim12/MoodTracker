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
            if (uiState.promptText.contains("Timeout")) { // Keep timeout delay if needed
                delay(2000)
            }
            onCloseScreen()
        }
    }

    BackHandler {
        viewModel.handleBackPress {
            // Closure is handled by the LaunchedEffect observing uiState.moodRecorded
            // No explicit call to onCloseScreen() needed here as uiState.moodRecorded will trigger it
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else if (uiState.error != null) {
                Text(
                    text = "Error: ${uiState.error}",
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.promptText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = uiState.timeText,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Mood selection grid is temporarily unavailable.",
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 24.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        // Manually select a default mood like "N/A" or "Asleep" and close
                        viewModel.onMoodSelected("N/A") {
                            // onCloseScreen() // Handled by LaunchedEffect
                        }
                    }) {
                        Text("Record 'N/A' and Close")
                    }
                }
            }
        }
    }
}

// Removed MoodSelectionContent and MoodButton composables as they are not used in placeholder
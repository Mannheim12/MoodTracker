package com.example.moodtracker.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.moodtracker.model.Constants
import com.example.moodtracker.model.Mood
import com.example.moodtracker.viewmodel.MoodSelectionViewModel
import kotlinx.coroutines.delay

class MoodSelectionViewModelFactory(
    private val application: Application,
    private val hourId: String? // Add hourId to the factory
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoodSelectionViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MoodSelectionViewModel(application, hourId) as T // Pass hourId to ViewModel
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

@Composable
fun MoodButton(mood: Mood, onMoodClick: (String) -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = { onMoodClick(mood.name) },
        modifier = modifier.padding(2.dp),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(mood.getColor()),
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 4.dp,
            pressedElevation = 8.dp
        )
    ) {
        Text(
            text = mood.name,
            style = TextStyle(
                color = Color(mood.getTextColor()),
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            ),
            softWrap = true,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun MoodSelectionGrid(moods: List<Mood>, onMoodClick: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Create 3x3 grid
        for (row in 0..2) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0..2) {
                    val cellModifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)

                    if (row == 2 && col == 0) {
                        // Horizontal split cell at bottom left
                        Column(
                            modifier = cellModifier.padding(2.dp),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            // Angry button (top)
                            MoodButton(
                                mood = moods[6],
                                onMoodClick = onMoodClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(0.dp) // Remove default padding since Column already has it
                            )
                            // Fearful button (bottom)
                            MoodButton(
                                mood = moods[7],
                                onMoodClick = onMoodClick,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(0.dp) // Remove default padding since Column already has it
                            )
                        }
                    } else {
                        // Regular cells - calculate index directly
                        val moodIndex = if (row < 2) {
                            row * 3 + col  // Rows 0,1: simple calculation
                        } else {
                            // Row 2: only col 1,2 possible here
                            col + 7  // col 1 -> 8, col 2 -> 9
                        }
                        MoodButton(
                            mood = moods[moodIndex],
                            onMoodClick = onMoodClick,
                            modifier = cellModifier
                        )
                    }
                }
            }
        }

        // N/A button below grid
        val naMood = Constants.DEFAULT_MOODS.find { it.name == "N/A" }
        if (naMood != null) {
            MoodButton(
                mood = naMood,
                onMoodClick = onMoodClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)  // Half height
                    .padding(horizontal = 0.dp, vertical = 6.dp)
            )
        }

        // Asleep button below N/A
        val asleepMood = Constants.DEFAULT_MOODS.find { it.name == "Asleep" }
        if (asleepMood != null) {
            MoodButton(
                mood = asleepMood,
                onMoodClick = onMoodClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)  // Half height
                    .padding(horizontal = 0.dp, vertical = 6.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSelectionScreen(
    onCloseScreen: () -> Unit,
    hourId: String? = null // Accept optional hourId
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    // Pass the hourId to the factory
    val viewModel: MoodSelectionViewModel = viewModel(
        factory = MoodSelectionViewModelFactory(application, hourId)
    )
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.moodRecorded, uiState.closeScreen) {
        if (uiState.moodRecorded || uiState.closeScreen) {
            if (uiState.promptText.contains("Timeout")) {
                delay(2000)
            }
            onCloseScreen()
        }
    }

    BackHandler {
        viewModel.handleBackPress()
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top section: Prompt and time text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
            ) {
                Text(
                    text = uiState.promptText,
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
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

            // Main content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                } else if (uiState.moods.isEmpty()) {
                    Text(
                        text = "No moods configured.\nPlease add moods in settings.",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                } else {
                    // Use custom grid
                    MoodSelectionGrid(
                        moods = uiState.moods,
                        onMoodClick = { selectedMoodName ->
                            viewModel.onMoodSelected(selectedMoodName) {
                                // Callback handled by LaunchedEffect
                            }
                        }
                    )
                }
            }

            // Timeout indicator at bottom
            if (!uiState.isLoading && !uiState.moodRecorded) {
                Text(
                    text = "Auto-closes in 60 seconds if no mood selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        }
    }
}
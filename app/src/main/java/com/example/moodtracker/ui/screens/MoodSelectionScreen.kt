package com.example.moodtracker.ui.screens

import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.example.moodtracker.model.Mood //
import com.example.moodtracker.viewmodel.MoodSelectionViewModel //
import kotlinx.coroutines.delay

class MoodSelectionViewModelFactory(
    private val application: Application,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MoodSelectionViewModel::class.java)) { //
            @Suppress("UNCHECKED_CAST")
            return MoodSelectionViewModel(application) as T //
        }
        throw IllegalArgumentException("Unknown ViewModel class") //
    }
}

@Composable
fun MoodButton(mood: Mood, onMoodClick: (String) -> Unit) {
    Button(
        onClick = { onMoodClick(mood.name) }, //
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth() // Fill the width of the grid cell
            // Use aspectRatio to make the button square (or other desired ratio).
            // This will make its height proportional to its width (which is screenWidth / numColumns).
            // This is a common way to get responsive, consistently sized grid items.
            .aspectRatio(1f), // Makes the button a square. Adjust ratio (e.g., 1.5f for taller) if needed.
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(mood.getColor()), //
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp) // Keep some padding for text
    ) {
        Text(
            text = mood.name, //
            style = TextStyle( // Using a custom TextStyle for precise control
                color = Color(mood.getTextColor()), //
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Medium
            ),
            softWrap = true,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodSelectionScreen(
    onCloseScreen: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application //
    val viewModel: MoodSelectionViewModel = viewModel(
        factory = MoodSelectionViewModelFactory(application) //
    )
    val uiState by viewModel.uiState.collectAsState() //

    LaunchedEffect(uiState.moodRecorded) { //
        if (uiState.moodRecorded) { //
            if (uiState.promptText.contains("Timeout")) { //
                delay(2000) //
            }
            onCloseScreen() //
        }
    }

    BackHandler { //
        viewModel.handleBackPress { //
            // Closure handled by LaunchedEffect
        }
    }

    Scaffold(
        topBar = {
            TopAppBar( //
                title = { Text("Select Your Mood", color = MaterialTheme.colorScheme.onPrimary) }, //
                colors = TopAppBarDefaults.topAppBarColors( //
                    containerColor = MaterialTheme.colorScheme.primary //
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize() //
                .padding(paddingValues) // Apply padding from Scaffold
                .padding(horizontal = 16.dp, vertical = 8.dp), // Standard screen padding
            horizontalAlignment = Alignment.CenterHorizontally //
        ) {
            // Top section: Prompt and time text
            Column(
                horizontalAlignment = Alignment.CenterHorizontally, //
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp) //
            ) {
                Text(
                    text = uiState.promptText, //
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), //
                    textAlign = TextAlign.Center, //
                    modifier = Modifier.padding(bottom = 8.dp) //
                )
                Text(
                    text = uiState.timeText, //
                    style = MaterialTheme.typography.bodyLarge, //
                    textAlign = TextAlign.Center, //
                    color = MaterialTheme.colorScheme.onSurfaceVariant //
                )
            }

            // Main content area:
            Box(
                modifier = Modifier
                    .weight(1f) // Box takes available vertical weighted space
                    .fillMaxWidth(), //
                // Removed contentAlignment to use default (TopStart).
                // If LazyVerticalGrid's content is shorter than the Box, it will align TopStart.
                // The goal is for the grid items (buttons) to define the height.
            ) {
                if (uiState.isLoading) { //
                    CircularProgressIndicator(modifier = Modifier.size(48.dp).align(Alignment.Center)) //
                } else if (uiState.moods.isEmpty()) { //
                    Text(
                        text = "No moods configured.\nPlease add moods in settings.", //
                        style = MaterialTheme.typography.bodyLarge, //
                        textAlign = TextAlign.Center, //
                        color = MaterialTheme.colorScheme.onSurfaceVariant, //
                        modifier = Modifier.padding(16.dp).align(Alignment.Center) //
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), //
                        modifier = Modifier.fillMaxSize(), // Grid fills the Box
                        contentPadding = PaddingValues(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp), // This spacing applies between rows of items
                        horizontalArrangement = Arrangement.spacedBy(2.dp) // This spacing applies between columns of items
                    ) {
                        items(uiState.moods, key = { mood -> mood.name }) { mood -> //
                            MoodButton(
                                mood = mood,
                                onMoodClick = { selectedMoodName ->
                                    viewModel.onMoodSelected(selectedMoodName) { //
                                        // ...
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Timeout indicator at bottom
            if (!uiState.isLoading && !uiState.moodRecorded) { //
                Text(
                    text = "Auto-closes in 60 seconds if no mood selected", //
                    style = MaterialTheme.typography.bodySmall, //
                    color = MaterialTheme.colorScheme.onSurfaceVariant, //
                    textAlign = TextAlign.Center, //
                    modifier = Modifier.padding(vertical = 16.dp) //
                )
            }
        }
    }
}
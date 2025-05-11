package com.example.moodtracker.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.moodtracker.ui.Screen
import com.example.moodtracker.viewmodel.DebugInfoUiState
import com.example.moodtracker.viewmodel.DisplayMoodEntry
import com.example.moodtracker.viewmodel.HomeViewModel
import com.example.moodtracker.viewmodel.TrackingStatusUiState
import com.example.moodtracker.viewmodel.TodaysMoodsUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val trackingStatusState by homeViewModel.trackingStatusUiState.collectAsState()
    val todaysMoodsState by homeViewModel.todaysMoodsUiState.collectAsState()
    val debugInfoState by homeViewModel.debugInfoUiState.collectAsState()

    // Reload data when the screen becomes visible
    LaunchedEffect(Unit) {
        homeViewModel.loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Mood Tracker",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                actions = {
                    IconButton(onClick = { navController.navigate(Screen.Settings.route) }) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TrackingStatusCard(trackingStatusState)
            }
            item {
                TodaysMoodsCard(todaysMoodsState)
            }
            item {
                QuickActionsCard(
                    isActive = trackingStatusState.isActive,
                    onCheckNow = { homeViewModel.onCheckNowClicked() },
                    onToggleTracking = { homeViewModel.onToggleTrackingClicked() }
                )
            }
            item {
                MissedEntriesCard()
            }
            // Conditionally display Debug Info Card
            if (debugInfoState.isDebugModeEnabled) {
                item {
                    DebugInfoDisplayCard(debugInfoState)
                }
            }
        }
    }
}

@Composable
fun TrackingStatusCard(state: TrackingStatusUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (state.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (state.isActive) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                contentDescription = if (state.isActive) "Tracking Active" else "Tracking Inactive",
                tint = if (state.isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp)
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    text = if (state.isActive) "Tracking Active" else "Tracking Inactive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                if (state.isLoading) {
                    Text("Loading status...", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text(state.nextCheckMessage, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun TodaysMoodsCard(state: TodaysMoodsUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Today's Moods", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.moods.isEmpty()) {
                Text(state.message ?: "No moods recorded in the last 24 hours.", style = MaterialTheme.typography.bodyMedium)
            } else {
                MoodTimeline(moods = state.moods)
            }
        }
    }
}

@Composable
fun MoodTimeline(moods: List<DisplayMoodEntry>) {
    if (moods.isEmpty()) return

    val timelineHeight = (moods.size * 60).dp // Approximate height
    val pointRadius = 6.dp
    val lineWidth = 2.dp
    val textStartPadding = 16.dp

    Column {
        moods.forEachIndexed { index, mood ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 8.dp) // Spacing between entries
            ) {
                // Timeline line and dot
                Box(modifier = Modifier.width(40.dp).height(50.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // Vertical line
                        if (moods.size > 1) { // Only draw line if more than one point
                            val yStart = if (index == 0) center.y else 0f
                            val yEnd = if (index == moods.size - 1) center.y else size.height
                            drawLine(
                                color = Color.LightGray,
                                start = Offset(center.x, yStart),
                                end = Offset(center.x, yEnd),
                                strokeWidth = lineWidth.toPx()
                            )
                        }
                        // Dot
                        drawCircle(
                            color = Color(mood.moodColor),
                            radius = pointRadius.toPx(),
                            center = center
                        )
                        // Outline for dot if needed
                        drawCircle(
                            color = Color.DarkGray,
                            radius = pointRadius.toPx(),
                            center = center,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                Spacer(Modifier.width(textStartPadding))

                Column(modifier = Modifier.weight(1f)) {
                    Text(mood.timeRange, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(mood.moodName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}


@Composable
fun QuickActionsCard(isActive: Boolean, onCheckNow: () -> Unit, onToggleTracking: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Button(onClick = onCheckNow, enabled = isActive) {
                Text("Check Now")
            }
            Button(onClick = onToggleTracking) {
                Text(if (isActive) "Stop Tracking" else "Start Tracking")
            }
        }
    }
}

@Composable
fun MissedEntriesCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Missed Entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "List of missed entries and ability to fill them coming soon.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun DebugInfoDisplayCard(state: DebugInfoUiState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Debug Information",
                style = MaterialTheme.typography.titleLarge, // Make title a bit larger
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Config File Contents:",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp) // Limit height
                    .verticalScroll(rememberScrollState()) // Make content scrollable
                    .padding(all = 4.dp) // Padding inside the scrollable box
            ) {
                Text(
                    text = state.configContent,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace // Use monospace for better readability of config
                )
            }
        }
    }
}
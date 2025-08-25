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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.moodtracker.model.MoodEntry
import com.example.moodtracker.ui.Screen
import com.example.moodtracker.viewmodel.DebugInfoUiState
import com.example.moodtracker.viewmodel.DisplayMissedEntry
import com.example.moodtracker.viewmodel.DisplayMoodEntry
import com.example.moodtracker.viewmodel.HomeViewModel
import com.example.moodtracker.viewmodel.MissedEntriesUiState
import com.example.moodtracker.viewmodel.TrackingStatusUiState
import com.example.moodtracker.viewmodel.TodaysMoodsUiState
import com.example.moodtracker.viewmodel.MoodDebugInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    homeViewModel: HomeViewModel = viewModel()
) {
    val trackingStatusState by homeViewModel.trackingStatusUiState.collectAsState()
    val todaysMoodsState by homeViewModel.todaysMoodsUiState.collectAsState()
    val missedEntriesState by homeViewModel.missedEntriesUiState.collectAsState()
    val debugInfoState by homeViewModel.debugInfoUiState.collectAsState()

    // Reload data when the screen becomes visible
    LaunchedEffect(Unit) {
        homeViewModel.loadData()
    }

    // Show the database view dialog when the state is updated
    debugInfoState.databaseEntriesForDialog?.let { entries ->
        DatabaseViewDialog(
            entries = entries,
            onDismiss = { homeViewModel.onDismissDatabaseDialog() }
        )
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
                QuickActionsCard(
                    isActive = trackingStatusState.isActive,
                    onCheckNow = { homeViewModel.onCheckNowClicked() },
                    onToggleTracking = { homeViewModel.onToggleTrackingClicked() }
                )
            }
            item {
                MissedEntriesCard(
                    state = missedEntriesState,
                    onMissedEntryClick = { hourId -> homeViewModel.onMissedEntryClicked(hourId) }
                )
            }
            item {
                TodaysMoodsCard(todaysMoodsState)
            }
            // Conditionally display Debug Info Card
            if (debugInfoState.isDebugModeEnabled) {
                item {
                    DebugInfoDisplayCard(
                        state = debugInfoState,
                        onViewDatabase = { homeViewModel.onViewDatabaseClicked() },
                        onPopulateDatabase = { homeViewModel.onPopulateDatabaseClicked() }
                    )
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

    val pointRadius = 6.dp
    val lineWidth = 2.dp

    Column {
        moods.forEachIndexed { index, mood ->
            Row(
                verticalAlignment = Alignment.CenterVertically
                // No padding here to eliminate gaps between timeline segments
            ) {
                // Timeline line and dot - fixed size that matches text height
                Box(modifier = Modifier.width(40.dp).height(24.dp), contentAlignment = Alignment.Center) {
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
                        // Outline for dot
                        drawCircle(
                            color = Color.DarkGray,
                            radius = pointRadius.toPx(),
                            center = center,
                            style = Stroke(width = 1.dp.toPx())
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                // Fixed-width box for time to ensure alignment
                Box(
                    modifier = Modifier.width(90.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = mood.timeRange,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text(
                    text = ": ${mood.moodName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
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
fun MissedEntriesCard(state: MissedEntriesUiState, onMissedEntryClick: (String) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Missed Entries", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (state.isLoading) {
                CircularProgressIndicator()
            } else if (state.missedEntries.isEmpty()) {
                Text(
                    "No missed entries to show.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.missedEntries.forEach { missedEntry ->
                        Button(
                            onClick = { onMissedEntryClick(missedEntry.hourId) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(missedEntry.displayText)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DebugInfoDisplayCard(
    state: DebugInfoUiState,
    onViewDatabase: () -> Unit,
    onPopulateDatabase: () -> Unit
) {
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
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(16.dp))

            // Worker Status Section
            DebugSection(title = "WORKER STATUS") {
                DebugRow("Next Check", state.nextCheckTime)
                DebugRow("Time Until Next", state.timeUntilNextCheck)
                DebugRow("Worker Status", state.workerStatus)
                DebugRow("Last Check", state.lastCheckTime)
                DebugRow("Time Since Last", state.timeSinceLastCheck)
                DebugRow("Tracking Enabled", if (state.trackingEnabled) "YES" else "NO")
            }

            Spacer(Modifier.height(12.dp))

            // Permissions Status Section
            DebugSection(title = "PERMISSIONS STATUS") {
                state.permissionsStatus.forEach { (permission, granted) ->
                    DebugRow(permission, if (granted) "GRANTED" else "DENIED")
                }
                DebugRow("Battery Optimization Exempt", if (state.batteryOptimizationExempt) "YES" else "NO")
            }

            Spacer(Modifier.height(12.dp))

            // Database Status Section
            DebugSection(title = "DATABASE STATUS") {
                DebugRow("Total Entries", state.databaseEntryCount.toString())
                DebugWideRow("Latest Entry", state.latestEntry)
            }

            Spacer(Modifier.height(12.dp))

            // New Database Actions Section
            DebugSection(title = "DATABASE ACTIONS") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = onViewDatabase) {
                        Text("View Database")
                    }
                    Button(onClick = onPopulateDatabase) {
                        Text("Populate (If Empty)")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Configuration Section
            DebugSection(title = "CONFIGURATION") {
                DebugRow("Min Interval", "${state.minInterval} minutes")
                DebugRow("Max Interval", "${state.maxInterval} minutes")
                DebugRow("Time Format", state.timeFormat)
                DebugRow("App Theme", state.appTheme)
                DebugRow("Auto-export", state.autoExportFrequency)
                DebugRow("Auto-sleep Start", state.autoSleepStartHour)
                DebugRow("Auto-sleep End", state.autoSleepEndHour)
            }

            Spacer(Modifier.height(12.dp))

            // Moods Section
            DebugSection(title = "MOODS (${state.moods.size})") {
                state.moods.forEach { mood ->
                    DebugMoodRow(mood)
                }
            }
        }
    }
}

@Composable
fun DebugSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
fun DebugRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End
        )
    }
}

@Composable
fun DebugWideRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 16.dp, top = 2.dp)
        )
    }
}

@Composable
fun DebugMoodRow(mood: MoodDebugInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .padding(end = 4.dp)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawCircle(
                        color = try { Color(android.graphics.Color.parseColor(mood.colorHex)) } catch (e: Exception) { Color.Gray },
                        radius = size.minDimension / 2
                    )
                }
            }
            Text(
                text = mood.name,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = mood.properties,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
fun DatabaseViewDialog(entries: List<MoodEntry>, onDismiss: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Database Entries (${entries.size})") },
        text = {
            LazyColumn {
                items(entries) { entry ->
                    val formattedDate = dateFormat.format(Date(entry.timestamp))
                    Text(
                        text = "${entry.id} | ${entry.moodName} | $formattedDate",
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
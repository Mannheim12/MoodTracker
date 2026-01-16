package com.example.moodtracker.ui.screens

import android.annotation.SuppressLint
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.Context
import android.os.PowerManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.example.moodtracker.util.ConfigManager
import com.example.moodtracker.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.mutableIntStateOf
import java.util.Calendar
import java.util.Locale

@SuppressLint("StringFormatInvalid") // For R.string. ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    settingsViewModel: SettingsViewModel = viewModel() // Obtain ViewModel
) {
    val uiState by settingsViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAutoExportDialog by rememberSaveable { mutableStateOf(false) }
    var showTimelineHoursDialog by rememberSaveable { mutableStateOf(false) }
    var showTimeFormatDialog by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    var showResetConfigDialog by rememberSaveable { mutableStateOf(false) }
    var showResetDatabaseDialog by rememberSaveable { mutableStateOf(false) }
    var showAutoSleepDialog by rememberSaveable { mutableStateOf(false) }

    // File Launchers
    val exportConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.exportConfig(it) { _, message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    )
    val importConfigLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.importConfig(it) { _, message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    )
    val exportDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.exportData(it) { _, message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    )
    val importDataLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                settingsViewModel.importData(it) { _, message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
            }
        }
    )

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            settingsViewModel.snackbarMessageShown() // Clear the message after showing
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 8.dp) // Add some horizontal padding for list items
        ) {
            // Battery Optimization Note
            item {
                val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                val isIgnoringOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName)
                if (!isIgnoringOptimizations) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Text(
                            text = "For best results, exclude this app from battery optimization in your phone's settings.",
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
            // Mood & Tracking Section
            item { SettingsSectionHeader("Mood & Tracking") }
            item {
                SettingsItem(
                    title = "Customize Mood Selector",
                    subtitle = "Edit mood names, colors, and layout (Not Implemented)",
                    onClick = {
                        scope.launch { snackbarHostState.showSnackbar("Mood customization coming soon!") }
                        // navController.navigate("mood_customization_screen_route") // Future navigation
                    }
                )
            }
            item {
                val enabledCount = uiState.autoSleepGrid.count { it.value }
                SettingsItem(
                    title = "Auto-sleep Hours",
                    subtitle = if (enabledCount == 0) "Not configured" else "$enabledCount hour${if (enabledCount == 1) "" else "s"} enabled",
                    onClick = { showAutoSleepDialog = true }
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { importConfigLauncher.launch(arrayOf("application/json")) }) {
                        Text("Import Config")
                    }
                    Button(onClick = { exportConfigLauncher.launch("mood_tracker_config.json") }) {
                        Text("Export Config")
                    }
                }
            }
            item {
                SettingsActionItem(
                    title = "Reset Config to Default",
                    onClick = { showResetConfigDialog = true },
                    isDestructive = true
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // Data Management Section
            item { SettingsSectionHeader("Data Management") }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(onClick = { importDataLauncher.launch(arrayOf("*/*")) }) { // Or "text/csv"
                        Text("Import Data")
                    }
                    Button(onClick = {
                        scope.launch {
                            val filename = settingsViewModel.generateDataExportFilename()
                            exportDataLauncher.launch(filename)
                        }
                    }) {
                        Text("Export Data")
                    }
                }
            }
            item {
                SettingsItem(
                    title = "Auto-export",
                    subtitle = formatAutoExportFrequency(uiState.autoExportFrequency),
                    onClick = { showAutoExportDialog = true }
                )
            }
            item {
                SettingsActionItem(
                    title = "Reset Database (Delete All Mood Entries)",
                    onClick = { showResetDatabaseDialog = true },
                    isDestructive = true
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // Appearance Section
            item { SettingsSectionHeader("Appearance") }
            item {
                SettingsItem(
                    title = "Timeline Hours",
                    subtitle = "${uiState.timelineHours} hours",
                    onClick = { showTimelineHoursDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = "Time Format",
                    subtitle = formatTimeFormat(uiState.timeFormat),
                    onClick = { showTimeFormatDialog = true }
                )
            }
            item {
                SettingsItem(
                    title = "Theme",
                    subtitle = uiState.appTheme.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() },
                    onClick = { showThemeDialog = true }
                )
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // Advanced Section
            item { SettingsSectionHeader("Advanced") }
            item {
                SettingsSwitchItem(
                    title = "Debug Mode",
                    subtitle = if (uiState.debugModeEnabled) "Enabled" else "Disabled",
                    checked = uiState.debugModeEnabled,
                    onCheckedChange = { settingsViewModel.updateDebugMode(it) }
                )
            }
            item { Spacer(modifier = Modifier.height(24.dp)) } // Extra space at bottom
        }
    }

    // Dialogs
    if (showTimelineHoursDialog) {
        SliderPickerDialog(
            title = "Timeline Hours (12-96)",
            currentValue = uiState.timelineHours,
            minValue = 12,
            maxValue = 96,
            step = 12,
            onDismiss = { showTimelineHoursDialog = false },
            onConfirm = { newValue ->
                settingsViewModel.updateTimelineHours(newValue)
                showTimelineHoursDialog = false
            }
        )
    }

    if (showAutoExportDialog) {
        SingleChoiceDialog(
            title = "Auto-export Frequency",
            options = mapOf(
                ConfigManager.AutoExportFrequency.OFF to "Off",
                ConfigManager.AutoExportFrequency.DAILY to "Daily (Not Implemented)",
                ConfigManager.AutoExportFrequency.WEEKLY_SUNDAY to "Every Sunday",
                ConfigManager.AutoExportFrequency.WEEKLY_MONDAY to "Every Monday (Not Implemented)",
                ConfigManager.AutoExportFrequency.MONTHLY_FIRST to "First of Month (Not Implemented)"
            ),
            currentSelection = uiState.autoExportFrequency,
            onDismiss = { showAutoExportDialog = false },
            onConfirm = { selection ->
                settingsViewModel.updateAutoExportFrequency(selection)
                showAutoExportDialog = false
                if (selection != ConfigManager.AutoExportFrequency.OFF && selection != ConfigManager.AutoExportFrequency.WEEKLY_SUNDAY) {
                    scope.launch { snackbarHostState.showSnackbar("Selected auto-export option not fully implemented yet.")}
                }
            }
        )
    }

    if (showTimeFormatDialog) {
        SingleChoiceDialog(
            title = "Time Format",
            options = mapOf(
                ConfigManager.TimeFormat.SYSTEM_DEFAULT to "System Default",
                ConfigManager.TimeFormat.H12 to "12-hour",
                ConfigManager.TimeFormat.H24 to "24-hour"
            ),
            currentSelection = uiState.timeFormat,
            onDismiss = { showTimeFormatDialog = false },
            onConfirm = { selection ->
                settingsViewModel.updateTimeFormat(selection)
                showTimeFormatDialog = false
            }
        )
    }

    if (showThemeDialog) {
        SingleChoiceDialog(
            title = "App Theme",
            options = mapOf(
                ConfigManager.AppTheme.SYSTEM to "System Default",
                ConfigManager.AppTheme.LIGHT to "Light",
                ConfigManager.AppTheme.DARK to "Dark"
            ),
            currentSelection = uiState.appTheme,
            onDismiss = { showThemeDialog = false },
            onConfirm = { selection ->
                settingsViewModel.updateAppTheme(selection)
                showThemeDialog = false
                // Snackbar for restart is handled by LaunchedEffect on uiState.snackbarMessage
            }
        )
    }
    if (showResetConfigDialog) {
        ConfirmActionDialog(
            title = "Reset Configuration?",
            text = "Are you sure you want to reset all settings to their default values? This cannot be undone.",
            onConfirm = {
                settingsViewModel.resetConfigToDefault { _, message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
                showResetConfigDialog = false
            },
            onDismiss = { showResetConfigDialog = false }
        )
    }

    if (showResetDatabaseDialog) {
        ConfirmActionDialog(
            title = "Reset Database?",
            text = "Are you sure you want to delete all mood entries? This action cannot be undone.",
            onConfirm = {
                settingsViewModel.resetDatabase { _, message ->
                    scope.launch { snackbarHostState.showSnackbar(message) }
                }
                showResetDatabaseDialog = false
            },
            onDismiss = { showResetDatabaseDialog = false }
        )
    }

    if (showAutoSleepDialog) {
        AutoSleepGridDialog(
            autoSleepGrid = uiState.autoSleepGrid,
            onDismiss = { showAutoSleepDialog = false },
            onToggleCell = { dayOfWeek, hour ->
                settingsViewModel.toggleAutoSleepCell(dayOfWeek, hour)
            }
        )
    }
}

// Helper Composables for Settings Items

@Composable
fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 8.dp) // Adjusted padding
    )
}

@Composable
fun SettingsItem(title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
fun SettingsSwitchItem(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        modifier = Modifier.clickable { onCheckedChange(!checked) }
    )
}

@Composable
fun SettingsActionItem(title: String, onClick: () -> Unit, isDestructive: Boolean = false) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        colors = if (isDestructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
        else ButtonDefaults.textButtonColors()
    ) {
        Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}


@Composable
fun NumberPickerDialog(
    title: String,
    currentValue: Int,
    range: IntRange,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var tempStringValue by rememberSaveable { mutableStateOf(currentValue.toString()) }
    val (isValidInput, parsedNum) = remember(tempStringValue) {
        val num = tempStringValue.toIntOrNull()
        if (num != null && num in range) {
            Pair(true, num)
        } else {
            Pair(false, null)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                OutlinedTextField(
                    value = tempStringValue,
                    onValueChange = { newValue ->
                        // Allow any text input, validation happens for confirm button enablement
                        tempStringValue = newValue
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Value (${range.first} - ${range.last})") },
                    isError = tempStringValue.isNotEmpty() && !isValidInput // Show error if input is not empty and not valid
                )
                if (tempStringValue.isNotEmpty() && !isValidInput) {
                    Text(
                        "Please enter a number between ${range.first} and ${range.last}.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    parsedNum?.let { onConfirm(it) } // Only call confirm if parsedNum is not null
                },
                enabled = isValidInput // Enable button only if input is valid
            ) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SliderPickerDialog(
    title: String,
    currentValue: Int,
    minValue: Int,
    maxValue: Int,
    step: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selectedValue by rememberSaveable { mutableIntStateOf(currentValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$selectedValue hours",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
                Slider(
                    value = selectedValue.toFloat(),
                    onValueChange = { selectedValue = (it / step).toInt() * step },
                    valueRange = minValue.toFloat()..maxValue.toFloat(),
                    steps = ((maxValue - minValue) / step) - 1,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("$minValue", style = MaterialTheme.typography.bodySmall)
                    Text("$maxValue", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedValue) }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SingleChoiceDialog(
    title: String,
    options: Map<String, String>, // Key: internal value, Value: display name
    currentSelection: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedOption by rememberSaveable { mutableStateOf(currentSelection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (key, displayName) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { selectedOption = key }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = (key == selectedOption),
                            onClick = { selectedOption = key }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(displayName)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(selectedOption) }) { Text("Confirm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonText: String = "Confirm",
    dismissButtonText: String = "Cancel"
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text(confirmButtonText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissButtonText) }
        }
    )
}


// Helper functions to format display values
fun formatAutoExportFrequency(key: String): String {
    return when (key) {
        ConfigManager.AutoExportFrequency.OFF -> "Off"
        ConfigManager.AutoExportFrequency.DAILY -> "Daily (Not Implemented)"
        ConfigManager.AutoExportFrequency.WEEKLY_SUNDAY -> "Every Sunday"
        ConfigManager.AutoExportFrequency.WEEKLY_MONDAY -> "Every Monday (Not Implemented)"
        ConfigManager.AutoExportFrequency.MONTHLY_FIRST -> "First of Month (Not Implemented)"
        else -> key
    }
}

fun formatTimeFormat(key: String): String {
    return when (key) {
        ConfigManager.TimeFormat.H12 -> "12-hour"
        ConfigManager.TimeFormat.H24 -> "24-hour"
        ConfigManager.TimeFormat.SYSTEM_DEFAULT -> "System Default"
        else -> key
    }
}

@Composable
fun AutoSleepGridDialog(
    autoSleepGrid: Map<String, Boolean>,
    onDismiss: () -> Unit,
    onToggleCell: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    val configManager = ConfigManager(context)
    val config = configManager.loadConfig()
    val use24Hour = config.timeFormat == ConfigManager.TimeFormat.H24

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-sleep Hours") },
        text = {
            Column {
                Text(
                    "Tap cells to toggle auto-sleep. Missed hours will be auto-recorded as Asleep.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { day ->
                                Text(
                                    day,
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    items(24) { hour ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(
                                Calendar.SUNDAY,
                                Calendar.MONDAY,
                                Calendar.TUESDAY,
                                Calendar.WEDNESDAY,
                                Calendar.THURSDAY,
                                Calendar.FRIDAY,
                                Calendar.SATURDAY
                            ).forEach { dayOfWeek ->
                                val key = "$dayOfWeek-$hour"
                                val isEnabled = autoSleepGrid[key] == true

                                val hourLabel = if (use24Hour) {
                                    String.format(Locale.US, "%02d", hour)
                                } else {
                                    val period = if (hour < 12) "AM" else "PM"
                                    val displayHour = when (hour) {
                                        0 -> 12
                                        in 1..12 -> hour
                                        else -> hour - 12
                                    }
                                    "$displayHour$period"
                                }

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp)
                                        .clickable { onToggleCell(dayOfWeek, hour) },
                                    colors = androidx.compose.material3.CardDefaults.cardColors(
                                        containerColor = if (isEnabled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            hourLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            color = if (isEnabled)
                                                MaterialTheme.colorScheme.onPrimary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
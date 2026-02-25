package com.snaky.poker.cev.ui.view

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.ui.model.MainViewModel
import com.snaky.poker.cev.ui.model.ProcessingStats
import com.snaky.poker.cev.ui.UpdateBanner
import com.snaky.poker.cev.ui.config.ConfigurationManager
import com.snaky.poker.cev.ui.config.HistorySource

@Composable
fun MainView(viewModel: MainViewModel) {
    var isSettingsOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Spin-cEV Calculator") },
                actions = {

                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (isSettingsOpen) {
                val currentConfig = ConfigurationManager.configuration.copy()
                SourcesEditor(onClose = {
                    isSettingsOpen = false
                    if (currentConfig != ConfigurationManager.configuration) viewModel.clearResults()
                })
            } else {
                MainContent(
                    viewModel = viewModel,
                    onOpenSettings = { isSettingsOpen = true }
                )
            }
        }
    }
}


@Composable
private fun MainContent(viewModel: MainViewModel, onOpenSettings: () -> Unit) {

    val activeSources = ConfigurationManager.configuration.sources.filter { it.isActive }

    Column(modifier = Modifier.padding(16.dp)) {
        UpdateBanner()

        Spacer(Modifier.height(8.dp))

        SourceSummaryBar(
            isCalculating = viewModel.isCalculating,
            onOpenSettings = onOpenSettings
        )

        Spacer(Modifier.height(16.dp))
        ActionArea(viewModel, activeSources)


        if(viewModel.getState() == ActionState.FINISHED) {
            Spacer(Modifier.height(24.dp))

            // --- Results Table ---
            StatsTable(
                rows = viewModel.statsRows,
                availableFormats = viewModel.availableFormats,
                selectedStack = viewModel.selectedStackFilter,
                onStackSelected = { viewModel.selectedStackFilter = it }
            )
        }
    }
}

@Composable
private fun ActionArea(
    viewModel: MainViewModel,
    activeSources: List<HistorySource>
) {
    AnimatedContent(
        targetState = viewModel.getState(),
        transitionSpec = {
            // Animation logic: New content slides up while old content fades out
            (slideInVertically { it } + fadeIn(tween(300)))
                .togetherWith(slideOutVertically { -it } + fadeOut(tween(200)))
        },
        label = "ActionAreaTransition"
    ) {
        when (it) {
            ActionState.CALCULATING -> {
                ProcessingView(viewModel)
            }
            ActionState.FINISHED -> ProcessingFeedback(viewModel.processingStats)
            ActionState.IDLE -> {
                Button(
                    onClick = { viewModel.runCalculation() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !activeSources.isEmpty()
                ) {
                    Text("Process history files")
                }
            }
        }
    }
}

@Composable
private fun ProcessingView(viewModel: MainViewModel) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.height(8.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Processing spins: ",
                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold)
            )
            Text(
                text = "${viewModel.currentSpinCount}",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.primary // Rappel de ta couleur violette
            )
            Surface(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(1.dp)
                    .height(16.dp),
                color = Color.Gray.copy(alpha = 0.5f)
            ) {}

            TextButton(
                onClick = { viewModel.stopCalculation() },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = RoundedCornerShape(4.dp),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)) // Un petit bord pour le définir
            ) {
                Icon(
                    Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.Red
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "STOP",
                    color = Color.Red,
                    style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
private fun SourceSummaryBar(
    isCalculating: Boolean,
    onOpenSettings: () -> Unit
) {
    val allActive = ConfigurationManager.configuration.sources.filter { it.isActive }
    val limit = 3

    val badgeText = if (allActive.size <= 1) "SOURCE" else "SOURCES"

    val displayNames = allActive.take(limit).map {
        if (it.name.length > 30) it.name.take(27) + "..." else it.name
    }

    val textToShow = when {
        allActive.isEmpty() -> "No active source"
        allActive.size <= limit -> displayNames.joinToString("  •  ")
        else -> {
            val extraCount = allActive.size - limit
            val extraText = if (extraCount == 1) "another" else "$extraCount others"
            "${displayNames.joinToString("  •  ")} (+$extraText)"
        }
    }

    Card(
        elevation = 0.dp,
        backgroundColor = MaterialTheme.colors.surface.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.3f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(
                enabled = !isCalculating,
                onClick = onOpenSettings
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Badge SOURCE(S)
            Surface(
                color = MaterialTheme.colors.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.overline.copy(fontWeight = FontWeight.Bold),
                    color = if (isCalculating) Color.Gray else MaterialTheme.colors.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(Modifier.width(16.dp))

            // list of active sources
            Text(
                text = textToShow,
                style = MaterialTheme.typography.body2.copy(
                    fontWeight = FontWeight.Medium,
                    color = if (isCalculating) Color.Gray else Color.Unspecified
                ),
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!isCalculating) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ProcessingFeedback(stats: ProcessingStats) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Valid Spins - Success Green
        BadgeText(
            count = stats.validSpinCount,
            label = "valid spins",
            color = Color(0xFF43A047) // green
        )

        if (stats.incompleteSpinCount> 0) {
            Spacer(Modifier.width(16.dp))
            BadgeText(
                count = stats.incompleteSpinCount,
                label = "incomplete spins",
                color = Color(0xFFFFB300) // Amber/Orange
            )
        }

        if (stats.duplicateHandCount > 0) {
            Spacer(Modifier.width(16.dp))
            BadgeText(
                count = stats.duplicateHandCount,
                label = "duplicate hands",
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun BadgeText(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, shape = CircleShape)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count $label",
            style = MaterialTheme.typography.caption,
            color = color.copy(alpha = 0.9f),
            fontWeight = FontWeight.Bold
        )
    }
}

private enum class ActionState {
    IDLE,       // Show "Process" button
    CALCULATING, // Show Progress bar + Stop button
    FINISHED    // Show Stats badges (Orange/Green/Gray)
}

private fun MainViewModel.getState(): ActionState {
    return when {
        isCalculating -> ActionState.CALCULATING
        !statsRows.isEmpty() -> ActionState.FINISHED
        else -> ActionState.IDLE
    }
}


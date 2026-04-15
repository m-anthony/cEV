package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.ui.components.PokerActionButton
import com.snaky.poker.cev.ui.components.PokerStatusCard
import com.snaky.poker.cev.ui.components.StatusData
import com.snaky.poker.cev.ui.config.ConfigurationManager
import com.snaky.poker.cev.ui.config.HistorySource
import com.snaky.poker.cev.ui.model.ProcessingStats
import com.snaky.poker.cev.ui.model.ResultsViewModel
import com.snaky.poker.cev.ui.theme.DefaultTheme

@Composable
fun ResultsView(viewModel: ResultsViewModel, onBusyState: (Boolean) -> Unit) {
    val activeSources = ConfigurationManager.configuration.sources.filter { it.isActive }
    var isSettingsOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isSettingsOpen, viewModel.isCalculating) {
        onBusyState(isSettingsOpen || viewModel.isCalculating)
    }

    if (isSettingsOpen) {
        val currentConfig = ConfigurationManager.configuration.copy()
        SourcesEditor(onClose = {
            isSettingsOpen = false
            if (currentConfig != ConfigurationManager.configuration) viewModel.clearResults()
        })
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(DefaultTheme.Dimensions.CONTAINER_PADDING)) {
            Spacer(Modifier.height(DefaultTheme.Dimensions.CHIP_SPACING))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.SECTION_SPACING)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SourceSummaryBar(
                        allActive = activeSources,
                        isCalculating = viewModel.isCalculating,
                        onOpenSettings = { isSettingsOpen = true }
                    )
                }
                Box(modifier = Modifier.width(350.dp)) {
                    ActionArea(viewModel, activeSources.isNotEmpty())
                }
            }

            if (viewModel.isCalculating) ProcessingProgress(viewModel)

            if (viewModel.statsRows.isNotEmpty() && !viewModel.isCalculating) {
                Spacer(Modifier.height(DefaultTheme.Dimensions.SECTION_SPACING))
                StatsTable(
                    rows = viewModel.statsRows,
                    availableFormats = viewModel.availableFormats,
                    selectedStack = viewModel.selectedStackFilter,
                    onStackSelected = { viewModel.selectedStackFilter = it },
                    modifier = Modifier.fillMaxWidth()
                        .background(DefaultTheme.Colors.WindowBackground, DefaultTheme.Shapes.Medium)
                        .clip(DefaultTheme.Shapes.Medium)
                )
            }
        }
    }
}

@Composable
private fun ActionArea(viewModel: ResultsViewModel, hasActiveSources: Boolean) {
    Box(modifier = Modifier.height(DefaultTheme.Dimensions.BUTTON_HEIGHT)) {
        if (viewModel.isCalculating) {
            Button(
                onClick = { viewModel.stopCalculation() },
                modifier = Modifier.fillMaxSize(),
                colors = ButtonDefaults.buttonColors(containerColor = DefaultTheme.Colors.ActionTechnicalContainer),
                shape = DefaultTheme.Shapes.Medium
            ) {
                Icon(Icons.Outlined.Stop, null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("STOP CALCULATION", style = DefaultTheme.Typography.ButtonLabel)
            }
        } else if (viewModel.statsRows.isEmpty() || viewModel.isUpdateAvailable) {
            PokerActionButton(
                text = "PROCESS HISTORY FILES",
                icon = Icons.Outlined.PlayArrow,
                onClick = { viewModel.apply { clearUpdateAvailable(); runCalculation() } },
                enabled = hasActiveSources,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            ProcessingResultCard(
                stats = viewModel.processingStats
            )
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProcessingResultCard(stats: ProcessingStats) {

    val primaryStatus = StatusData(
        count = stats.validSpinCount,
        label = "valid spins",
        color = Color(0xFF1B5E20) // Vert sombre identique à ton screen
    )

    val secondaryStatuses = buildList {
        if (stats.incompleteSpinCount > 0) {
            add(StatusData(stats.incompleteSpinCount, "incomplete spins", Color(0xFFE65100)))
        }
        if (stats.duplicateHandCount > 0) {
            add(StatusData(stats.duplicateHandCount, "duplicate hands", Color(0xFF212121)))
        }
    }

    PokerStatusCard(
        primaryStatus = primaryStatus,
        secondaryStatuses = secondaryStatuses,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun SourceSummaryBar(allActive: List<HistorySource>, isCalculating: Boolean, onOpenSettings: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .height(DefaultTheme.Dimensions.BUTTON_HEIGHT)
            .clickable(enabled = !isCalculating, onClick = onOpenSettings),
        shape = DefaultTheme.Shapes.Medium,
        colors = CardDefaults.outlinedCardColors(
            // On s'assure que le container est soit transparent, soit strictement WindowBackground
            containerColor = DefaultTheme.Colors.WindowBackground,
        ),
        border = BorderStroke(DefaultTheme.Dimensions.BORDER_THICKNESS, DefaultTheme.Colors.Divider),
        // SOLUTION : On force l'élévation tonale à 0 pour éviter le gris de Material 3
        elevation = CardDefaults.outlinedCardElevation(
            defaultElevation = 0.dp,
            hoveredElevation = 0.dp,
            pressedElevation = 0.dp
        )
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                color = if (isCalculating) DefaultTheme.Colors.DisabledContainer else DefaultTheme.Colors.AccentContainer,
                shape = DefaultTheme.Shapes.Small
            ) {
                Text(
                    if (allActive.size <= 1) "SOURCE" else "SOURCES",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = DefaultTheme.Typography.Annotation.copy(fontWeight = FontWeight.Bold),
                    color = if (isCalculating) DefaultTheme.Colors.DisabledContent else DefaultTheme.Colors.PrimaryAction
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = if (allActive.isEmpty()) "No active source" else allActive.joinToString("  •  ") { it.name },
                style = DefaultTheme.Typography.BodyBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!isCalculating) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = DefaultTheme.Colors.TextSecondary)
        }
    }
}

@Composable
private fun ProcessingProgress(viewModel: ResultsViewModel) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(DefaultTheme.Shapes.Small),
            color = DefaultTheme.Colors.PrimaryAction,
            trackColor = DefaultTheme.Colors.DisabledContainer
        )
        Text(
            text = "Processing: ${viewModel.currentSpinCount} spins",
            style = DefaultTheme.Typography.Annotation,
            color = DefaultTheme.Colors.TextSecondary,
            modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
        )
    }
}
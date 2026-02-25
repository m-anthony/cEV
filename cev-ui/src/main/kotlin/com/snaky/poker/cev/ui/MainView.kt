package com.snaky.poker.cev.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaky.poker.cev.core.Hand
import com.snaky.poker.cev.core.Hand.Position.*
import com.snaky.poker.cev.ui.config.ConfigurationManager
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
        if (viewModel.isCalculating) {
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
                        Text("STOP", color = Color.Red, style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold))
                    }
                }
            }
        } else if(!viewModel.statsRows.isEmpty()) {
            ProcessingFeedback(viewModel.processingStats)
        } else {
            Button(
                onClick = { viewModel.runCalculation() },
                modifier = Modifier.fillMaxWidth(),
                enabled = !activeSources.isEmpty()
            ) {
                Text("Process history files")
            }
        }

        // --- Action Button ---

        Spacer(Modifier.height(24.dp))
        if (viewModel.availableFormats.size > 1) {
            FormatSelector(
                availableFormats = viewModel.availableFormats,
                selectedStack = viewModel.selectedStackFilter,
                onSelect = { viewModel.selectedStackFilter = it },
            )
        }
        // --- Results Table ---
        StatsTable(viewModel.statsRows)
    }
}

@Composable
fun SourceSummaryBar(
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
fun FormatSelector(
    availableFormats: Map<Int, Int>,
    selectedStack: Int?,
    onSelect: (Int?) -> Unit
) {

    if (availableFormats.size > 1) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Format : ", style = MaterialTheme.typography.subtitle2)

            FilterChip(
                selected = selectedStack == null,
                onClick = { onSelect(null) },
                label = "All"
            )

            availableFormats.forEach { (stack, count) ->
                val label = when(stack) {
                    200 -> "Flash - 200 chips"
                    300 -> "Nitro - 300 chips"
                    500 -> "Regular - 500 chips"
                    else -> "($stack)"
                }

                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = selectedStack == stack,
                    onClick = { onSelect(stack) },
                    label = "$label ($count)"
                )
            }
        }
    }
}

@Composable
fun FilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String
) {
    val backgroundColor = if (selected) MaterialTheme.colors.primary else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colors.onPrimary else MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
    val borderColor = if (selected) MaterialTheme.colors.primary else Color.LightGray.copy(alpha = 0.5f)

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .padding(end = 8.dp)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
@Composable
fun ProcessingFeedback(stats: ProcessingStats) {
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
fun BadgeText(count: Int, label: String, color: Color) {
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

@Composable
fun StatsTable(rows: List<SpinStats>) {

    val columns = listOf(
        "Buy-in" to { s: SpinStats -> AnnotatedString(s.label) },
        "Games" to { s: SpinStats -> AnnotatedString(s.count.toString()) },
        "Winnings" to { s: SpinStats -> AnnotatedString("%.2f€".format(s.netGain)) },
        "cEV" to { s -> s.formatCev() },
        "ITM" to { s: SpinStats -> AnnotatedString("%.1f %%".format(s.itm * 100)) },
        "ROI" to { s: SpinStats -> AnnotatedString("%.2f %%".format(s.roi * 100)) },
        "cEV BU" to { s: SpinStats -> s.formatPositionCev(BU) },
        "cEV SB" to { s: SpinStats -> s.formatPositionCev(SB) },
        "cEV BB" to { s: SpinStats -> s.formatPositionCev(BB) },
        "cEV HUSB" to { s: SpinStats -> s.formatPositionCev(HUSB) },
        "cEV HUBB" to { s: SpinStats -> s.formatPositionCev(HUBB) },
        "Eff. Rake" to { s: SpinStats -> s.formatEffectiveRake() }

    )

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(Modifier.background(MaterialTheme.colors.primaryVariant).padding(8.dp)) {
            columns.forEach { (title, _) ->
                val endPadding = if (title.contains("cEV")) 8 else 0
                Text(
                    title,
                    Modifier.weight(1f).padding(end = endPadding.dp),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End
                )
            }
        }


        // Body
        LazyColumn(Modifier.fillMaxSize()) {
            items(rows) { row ->
                val isTotal = row.label == "Total"
                val bgColor = if (isTotal) Color.LightGray.copy(alpha = 0.3f) else Color.Transparent

                Row(Modifier.background(bgColor).padding(8.dp)) {
                    columns.forEach { (_, formatter) ->
                        Text(
                            text = formatter(row),
                            modifier = Modifier.weight(1f),
                            fontWeight = if (isTotal) FontWeight.Bold else FontWeight.Normal,
                            textAlign = TextAlign.End
                        )
                    }
                }
                Divider()
            }
        }
    }
}

private fun SpinStats.formatCev(): AnnotatedString {
    if(cev.isNaN()) return AnnotatedString("")
    val ci95 = (2 * cevStdDev / sqrt(count.toDouble())).toInt()
    val isSignificant = ci95 < maxOf(10.0, cev / 2) && count > 50
    return buildAnnotatedString {
        if (isSignificant) {
            append(cev.roundToInt().toString())

            withStyle(style = SpanStyle(
                color = Color.Gray.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
                fontFamily = FontFamily.Monospace
            )) {
                append(" \u00B1%2d".format(ci95))
            }
        } else {
            withStyle(style = SpanStyle(
                color = Color.Gray.copy(alpha = 0.7f),
                fontWeight = FontWeight.Normal
            )) {
                append("%3d".format(cev.roundToInt()))
            }
            withStyle(style = SpanStyle(
                color = Color.Transparent,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal
            )) {
                append("----")
            }
        }
    }
}

private fun SpinStats.formatPositionCev(pos: Hand.Position): AnnotatedString {
    return AnnotatedString(
        positionalCev[pos]?.let { "%.1f".format(it) } ?: ""
    )
}

private fun SpinStats.formatEffectiveRake(): AnnotatedString {
    return AnnotatedString(
        if(effectiveRake.isNaN()) "N/A" else "%.2f %%".format(effectiveRake * 100)
    )
}


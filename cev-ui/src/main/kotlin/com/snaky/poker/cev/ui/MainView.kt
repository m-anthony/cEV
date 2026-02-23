package com.snaky.poker.cev.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                    if (viewModel.isCalculating) {
                        TextButton(
                            onClick = {
                                viewModel.stopCalculation()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text("STOP", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (isSettingsOpen) {
                SourcesEditor(onClose = { isSettingsOpen = false })
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

        // --- Action Button ---
        Button(
            onClick = { viewModel.runCalculation() },
            modifier = Modifier.fillMaxWidth(),
            enabled = !activeSources.isEmpty() && !viewModel.isCalculating
        ) {
            if (viewModel.isCalculating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Process history files")
            }
        }

        Spacer(Modifier.height(24.dp))

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
fun StatsTable(rows: List<SpinStats>) {

    val columns = listOf(
        "Buy-in" to { s: SpinStats -> AnnotatedString(s.label) },
        "Games" to { s: SpinStats -> AnnotatedString(s.count.toString()) },
        "Winnings" to { s: SpinStats -> AnnotatedString("%.2f€".format(s.netGain)) },
        "cEV" to { s -> s.formatCev() },
        "ITM" to { s: SpinStats -> AnnotatedString("%.1f %%".format(s.itm * 100)) },
        "ROI" to { s: SpinStats -> AnnotatedString("%.2f %%".format(s.roi * 100)) },
        "cEV BU" to { s: SpinStats -> AnnotatedString("%.1f".format(s.positionalCev[BU] ?: 0.0)) },
        "cEV SB" to { s: SpinStats -> AnnotatedString("%.1f".format(s.positionalCev[SB] ?: 0.0)) },
        "cEV BB" to { s: SpinStats -> AnnotatedString("%.1f".format(s.positionalCev[BB] ?: 0.0)) },
        "cEV HUSB" to { s: SpinStats -> AnnotatedString("%.1f".format(s.positionalCev[HUSB] ?: 0.0)) },
        "cEV HUBB" to { s: SpinStats -> AnnotatedString("%.1f".format(s.positionalCev[HUBB] ?: 0.0)) },
        "Eff. Rake" to { s: SpinStats -> AnnotatedString("%.2f %%".format(s.effectiveRake * 100)) }
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
    val ci95 = (2 * cevStdDev / sqrt(count.toDouble())).toInt()
    val isSignificant = ci95 < maxOf(10.0, cev / 2)
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


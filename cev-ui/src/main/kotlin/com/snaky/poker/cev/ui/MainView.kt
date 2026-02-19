package com.snaky.poker.cev.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.core.Hand.Position.*
import java.io.File
import javax.swing.JFileChooser
import kotlin.math.roundToInt

var lastOpenedDirectory: File? = null

@Composable
fun MainView(viewModel: MainViewModel) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Spin cEV Calculator") }) }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // --- Configuration Area ---
            Card(elevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Hand Histories Folder:", style = MaterialTheme.typography.caption)
                        Text(viewModel.selectedDirectory?.absolutePath ?: "No folder selected",
                            style = MaterialTheme.typography.body1)
                    }
                    Button(onClick = {
                        pickDirectory()?.let { viewModel.selectDirectory(it) }
                    }) {
                        Text("Browse ...")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // --- Action Button ---
            Button(
                onClick = { viewModel.runCalculation() },
                modifier = Modifier.fillMaxWidth(),
                enabled = viewModel.selectedDirectory != null && !viewModel.isCalculating
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
}

@Composable
fun StatsTable(rows: List<SpinStats>) {
    val columns = listOf(
        "Buy-in" to { s: SpinStats -> s.label },
        "Games" to { s: SpinStats -> s.count.toString() },
        "Winnings" to { s: SpinStats -> "%.2f€".format(s.netGain) },
        "cEV" to { s: SpinStats -> s.cev.roundToInt().toString() },
        "ITM" to { s: SpinStats -> "%.1f %%".format(s.itm * 100) },
        "ROI" to { s: SpinStats -> "%.2f %%".format(s.roi * 100) },
        "cEV BU" to {s: SpinStats -> "%.1f".format(s.positionalCev[BU] ?: 0.0)},
        "cEV SB" to {s: SpinStats -> "%.1f".format(s.positionalCev[SB] ?: 0.0)},
        "cEV BB" to {s: SpinStats -> "%.1f".format(s.positionalCev[BB] ?: 0.0)},
        "cEV HUSB" to {s: SpinStats -> "%.1f".format(s.positionalCev[HUSB] ?: 0.0)},
        "cEV HUBB" to {s: SpinStats -> "%.1f".format(s.positionalCev[HUBB] ?: 0.0)},
        "Eff. Rake" to {s: SpinStats -> "%.2f %%".format(s.effectiveRake * 100)}
    )

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(Modifier.background(MaterialTheme.colors.primaryVariant).padding(8.dp)) {
            columns.forEach { (title, _) ->
                Text(
                    title,
                    Modifier.weight(1f),
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

private fun pickDirectory(): File? {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        lastOpenedDirectory?.let { currentDirectory = it }
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        lastOpenedDirectory = chooser.selectedFile
        chooser.selectedFile
    } else null
}
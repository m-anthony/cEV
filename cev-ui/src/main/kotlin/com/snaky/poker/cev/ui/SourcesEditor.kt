package com.snaky.poker.cev.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.ui.config.ConfigurationManager
import com.snaky.poker.cev.ui.config.HistorySource
import java.io.File
import javax.swing.JFileChooser

@Composable
fun SourcesEditor(onClose: () -> Unit) {

    var draftConfig by remember { mutableStateOf(ConfigurationManager.configuration) }

    Column(modifier = Modifier
        .padding(16.dp)
        .fillMaxSize()
        .onPreviewKeyEvent { event ->
            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                onClose()
                true
            } else {
                false
            }
        }) {
        Text("Configure sources", style = MaterialTheme.typography.h6)

        Spacer(modifier = Modifier.height(16.dp))

        //current sources (based on draft)
        Box(modifier = Modifier.weight(1f)) {
            SourcesList(
                sources = draftConfig.sources,
                onSourcesChanged = { newSources ->
                    draftConfig = draftConfig.copy(sources = newSources)
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Actions : Add, Cancel, Save
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                pickDirectory { selectedPath ->
                    val newSource = HistorySource(name = "New source", path = selectedPath)
                    if (draftConfig.sources.none { it.path == selectedPath }) {
                        draftConfig = draftConfig.copy(sources = draftConfig.sources + newSource)
                    }
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add a new source folder")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onClose) {
                    Text("Cancel")
                }
                Button(onClick = {
                    ConfigurationManager.save(draftConfig)
                    onClose()
                }) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
fun SourcesList(
    sources: List<HistorySource>,
    onSourcesChanged: (List<HistorySource>) -> Unit
) {
    Card(elevation = 2.dp, modifier = Modifier.fillMaxSize()) {
        if (sources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No source", color = Color.Gray)
            }
        } else {
            LazyColumn {
                itemsIndexed(sources) { index, source ->
                    SourceRow(
                        source = source,
                        onUpdate = { updatedSource ->
                            val newList = sources.toMutableList().apply { set(index, updatedSource) }
                            onSourcesChanged(newList)
                        },
                        onDelete = {
                            onSourcesChanged(sources.filterIndexed { i, _ -> i != index })
                        }
                    )
                    if (index < sources.size - 1) Divider()
                }
            }
        }
    }
}

@Composable
fun SourceRow(
    source: HistorySource,
    onUpdate: (HistorySource) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = source.isActive,
            onCheckedChange = { onUpdate(source.copy(isActive = it)) }
        )

        Column(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
            TextField(
                value = source.name,
                onValueChange = { newName ->
                    onUpdate(source.copy(name = newName))
                },
                placeholder = { Text("Source Name") },
                singleLine = true,
                colors = TextFieldDefaults.textFieldColors(
                    backgroundColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colors.primary,
                    unfocusedIndicatorColor = Color.LightGray.copy(alpha = 0.5f)
                ),
                textStyle = MaterialTheme.typography.body1.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                source.path,
                style = MaterialTheme.typography.caption,
                color = Color.Gray,
                maxLines = 1
            )
        }

        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
        }
    }
}

private var lastOpenedDirectory: File? = null
private fun pickDirectory(onPathSelected: (String) -> Unit) {
    val chooser = JFileChooser().apply {
        fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        dialogTitle = "Select a Hand History folder"
        lastOpenedDirectory?.let { currentDirectory = it }
    }
    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
        onPathSelected(chooser.selectedFile.also { lastOpenedDirectory = it }.absolutePath)
    }
}
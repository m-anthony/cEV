package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.ui.components.PokerActionButton
import com.snaky.poker.cev.ui.config.ConfigurationManager
import com.snaky.poker.cev.ui.config.HistorySource
import com.snaky.poker.cev.ui.pickDirectory
import com.snaky.poker.cev.ui.theme.DefaultTheme
import java.io.File
@Composable
fun SourcesEditor(onClose: () -> Unit) {
    val initialConfig = remember { ConfigurationManager.configuration }
    var draftConfig by remember { mutableStateOf(initialConfig) }
    val isDirty = draftConfig != initialConfig

    Column(modifier = Modifier
        // Utilisation du padding standard harmonisé
        .padding(DefaultTheme.Dimensions.CONTAINER_PADDING * 1.5f)
        .fillMaxSize()
        .onPreviewKeyEvent { event ->
            if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                onClose()
                true
            } else false
        }) {

        Text(
            text = "Configure sources",
            style = DefaultTheme.Typography.Header,
            color = DefaultTheme.Colors.TextPrimary
        )

        Spacer(modifier = Modifier.height(DefaultTheme.Dimensions.FORM_SPACING_LARGE))

        Box(modifier = Modifier.weight(1f)) {
            SourcesList(
                sources = draftConfig.sources,
                onSourcesChanged = { draftConfig = draftConfig.copy(sources = it) }
            )
        }

        Spacer(modifier = Modifier.height(DefaultTheme.Dimensions.FORM_SPACING_LARGE))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    pickDirectory(lastOpenedDirectory) { path ->
                        lastOpenedDirectory = File(path)
                        if (draftConfig.sources.none { it.path == path }) {
                            val newSource = HistorySource(name = "New source", path = path)
                            draftConfig = draftConfig.copy(sources = draftConfig.sources + newSource)
                        }
                    }
                },
                modifier = Modifier.height(DefaultTheme.Dimensions.BUTTON_HEIGHT),
                shape = DefaultTheme.Shapes.Medium,
                // Remplacement du 1.dp par ta constante
                border = BorderStroke(DefaultTheme.Dimensions.BORDER_THICKNESS, DefaultTheme.Colors.PrimaryAction)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = null,
                    tint = DefaultTheme.Colors.PrimaryAction
                )
                Spacer(Modifier.width(DefaultTheme.Dimensions.CHIP_SPACING))
                Text(
                    text = "Add source folder",
                    color = DefaultTheme.Colors.PrimaryAction,
                    style = DefaultTheme.Typography.FieldLabel
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.FORM_SPACING_SMALL),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.height(DefaultTheme.Dimensions.BUTTON_HEIGHT)
                ) {
                    Text(
                        text = "Cancel",
                        color = DefaultTheme.Colors.TextSecondary
                    )
                }

                PokerActionButton(
                    text = "SAVE",
                    onClick = {
                        ConfigurationManager.save(draftConfig)
                        onClose()
                    },
                    enabled = isDirty,
                    modifier = Modifier.width(140.dp)
                )
            }
        }
    }
}

private var lastOpenedDirectory: File? = null

@Composable
private fun SourcesList(
    sources: List<HistorySource>,
    onSourcesChanged: (List<HistorySource>) -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxSize(),
        shape = DefaultTheme.Shapes.Large,
        colors = CardDefaults.outlinedCardColors(
            containerColor = DefaultTheme.Colors.TableBackground
        ),
        border = BorderStroke(DefaultTheme.Dimensions.BORDER_THICKNESS, DefaultTheme.Colors.Divider)
    ) {
        if (sources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No source configured",
                    style = DefaultTheme.Typography.FieldLabel,
                    color = DefaultTheme.Colors.TextDisabled
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(DefaultTheme.Dimensions.FORM_SPACING_SMALL),
                verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.FORM_SPACING_SMALL)
            ) {
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
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    source: HistorySource,
    onUpdate: (HistorySource) -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = DefaultTheme.Shapes.Medium,
        // C'est parfait : AccentContainer apporte la douceur lavande
        color = DefaultTheme.Colors.AccentContainer,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            DefaultTheme.Dimensions.BORDER_THICKNESS,
            // Utilisation d'un alpha léger sur le divider pour ne pas "emprisonner" la ligne
            DefaultTheme.Colors.Divider.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(DefaultTheme.Dimensions.FORM_SPACING_SMALL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = source.isActive,
                onCheckedChange = { onUpdate(source.copy(isActive = it)) },
                colors = CheckboxDefaults.colors(
                    checkedColor = DefaultTheme.Colors.PrimaryAction,
                    uncheckedColor = DefaultTheme.Colors.TextSecondary
                )
            )

            Column(modifier = Modifier.weight(1f).padding(horizontal = DefaultTheme.Dimensions.CHIP_SPACING)) {
                BasicTextField(
                    value = source.name,
                    onValueChange = { newName -> onUpdate(source.copy(name = newName)) },
                    textStyle = DefaultTheme.Typography.FieldLabel.copy(
                        color = DefaultTheme.Colors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Text(
                    text = source.path,
                    style = DefaultTheme.Typography.FieldLabel.copy(
                        fontSize = DefaultTheme.Typography.FieldLabel.fontSize * 0.85f
                    ),
                    color = DefaultTheme.Colors.TextSecondary,
                    maxLines = 1
                )
            }

            IconButton(
                onClick = onDelete,
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = DefaultTheme.Colors.TextDanger
                )
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = "Delete")
            }
        }
    }
}
@file:OptIn(ExperimentalLayoutApi::class)

package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.core.watchers.RoomLiveSupport
import com.snaky.poker.cev.core.watchers.getLiveSupport
import com.snaky.poker.cev.ui.components.PokerActionButton
import com.snaky.poker.cev.ui.components.PokerSwitch
import com.snaky.poker.cev.ui.config.HistorySource
import com.snaky.poker.cev.ui.model.SourcesViewModel
import com.snaky.poker.cev.ui.pickDirectory
import com.snaky.poker.cev.ui.theme.DefaultTheme
import java.io.File

@Composable
fun SourcesEditor(
    onClose: () -> Unit,
    viewModel: SourcesViewModel = remember { SourcesViewModel() }
) {

    DisposableEffect(Unit) {
        onDispose { viewModel.clear() }
    }

    val isDirty = viewModel.isDirty

    var sourceToEdit by remember { mutableStateOf<HistorySource?>(null) }

    Column(
        modifier = Modifier
            .padding(DefaultTheme.Dimensions.CONTAINER_PADDING * 1.5f)
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                    onClose()
                    true
                } else false
            }) {

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = { if (sourceToEdit != null) sourceToEdit = null else onClose() },
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = "Configure sources",
                    style = DefaultTheme.Typography.Header.copy(
                        color = if (sourceToEdit == null) DefaultTheme.Colors.TextPrimary else DefaultTheme.Colors.PrimaryAction
                    )
                )
            }

            if (sourceToEdit != null) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    tint = DefaultTheme.Colors.TextSecondary
                )
                Text(
                    text = "Edit source",
                    style = DefaultTheme.Typography.Header,
                    color = DefaultTheme.Colors.TextPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(DefaultTheme.Dimensions.FORM_SPACING_LARGE))

        Box(modifier = Modifier.weight(1f)) {
            if (sourceToEdit == null) {
                // --- VUE LISTE ---
                SourcesList(
                    sources = viewModel.draftConfig.sources,
                    onSourcesChanged = { newList ->
                        viewModel.updateSources(newList) // On passe par le ViewModel
                    },
                    onEditSource = { sourceToEdit = it },
                )
            } else {
                // --- VUE FORMULAIRE (Inline) ---
                SourceEditorForm(
                    source = sourceToEdit!!,
                    onCancel = { sourceToEdit = null },
                    viewModel = viewModel,
                    onSave = { updatedSource ->
                        val current = viewModel.draftConfig.sources.toMutableList()
                        val index = current.indexOfFirst { it.id == updatedSource.id }
                        if (index != -1) current[index] = updatedSource else current.add(updatedSource)

                        viewModel.updateSources(current)
                        sourceToEdit = null // Retour à la liste
                    }
                )
            }
        }

        if (sourceToEdit == null) {
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
                            // On crée une source "temporaire" et on bascule en mode édition
                            sourceToEdit = HistorySource(
                                name = File(path).name,
                                path = path
                            )
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
                        modifier = Modifier.height(DefaultTheme.Dimensions.BUTTON_HEIGHT).widthIn(min = 80.dp)
                    ) {
                        Text(
                            text = if(isDirty) "CANCEL" else "CLOSE",
                            color = DefaultTheme.Colors.TextSecondary
                        )
                    }

                    PokerActionButton(
                        text = "SAVE CHANGES",
                        onClick = {
                            viewModel.save()
                            onClose()
                        },
                        enabled = isDirty,
                    )
                }
            }
        }
    }
}

private var lastOpenedDirectory: File? = null

@Composable
private fun SourcesList(
    sources: List<HistorySource>,
    onSourcesChanged: (List<HistorySource>) -> Unit,
    onEditSource: (HistorySource) -> Unit
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
                        },
                        onEdit = { onEditSource(source) }
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
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        onClick = onEdit,
        shape = DefaultTheme.Shapes.Medium,
        color = DefaultTheme.Colors.AccentContainer,
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            DefaultTheme.Dimensions.BORDER_THICKNESS,
            DefaultTheme.Colors.Divider.copy(alpha = 0.2f)
        )
    ) {
        Row(
            modifier = Modifier.padding(DefaultTheme.Dimensions.FORM_SPACING_SMALL),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PokerSwitch(
                checked = source.isActive,
                onCheckedChange = { onUpdate(source.copy(isActive = it)) },
                modifier = Modifier.scale(0.8f)
            )

            Column(modifier = Modifier.weight(1f).padding(horizontal = DefaultTheme.Dimensions.CHIP_SPACING)) {
                Text(
                    text = source.name,
                    style = DefaultTheme.Typography.FieldLabel.copy(fontWeight = FontWeight.Bold),
                    color = DefaultTheme.Colors.TextPrimary
                )
                Text(
                    text = source.path,
                    style = DefaultTheme.Typography.Small,
                    color = DefaultTheme.Colors.TextSecondary,
                    maxLines = 1
                )

                if (source.liveRoom?.getLiveSupport() == RoomLiveSupport.READY) {
                    Text(
                        text = "● Live: ${source.liveRoom}",
                        style = DefaultTheme.Typography.Annotation,
                        color = DefaultTheme.Colors.Success
                    )
                }
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

@Composable
private fun SourceEditorForm(
    source: HistorySource,
    viewModel: SourcesViewModel,
    onSave: (HistorySource) -> Unit,
    onCancel: () -> Unit
) {
    var name by remember { mutableStateOf(source.name) }
    var path by remember { mutableStateOf(source.path) }
    var isActive by remember { mutableStateOf(source.isActive) }
    var isLiveEnabled by remember { mutableStateOf(source.liveRoom != null) }
    var selectedRoom by remember(source.id) { mutableStateOf(source.liveRoom) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.SECTION_SPACING)
    ) {
        Surface(
            shape = DefaultTheme.Shapes.Medium,
            color = DefaultTheme.Colors.AccentContainer,
            border = BorderStroke(
                DefaultTheme.Dimensions.BORDER_THICKNESS,
                DefaultTheme.Colors.Divider.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(
                    horizontal = DefaultTheme.Dimensions.CONTAINER_PADDING,
                    // On utilise CONTAINER_PADDING en haut et en bas pour la symétrie
                    vertical = DefaultTheme.Dimensions.CONTAINER_PADDING
                ),
                verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.FORM_SPACING_SMALL)
            ) {
                // --- IDENTITY ---
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = DefaultTheme.Colors.Transparent,
                        focusedContainerColor = DefaultTheme.Colors.Transparent
                    )
                )

                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Folder") },
                    modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            pickDirectory(File(path).ifExistsOrParent()) { path = it }
                        }) { Icon(Icons.Outlined.FolderOpen, contentDescription = null) }
                    },
                    textStyle = DefaultTheme.Typography.Small,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = DefaultTheme.Colors.Transparent,
                        focusedContainerColor = DefaultTheme.Colors.Transparent
                    )
                )

                // Espace léger entre les champs et les toggles
                Spacer(Modifier.height(DefaultTheme.Dimensions.FORM_SPACING_SMALL))

                // --- SETTINGS ---
                SettingToggleRow(
                    title = "Include in Global Reports",
                    subtitle = "Analyze data for statistics",
                    checked = isActive,
                    onCheckedChange = { isActive = it }
                )

                SettingToggleRow(
                    title = "Live Session Tracking",
                    subtitle = "Monitor hands in real-time",
                    checked = isLiveEnabled,
                    onCheckedChange = { enabled ->
                        isLiveEnabled = enabled
                        if (!enabled) selectedRoom = null
                    }
                )

                // --- LIVE PANEL ---
                if(isLiveEnabled) {
                    DetectionWithOverride(
                        path = path,
                        selectedRoom = selectedRoom,
                        isDetecting = viewModel.isDetecting, // Utilise l'état global du ViewModel
                        onRoomSelected = { selectedRoom = it },
                        onTriggerDetection = { currentPath ->
                            viewModel.detectRoomForPath(currentPath) { selectedRoom = it }
                        }
                    )
                }
            }
        }

        // On utilise un simple Spacer pour séparer le bloc des boutons
        // sans forcer un étirement excessif
        Spacer(Modifier.weight(1f))

        // --- ACTIONS ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isModified = name != source.name ||
                    path != source.path ||
                    isActive != source.isActive ||
                    (isLiveEnabled != (source.liveRoom != null)) ||
                    selectedRoom != source.liveRoom

            if(isModified) {
                TextButton(onClick = onCancel) {
                    Text("Discard", color = DefaultTheme.Colors.TextSecondary)
                }

                Spacer(Modifier.width(DefaultTheme.Dimensions.SECTION_SPACING))
            }


            PokerActionButton(
                text = if (isModified) "APPLY" else "DONE",
                onClick = {
                    onSave(source.copy(
                        name = name,
                        path = path,
                        isActive = isActive,
                        liveRoom = if(isLiveEnabled) selectedRoom else null
                    ))
                },
                enabled = name.isNotBlank() && path.isNotBlank()
                        && (!isLiveEnabled || (selectedRoom != null && selectedRoom?.getLiveSupport() != RoomLiveSupport.IMPOSSIBLE)),
                modifier = Modifier.width(140.dp)
            )
        }
    }
}

private val RoomLiveSupport.badgeText: String?
    @Composable get() = when(this) {
        RoomLiveSupport.READY -> null
        RoomLiveSupport.PLANNED -> "Coming soon"
        RoomLiveSupport.IMPOSSIBLE -> "No Live tracking"
    }

private val RoomLiveSupport.statusSuffix: String
    @Composable get() = when(this) {
        RoomLiveSupport.READY -> "Ready for Live"
        RoomLiveSupport.PLANNED -> "Support coming soon"
        RoomLiveSupport.IMPOSSIBLE -> "Live tracking impossible"
    }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetectionWithOverride(
    path: String,
    selectedRoom: Room?,
    isDetecting: Boolean,
    onRoomSelected: (Room?) -> Unit,
    onTriggerDetection: (String) -> Unit
) {
    var showManualSelection by remember { mutableStateOf(false) }
    val support = selectedRoom?.getLiveSupport() ?: RoomLiveSupport.IMPOSSIBLE


    LaunchedEffect(path) {
        if (selectedRoom == null) {
            onTriggerDetection(path)
        }
    }

    Surface(
        color = DefaultTheme.Colors.AccentContainer,
        shape = DefaultTheme.Shapes.Small,
        border = BorderStroke(1.dp, DefaultTheme.Colors.Divider.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDetecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = DefaultTheme.Colors.PrimaryAction
                    )
                } else {
                    Icon(
                        imageVector = when {
                            selectedRoom == null -> Icons.Outlined.Info
                            support == RoomLiveSupport.READY -> Icons.Outlined.CheckCircle
                            support == RoomLiveSupport.PLANNED -> Icons.Outlined.Schedule
                            else -> Icons.Outlined.Block
                        },
                        contentDescription = null,
                        tint = when {
                            selectedRoom == null -> DefaultTheme.Colors.TextSecondary
                            support == RoomLiveSupport.READY -> DefaultTheme.Colors.Success
                            support == RoomLiveSupport.PLANNED -> DefaultTheme.Colors.PrimaryAction
                            else -> DefaultTheme.Colors.TextSecondary
                        },
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Text(
                    text = when {
                        isDetecting -> "Analyzing folder ..."
                        selectedRoom == null -> "No software detected. Select one manually or disable Live tracking."
                        else -> "${selectedRoom.name} (${support.statusSuffix})"
                    },
                    style = DefaultTheme.Typography.Small.copy(
                        fontWeight = FontWeight.Bold,
                        color = if (selectedRoom == null && !isDetecting) DefaultTheme.Colors.TextDanger else DefaultTheme.Colors.TextPrimary
                    ),
                    modifier = Modifier.weight(1f)
                )

                if(!showManualSelection && !isDetecting) {
                    TextButton(
                        onClick = { showManualSelection = true },
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(24.dp)
                    ) {
                        Text(
                            text =  "Change",
                            style = DefaultTheme.Typography.Small.copy(fontWeight = FontWeight.Bold),
                            color = if (showManualSelection && support == RoomLiveSupport.IMPOSSIBLE) DefaultTheme.Colors.TextDanger else DefaultTheme.Colors.PrimaryAction
                        )
                    }
                }
            }

            if (showManualSelection) {
                // Manual room picker
                HorizontalDivider(thickness = 0.5.dp, color = DefaultTheme.Colors.Divider.copy(alpha = 0.3f))

                FlowRow(
                    modifier = Modifier.padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Room.entries.forEach { room ->
                        val roomSupport = room.getLiveSupport()
                        val supportedLiveRoom = roomSupport != RoomLiveSupport.IMPOSSIBLE
                        FilterChip(
                            selected = selectedRoom == room,
                            onClick = {
                                onRoomSelected(room)
                                if (supportedLiveRoom) showManualSelection = false
                            },
                            label = {
                                Text(
                                    text = room.name + (roomSupport.badgeText?.let { " ($it)" } ?: ""),
                                    fontSize = 11.sp,
                                    style = if (supportedLiveRoom) LocalTextStyle.current else TextStyle(textDecoration = TextDecoration.LineThrough)
                                )
                            },
                            enabled = supportedLiveRoom,
                            shape = DefaultTheme.Shapes.Small,
                            modifier = Modifier.height(28.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = DefaultTheme.Colors.AccentContainer,
                                labelColor = DefaultTheme.Colors.OnAccentContainer,
                                selectedContainerColor = DefaultTheme.Colors.PrimaryAction,
                                selectedLabelColor = DefaultTheme.Colors.TextOnAction,
                                disabledContainerColor = DefaultTheme.Colors.DisabledContainer,
                                disabledLabelColor = DefaultTheme.Colors.TextDisabled
                            ),
                            border = if (supportedLiveRoom) {
                                FilterChipDefaults.filterChipBorder(enabled = true, selected = selectedRoom == room)
                            } else {
                                BorderStroke(1.dp, DefaultTheme.Colors.Divider.copy(alpha = 0.5f))
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = DefaultTheme.Typography.BodyBold)
            Text(subtitle, style = DefaultTheme.Typography.Small, color = DefaultTheme.Colors.TextSecondary)
        }
        PokerSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun File.ifExistsOrParent(): File = if (exists()) this else parentFile ?: File(System.getProperty("user.home"))
package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.ui.components.*
import com.snaky.poker.cev.ui.model.LiveSessionUiState
import com.snaky.poker.cev.ui.model.LiveSessionViewModel
import com.snaky.poker.cev.ui.model.SessionStatus
import com.snaky.poker.cev.ui.theme.DefaultTheme
import kotlinx.coroutines.delay

@Composable
fun LiveSessionView(viewModel: LiveSessionViewModel) {
    val state by viewModel.uiState.collectAsState()

    Column{
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(DefaultTheme.Dimensions.CONTAINER_PADDING)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.SECTION_SPACING)
        ) {

            // --- DASHBOARD (KPIs) ---
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.SECTION_SPACING),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val green = Color(0xFF2E7D32)

                val statusColor = when (state.status) {
                    SessionStatus.TRACKING -> green
                    SessionStatus.PAUSED ->DefaultTheme.Colors.ActionTechnicalContainer
                    SessionStatus.READY -> DefaultTheme.Colors.TextPrimary
                }
                SessionStatCard(
                    label = "ACTIVE DURATION",
                    value = state.displayDuration,
                    modifier = Modifier.weight(1f),
                    valueColor = statusColor,
                )
                SessionStatCard(
                    label = "TOTAL GAMES",
                    value = "${state.totalGames}",
                    modifier = Modifier.weight(1f)
                )
                SessionStatCard(
                    label = "IN PROGRESS",
                    value = "${state.runningSpinsCount}",
                    modifier = Modifier.weight(1f),
                    valueColor = if (state.runningSpinsCount > 0) green else DefaultTheme.Colors.TextPrimary
                )
                // Bouton Reset (Largeur limitée pour l'équilibre visuel)
                PokerActionButton(
                    text = "RESET STATS",
                    icon = Icons.Outlined.Refresh,
                    onClick = { viewModel.resetStats() },
                    enabled = state.runningSpinsCount == 0 && state.status != SessionStatus.READY,
                    modifier = Modifier.width(180.dp),
                    containerColor = DefaultTheme.Colors.ActionTechnicalContainer
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.SECTION_SPACING)
            ) {
                // --- PERFORMANCE TABLE ---
                fun displayValue(value: AnnotatedString) = if(state.showResults) value else AnnotatedString("---")
                fun displayValue(value: String) = displayValue(AnnotatedString(value))

                PokerTable(
                    modifier = Modifier.weight(3f),
                    headerContent = {
                        PokerHeaderCell("Game Type", Modifier.weight(2f), textAlign = TextAlign.Start)
                        PokerHeaderCell("Count", Modifier.weight(1f))
                        PokerHeaderCell("Net Won", Modifier.weight(1.5f))
                        PokerHeaderCell("ROI", Modifier.weight(1f))
                        PokerHeaderCell("ITM", Modifier.weight(1f))
                        PokerHeaderCell("cEV", Modifier.weight(1f))
                    },
                    bodyContent = {
                        itemsIndexed(state.rows) { index, row ->
                            // Utilisation de PokerTableRow pour l'alternance automatique (Zébra)
                            PokerTableRow(index = index) {
                                PokerCellText(row.label, Modifier.weight(2f), textAlign = TextAlign.Start)
                                PokerCellText(row.count.toString(), Modifier.weight(1f))
                                PokerCellText(displayValue(row.netWon), Modifier.weight(1.5f))
                                PokerCellText(displayValue(row.roi), Modifier.weight(1f))
                                PokerCellText(displayValue(row.itm), Modifier.weight(1f))
                                PokerCellText(displayValue(row.cev), Modifier.weight(1f))
                            }
                        }
                    },
                    totalContent = state.totalRow?.let { total ->
                        {
                            PokerCellText("TOTAL", Modifier.weight(2f), isTotal = true, textAlign = TextAlign.Start)
                            PokerCellText(total.count.toString(), Modifier.weight(1f), isTotal = true)
                            PokerCellText(displayValue(total.netWon), Modifier.weight(1.5f), isTotal = true)
                            PokerCellText(displayValue(total.roi), Modifier.weight(1f), isTotal = true)
                            PokerCellText(displayValue(total.itm), Modifier.weight(1f), isTotal = true)
                            PokerCellText(displayValue(total.cev), Modifier.weight(1f), isTotal = true)
                        }
                    },
                )

                Surface(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = DefaultTheme.Shapes.Medium,
                    color = DefaultTheme.Colors.HeaderBackground.copy(alpha = 0.2f),
                    border = BorderStroke(DefaultTheme.Dimensions.BORDER_THICKNESS, DefaultTheme.Colors.Divider)
                ) {
                    Column(
                        modifier = Modifier.padding(DefaultTheme.Dimensions.CONTAINER_PADDING),
                        verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.SECTION_SPACING)
                    ) {
                        Text(
                            text = "SESSION OPTIONS",
                            style = DefaultTheme.Typography.SectionLabel,
                            color = DefaultTheme.Colors.TextSecondary
                        )

                        // Option : Live Widget
                        SessionOptionRow(
                            title = "Timer and count Widget",
                            tooltip = "Show a small overlay with timer and game count.\nDouble-click the widget to minimize or restore the main window.",
                            checked = state.liveWidgetConfig != null,
                            onCheckedChange = { enabled -> viewModel.toggleLiveWidget(enabled) }
                        )

                        SessionOptionRow(
                            title = "Hide Results",
                            tooltip = "Hide sensitive data like cEV, ROI, and Net Won.\nIdeal for staying focused on play rather than short-term results.",
                            checked = !state.showResults,
                            onCheckedChange = { viewModel.showResults(!it) }
                        )
                    }
                }
            }
        }
        ApplicationStatusBar(state)
    }
}

@Composable
private fun SessionStatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = DefaultTheme.Colors.TextPrimary,
) {
    Surface(
        modifier = modifier,
        shape = DefaultTheme.Shapes.Medium,
        color = DefaultTheme.Colors.HeaderBackground.copy(alpha = 0.3f),
        border = BorderStroke(DefaultTheme.Dimensions.BORDER_THICKNESS, DefaultTheme.Colors.Divider)
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = DefaultTheme.Dimensions.CONTAINER_PADDING,
                vertical = DefaultTheme.Dimensions.CHIP_VERTICAL_PADDING
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.CELL_PADDING)
        ) {
            Text(
                text = label,
                style = DefaultTheme.Typography.SectionLabel,
                color = DefaultTheme.Colors.TextSecondary
            )
            Text(
                text = value,
                style = DefaultTheme.Typography.HeadlineMonospace,
                color = valueColor, maxLines = 1
            )
        }
    }
}

@Composable
private fun ApplicationStatusBar(state: LiveSessionUiState) {
    var displayLastUpdate by remember(state.lastUpdateMillis) {
        mutableStateOf(getRelativeTime(state.lastUpdateMillis))
    }
    LaunchedEffect(state.lastUpdateMillis) {
        while (true) {
            delay(5000) // Rafraîchissement de l'affichage toutes les 5s
            displayLastUpdate = getRelativeTime(state.lastUpdateMillis)
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().height(24.dp),
        color = DefaultTheme.Colors.HeaderBackground.copy(alpha = 0.5f),
        border = BorderStroke(width = DefaultTheme.Dimensions.BORDER_THICKNESS, color = DefaultTheme.Colors.Divider)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DefaultTheme.Dimensions.CONTAINER_PADDING),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            state.sessionStartWallTime?.let {
                Text(
                    text = "Session started at $it",
                    style = DefaultTheme.Typography.Annotation,
                    color = DefaultTheme.Colors.TextSecondary
                )
            }

            if (state.hasStaleWarnings) {
                Surface(
                    color = DefaultTheme.Colors.ErrorBackground,
                    border = BorderStroke(DefaultTheme.Dimensions.BORDER_THICKNESS, DefaultTheme.Colors.ErrorOutline),
                    shape = DefaultTheme.Shapes.Medium
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = DefaultTheme.Colors.TextDanger,
                            modifier = Modifier.size(14.dp)
                        )

                        Spacer(Modifier.width(6.dp))

                        Text(
                            text = "${state.staledSpinsCount} spin(s) inactive",
                            style = DefaultTheme.Typography.Small.copy(fontWeight = FontWeight.Bold),
                            color = DefaultTheme.Colors.TextDanger
                        )
                    }
                }
            }

            val syncText = state.lastUpdateMillis?.let {
                "Last Sync: $displayLastUpdate"
            } ?: "Waiting for data..."
            Text(
                text = syncText,
                style = DefaultTheme.Typography.Annotation,
                color = DefaultTheme.Colors.TextSecondary
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SessionOptionRow(
    title: String,
    tooltip: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TooltipArea(
            tooltip = {
                Surface(
                    modifier = Modifier.shadow(DefaultTheme.Dimensions.TOOLTIP_ELEVATION),
                    color = DefaultTheme.Colors.TooltipBackground,
                    shape = DefaultTheme.Shapes.Small
                ) {
                    Text(
                        text = tooltip,
                        modifier = Modifier.padding(DefaultTheme.Dimensions.TOOLTIP_PADDING),
                        color = DefaultTheme.Colors.TooltipOnBackground,
                        style = DefaultTheme.Typography.Small
                    )
                }
            },
            delayMillis = DefaultTheme.Dimensions.TOOLTIP_DELAY
        ) {
            Text(
                text = title,
                style = DefaultTheme.Typography.BodyBold,
                color = DefaultTheme.Colors.TextPrimary
            )
        }

        PokerSwitch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private fun getRelativeTime(timestamp: Long?): String {
    if (timestamp == null) return "No activity"
    val diffSeconds = (System.currentTimeMillis() - timestamp) / 1000

    return when {
        diffSeconds < 5 -> "Just now"
        diffSeconds < 60 -> "${diffSeconds}s ago"
        diffSeconds < 3600 -> "${diffSeconds / 60} min ago"
        else -> "More than 1 hour ago"
    }
}


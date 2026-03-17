package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.ui.VarianceReport
import com.snaky.poker.cev.ui.components.*
import com.snaky.poker.cev.ui.theme.DefaultTheme
import com.snaky.poker.cev.ui.toAmountAnnotatedString

@Composable
fun VarianceResultTable(
    report: VarianceReport,
    buyInCents: Int,
    gamesCount: Int,
    displayInBuyIns: Boolean,
    onToggleDisplayMode: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(DefaultTheme.Dimensions.CONTAINER_PADDING)
    ) {
        // --- HEADER & UNIT SELECTOR ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = DefaultTheme.Dimensions.SECTION_SPACING),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Simulation Results",
                style = DefaultTheme.Typography.Headline,
                color = DefaultTheme.Colors.TextPrimary
            )

            SingleChoiceSegmentedButtonRow {
                SegmentedButton(
                    selected = !displayInBuyIns,
                    onClick = { if (displayInBuyIns) onToggleDisplayMode() },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = { Icon(Icons.Default.Euro, contentDescription = null, modifier = Modifier.size(DefaultTheme.Dimensions.CHIP_VERTICAL_PADDING * 2)) }
                ) {
                    Text("Currency", style = DefaultTheme.Typography.Small)
                }
                SegmentedButton(
                    selected = displayInBuyIns,
                    onClick = { if (!displayInBuyIns) onToggleDisplayMode() },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = { Icon(Icons.Default.Numbers, contentDescription = null, modifier = Modifier.size(DefaultTheme.Dimensions.CHIP_VERTICAL_PADDING * 2)) }
                ) {
                    Text("Buy-ins", style = DefaultTheme.Typography.Small)
                }
            }
        }

        // --- TOP LINE: Average Summary Stats ---
        Surface(
            color = DefaultTheme.Colors.AccentContainer,
            shape = DefaultTheme.Shapes.Large,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(DefaultTheme.Dimensions.CONTAINER_PADDING),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TheoreticalStat(
                    label = "Average Net Profit",
                    value = report.theoreticalEv.toSmartAmountString(buyInCents, displayInBuyIns)
                )

                val theoreticalRoi = (report.theoreticalEv.toDouble() / (gamesCount.toDouble() * buyInCents)) * 100
                TheoreticalStat(
                    label = "Average ROI",
                    value = String.format("%.2f%%", theoreticalRoi)
                )

                TheoreticalStat(
                    label = "Average Rake",
                    value = String.format("%.2f%%", report.theoreticalRake * 100)
                )
            }
        }

        Spacer(modifier = Modifier.height(DefaultTheme.Dimensions.SECTION_SPACING))

        // --- UTILISATION DU POKERTABLE ---
        PokerTable(
            headerContent = {
                PokerHeaderCell(
                    text = "Percentile",
                    modifier = Modifier.weight(1.0f),
                    textAlign = TextAlign.Start,
                    tooltipText = "A percentile indicates the value below which a given percentage of observations fall. Note: Metrics are calculated independently."
                )
                PokerHeaderCell(
                    text = "Net Profit",
                    modifier = Modifier.weight(1.0f),
                    textAlign = TextAlign.End,
                    tooltipText = "Total gains including rakeback"
                )
                PokerHeaderCell(
                    text = "ROI",
                    modifier = Modifier.weight(1.0f),
                    textAlign = TextAlign.End,
                    tooltipText = "Actual Return on Investment"
                )
                PokerHeaderCell(
                    text = "Max Drop",
                    modifier = Modifier.weight(1.0f),
                    textAlign = TextAlign.End,
                    tooltipText = "Largest peak-to-trough drop recorded"
                )
                PokerHeaderCell(
                    text = "Low Point",
                    modifier = Modifier.weight(1.0f),
                    textAlign = TextAlign.End,
                    tooltipText = "The lowest bankroll point reached during the run"
                )
                PokerHeaderCell(
                    text = "Max Breakeven",
                    modifier = Modifier.weight(1.3f),
                    textAlign = TextAlign.End,
                    tooltipText = "Max games played without hitting a new profit peak"
                )
                PokerHeaderCell(
                    text = "Eff. Rake",
                    modifier = Modifier.weight(1.0f),
                    textAlign = TextAlign.End,
                    tooltipText = "Actual rake paid based on multiplier distribution"
                )
            },
            bodyContent = {
                items(report.points) { point ->
                    val isMedian = point.percentile == 50
                    val isWorst = point.percentile == 1
                    val isTop = point.percentile == 99

                    val rowBackground = when {
                        isMedian -> DefaultTheme.Colors.RowMedian
                        isWorst -> DefaultTheme.Colors.RowNegative
                        isTop -> DefaultTheme.Colors.RowPositive
                        else -> Color.Transparent
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBackground)
                            .padding(DefaultTheme.Dimensions.TABLE_ROW_PADDING),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val label = when(point.percentile) {
                            1 -> "1% (Worst)"
                            50 -> "50%"
                            99 -> "99% (Top)"
                            else -> "${point.percentile}%"
                        }

                        // Percentile Label
                        PokerCellText(
                            text = label,
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.Start,
                            isTotal = isMedian
                        )

                        // Net Profit
                        PokerCellText(
                            text = point.netProfitCents.let {
                                it.toSmartAmountString(buyInCents, displayInBuyIns).toAmountAnnotatedString(it.toDouble())
                            },
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.End,
                            isTotal = isMedian,
                        )

                        // ROI
                        val effectiveRoi = (point.netProfitCents.toDouble() / (gamesCount.toDouble() * buyInCents)) * 100
                        PokerCellText(
                            text = String.format("%.2f%%", effectiveRoi).toAmountAnnotatedString(effectiveRoi),
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.End,
                            isTotal = isMedian,
                        )

                        // Max Swing
                        PokerCellText(
                            text = point.maxSwingCents.toSmartAmountString(buyInCents, displayInBuyIns),
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.End,
                            isTotal = isMedian
                        )

                        // Low Point
                        PokerCellText(
                            text = point.lowPointCents.toSmartAmountString(buyInCents, displayInBuyIns),
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.End,
                            isTotal = isMedian
                        )

                        // Breakeven
                        PokerCellText(
                            text = "${point.longestBreakeven} games",
                            modifier = Modifier.weight(1.3f),
                            textAlign = TextAlign.End,
                            isTotal = isMedian
                        )

                        // Eff. Rake
                        PokerCellText(
                            text = String.format("%.2f%%", point.effectiveRake * 100),
                            modifier = Modifier.weight(1.0f),
                            textAlign = TextAlign.End,
                            isTotal = isMedian
                        )
                    }

                    // On ne met pas de divider sur les lignes de percentile colorées pour garder le bloc uni
                    if (!isMedian && !isWorst && !isTop) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = DefaultTheme.Dimensions.CONTAINER_PADDING),
                            thickness = DefaultTheme.Dimensions.DIVIDER_THICKNESS,
                            color = DefaultTheme.Colors.Divider
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun TheoreticalStat(label: String, value: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp) // Petit espace entre label et valeur
    ) {
        Text(
            text = label,
            style = DefaultTheme.Typography.Small,
            color = DefaultTheme.Colors.TextSecondary
        )
        Text(
            text = value,
            style = DefaultTheme.Typography.TitleBold,
            color = DefaultTheme.Colors.TextPrimary
        )
    }
}


fun Int.toSmartAmountString(buyInCents: Int, displayInBuyIns: Boolean): String {
    return if (displayInBuyIns) {
        val bi = this.toDouble() / buyInCents
        if (bi % 1.0 == 0.0) String.format("%.0f BI", bi) else String.format("%.1f BI", bi)
    } else {
        val euros = this / 100.0
        val formatted = if (this % 100 == 0) {
            String.format("%,.0f", euros)
        } else {
            String.format("%,.2f", euros)
        }
        "${formatted.replace(" ", " ")} €"
    }
}
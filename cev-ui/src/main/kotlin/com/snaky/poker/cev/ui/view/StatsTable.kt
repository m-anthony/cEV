package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import com.snaky.poker.cev.core.model.Hand
import com.snaky.poker.cev.ui.amountAnnotatedString
import com.snaky.poker.cev.ui.components.*
import com.snaky.poker.cev.ui.formatStartingStack
import com.snaky.poker.cev.ui.model.SpinStats
import com.snaky.poker.cev.ui.theme.DefaultTheme
import com.snaky.poker.cev.ui.toAmountAnnotatedString
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun StatsTable(
    rows: List<SpinStats>,
    availableFormats: Map<Int, Int>,
    selectedStack: Int?,
    onStackSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {

    val dataRows = rows.filter { it.label != "Total" }
    val totalRow = rows.find { it.label == "Total" }

    val columns = listOf(
        "Buy-in" to { s: SpinStats -> AnnotatedString(s.label) },
        "Games" to { s: SpinStats -> AnnotatedString(s.count.toString()) },
        "cEV" to { s: SpinStats -> s.formatCev() },
        "EV$" to { s: SpinStats -> s.formatEvMoney(rows, selectedStack) },
        "Net Won" to { s: SpinStats -> amountAnnotatedString(s.netGain)},
        "ROI" to { s: SpinStats -> "%.2f %%".format(s.roi * 100).toAmountAnnotatedString(s.roi) },
        "ITM" to { s: SpinStats -> AnnotatedString("%.1f %%".format(s.itm * 100)) },
        "cEV BU" to { s: SpinStats -> s.formatPositionCev(Hand.Position.BU) },
        "cEV SB" to { s: SpinStats -> s.formatPositionCev(Hand.Position.SB) },
        "cEV BB" to { s: SpinStats -> s.formatPositionCev(Hand.Position.BB) },
        "cEV HUSB" to { s: SpinStats -> s.formatPositionCev(Hand.Position.HUSB) },
        "cEV HUBB" to { s: SpinStats -> s.formatPositionCev(Hand.Position.HUBB) },
        "Eff. Rake" to { s: SpinStats -> s.formatEffectiveRake() }
    )

    Column(modifier = modifier) {

        if (availableFormats.size > 1) {
            FormatSelector(availableFormats, selectedStack, onStackSelected)
            Spacer(Modifier.height(DefaultTheme.Dimensions.SECTION_SPACING))
        }

        PokerTable(
            headerContent = {
                columns.forEach { (title, _) ->
                    PokerHeaderCell(text = title, modifier = Modifier.weight(1f))
                }
            },
            bodyContent = {
                itemsIndexed(dataRows) { index, row ->
                    PokerTableRow(index = index) {
                        columns.forEach { (title, formatter) ->
                            val cellModifier = Modifier.weight(1f)
                            val content = formatter(row)
                            val res = row.varianceResult

                            if (title == "EV$" && res != null) {
                                val selector = res.profitSelector(selectedStack)
                                val tooltipText = "Range (90%%): %.2f€ to %.2f€".format(
                                    selector.invoke(res.p5Sample) / 100.0,
                                    selector.invoke(res.p95Sample) / 100.0
                                )

                                PokerTooltipArea(tooltipText = tooltipText, modifier = cellModifier) {
                                    PokerCellText(
                                        text = content,
                                        modifier = Modifier.fillMaxWidth(),
                                        isInteractive = true
                                    )
                                }
                            } else {
                                PokerCellText(text = content, modifier = cellModifier)
                            }
                        }
                    }
                }
            },
            totalContent = {
                totalRow?.let { row ->
                    columns.forEach { (_, formatter) ->
                        PokerCellText(
                            text = formatter(row),
                            modifier = Modifier.weight(1f),
                            isTotal = true
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun FormatSelector(
    availableFormats: Map<Int, Int>,
    selectedStack: Int?,
    onSelect: (Int?) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.CHIP_SPACING)
    ) {
        PokerLabel("Format :")

        PokerFilterChip(selected = selectedStack == null, onClick = { onSelect(null) }, label = "All")

        availableFormats.forEach { (stack, count) ->
            PokerFilterChip(
                selected = selectedStack == stack,
                onClick = { onSelect(stack) },
                label = "${formatStartingStack(stack)} ($count)"
            )
        }
    }
}

// --- Logique métier (Inchangée) ---
private fun SpinStats.formatCev(): AnnotatedString {
    if(cev.isNaN()) return AnnotatedString("")
    val (ci95, isSignificant) = getConfidenceInterval()
    val annotationStyle = DefaultTheme.Typography.Annotation
    val disabledColor = DefaultTheme.Colors.TextDisabled

    return buildAnnotatedString {
        if (isSignificant) {
            append(cev.roundToInt().toString())
            withStyle(style = SpanStyle(
                color = disabledColor,
                fontSize = annotationStyle.fontSize,
                fontFamily = annotationStyle.fontFamily
            )) {
                append(" \u00B1%2d".format(ci95))
            }
        } else {
            withStyle(style = SpanStyle(color = disabledColor)) {
                append("%3d".format(cev.roundToInt()))
            }
            withStyle(style = SpanStyle(
                color = DefaultTheme.Colors.Transparent,
                fontSize = annotationStyle.fontSize,
                fontFamily = annotationStyle.fontFamily
            )) {
                append("----")
            }
        }
    }
}

private fun SpinStats.formatEvMoney(allRows: List<SpinStats>, selectedStack: Int?): AnnotatedString {
    val ev = if (label != "Total") {
        varianceResult?.let { it.profitSelector(selectedStack).invoke(it.medianSample) / 100.0 }
    } else {
        allRows.filter { it != this }
            .fold(0 as Int?) { acc, r ->
                r.varianceResult?.let { acc?.plus(it.profitSelector(selectedStack).invoke(it.medianSample)) }
            }?.div(100.0)
    }
    return ev?.let { amountAnnotatedString(it) } ?: AnnotatedString("...")
}

private fun SpinStats.getConfidenceInterval(): Pair<Int, Boolean> {
    val ci95 = (2 * cevStdDev / sqrt(count.toDouble())).toInt()
    val isSignificant = ci95 < maxOf(10.0, cev / 2) && count > 50
    return Pair(ci95, isSignificant)
}

private fun SpinStats.formatPositionCev(pos: Hand.Position): AnnotatedString {
    val (_, isSignificant) = getConfidenceInterval()
    val cevString = positionalCev[pos]?.let { "%.1f".format(it) } ?: ""
    val disabledColor = DefaultTheme.Colors.TextDisabled

    return buildAnnotatedString {
        if(!isSignificant){
            withStyle(style = SpanStyle(color = disabledColor)) {
                append(cevString)
            }
        } else {
            append(cevString)
        }
    }
}

private fun SpinStats.formatEffectiveRake(): AnnotatedString {
    return AnnotatedString(
        if(effectiveRake.isNaN()) "N/A" else "%.2f %%".format(effectiveRake * 100)
    )
}
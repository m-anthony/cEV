package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaky.poker.cev.core.Hand
import com.snaky.poker.cev.ui.model.SpinStats
import kotlin.math.roundToInt
import kotlin.math.sqrt

@Composable
fun StatsTable(
    rows: List<SpinStats>,
    availableFormats: Map<Int, Int>,
    selectedStack: Int?,
    onStackSelected: (Int?) -> Unit
) {

    val columns = listOf(
        "Buy-in" to { s: SpinStats -> AnnotatedString(s.label) },
        "Games" to { s: SpinStats -> AnnotatedString(s.count.toString()) },
        "Winnings" to { s: SpinStats -> AnnotatedString("%.2f€".format(s.netGain)) },
        "cEV" to { s -> s.formatCev() },
        "ITM" to { s: SpinStats -> AnnotatedString("%.1f %%".format(s.itm * 100)) },
        "ROI" to { s: SpinStats -> AnnotatedString("%.2f %%".format(s.roi * 100)) },
        "cEV BU" to { s: SpinStats -> s.formatPositionCev(Hand.Position.BU) },
        "cEV SB" to { s: SpinStats -> s.formatPositionCev(Hand.Position.SB) },
        "cEV BB" to { s: SpinStats -> s.formatPositionCev(Hand.Position.BB) },
        "cEV HUSB" to { s: SpinStats -> s.formatPositionCev(Hand.Position.HUSB) },
        "cEV HUBB" to { s: SpinStats -> s.formatPositionCev(Hand.Position.HUBB) },
        "Eff. Rake" to { s: SpinStats -> s.formatEffectiveRake() }
    )

    if (availableFormats.size > 1) {
        FormatSelector(availableFormats, selectedStack, onStackSelected)
    }

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

@Composable
private fun FormatSelector(
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
private fun FilterChip(
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
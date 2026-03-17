package com.snaky.poker.cev.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import com.snaky.poker.cev.ui.theme.DefaultTheme

@Composable
fun PokerTable(
    modifier: Modifier = Modifier,
    headerContent: @Composable RowScope.() -> Unit,
    bodyContent: LazyListScope.() -> Unit,
    totalContent: @Composable (RowScope.() -> Unit)? = null
) {
    Surface(
        shape = DefaultTheme.Shapes.Large,
        color = DefaultTheme.Colors.TableBackground,
        tonalElevation = DefaultTheme.Dimensions.TABLE_ELEVATION,
        border = BorderStroke(
            DefaultTheme.Dimensions.BORDER_THICKNESS,
            DefaultTheme.Colors.Divider
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DefaultTheme.Colors.HeaderBackground)
                    .padding(DefaultTheme.Dimensions.TABLE_HEADER_PADDING),
                verticalAlignment = Alignment.CenterVertically,
                content = headerContent
            )
            HorizontalDivider(
                thickness = DefaultTheme.Dimensions.DIVIDER_THICKNESS,
                color = DefaultTheme.Colors.Divider
            )
            Box(modifier = Modifier.weight(1f, fill = false)) {
                LazyColumn(modifier = Modifier.fillMaxWidth(), content = bodyContent)
            }
            totalContent?.let { content ->
                HorizontalDivider(
                    thickness = DefaultTheme.Dimensions.DIVIDER_THICKNESS,
                    color = DefaultTheme.Colors.Divider
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DefaultTheme.Colors.AccentContainer)
                        .padding(DefaultTheme.Dimensions.TABLE_HEADER_PADDING),
                    verticalAlignment = Alignment.CenterVertically,
                    content = content
                )
            }
        }
    }
}

@Composable
fun PokerHeaderCell(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.End,
    tooltipText: String? = null // Nouveau paramètre optionnel
) {
    val tooltip = tooltipText != null
    val baseTextModifier = if(tooltip) Modifier else modifier
    val headerContent = @Composable {
        Text(
            text = text,
            modifier = baseTextModifier
                .fillMaxWidth()
                .padding(horizontal = DefaultTheme.Dimensions.CELL_PADDING),
            style = DefaultTheme.Typography.Header,
            color = DefaultTheme.Colors.TextSecondary,
            textAlign = textAlign
        )
    }

    if (tooltip) {
        PokerTooltipArea(tooltipText = tooltipText, modifier = modifier) {
            headerContent()
        }
    } else {
        headerContent()
    }
}

@Composable
fun PokerCellText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    isTotal: Boolean = false,
    isInteractive: Boolean = false,
    textAlign: TextAlign = TextAlign.End,
) {
    Box(
        modifier = modifier.defaultMinSize(minHeight = DefaultTheme.Dimensions.TABLE_ROW_MIN_HEIGHT),
        contentAlignment = when(textAlign) {
            TextAlign.End -> Alignment.CenterEnd
            TextAlign.Start -> Alignment.CenterStart
            else -> Alignment.Center
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = DefaultTheme.Dimensions.CELL_PADDING),
            style = if (isTotal) DefaultTheme.Typography.BodyBold else DefaultTheme.Typography.Body,
            textDecoration = if (isInteractive) TextDecoration.Underline else TextDecoration.None,
            textAlign = textAlign,
            color = when {
                isTotal -> DefaultTheme.Colors.TextContrast
                else -> DefaultTheme.Colors.TextPrimary
            }
        )
    }
}

@Composable
fun PokerCellText(
    text: String,
    modifier: Modifier = Modifier,
    isTotal: Boolean = false,
    isInteractive: Boolean = false,
    textAlign: TextAlign = TextAlign.End,
) = PokerCellText(AnnotatedString(text), modifier, isTotal, isInteractive, textAlign)
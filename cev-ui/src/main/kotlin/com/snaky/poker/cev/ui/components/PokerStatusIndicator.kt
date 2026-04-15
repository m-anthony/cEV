package com.snaky.poker.cev.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.ui.theme.DefaultTheme


@Composable
fun PokerStatusIndicator(
    status: StatusData,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
    showBackground: Boolean = false
) {
    PokerStatusIndicator(
        text = "${status.count} ${status.label}",
        color = status.color,
        modifier = modifier,
        isLarge = isLarge,
        showBackground = showBackground
    )
}

@Composable
fun PokerStatusIndicator(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
    showBackground: Boolean = false
) {
    // Utilisation des dimensions du thème pour les puces
    val dotSize = if (isLarge) DefaultTheme.Dimensions.FORM_SPACING_SMALL else 6.dp
    val spacing = if (isLarge) DefaultTheme.Dimensions.CHIP_HORIZONTAL_PADDING else DefaultTheme.Dimensions.FORM_SPACING_SMALL

    // Utilisation de la typographie sémantique
    val textStyle = if (isLarge) DefaultTheme.Typography.LabelBold else DefaultTheme.Typography.Small

    if (showBackground) {
        Surface(
            shape = DefaultTheme.Shapes.Medium,
            color = color.copy(alpha = 0.1f),
            border = BorderStroke(DefaultTheme.Dimensions.BORDER_THICKNESS, color.copy(alpha = 0.2f)),
            modifier = modifier
        ) {
            StatusContent(
                dotSize = dotSize,
                spacing = spacing,
                dotColor = color,
                text = text,
                style = textStyle,
                // Padding horizontal synchronisé avec tes puces (Chips)
                modifier = Modifier.padding(
                    horizontal = DefaultTheme.Dimensions.CHIP_HORIZONTAL_PADDING,
                    vertical = DefaultTheme.Dimensions.CHIP_VERTICAL_PADDING
                )
            )
        }
    } else {
        StatusContent(dotSize, spacing, color, text, textStyle, modifier)
    }
}

@Composable
private fun StatusContent(
    dotSize: Dp,
    spacing: Dp,
    dotColor: Color,
    text: String,
    style: TextStyle,
    modifier: Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(dotSize).background(dotColor, CircleShape))
        Spacer(Modifier.width(spacing))
        Text(text = text, color = dotColor, style = style)
    }
}
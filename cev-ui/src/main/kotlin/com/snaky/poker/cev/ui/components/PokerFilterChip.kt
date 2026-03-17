package com.snaky.poker.cev.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.snaky.poker.cev.ui.theme.DefaultTheme

@Composable
fun PokerFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) DefaultTheme.Colors.AccentContainer else Color.Transparent,
        contentColor = if (selected) DefaultTheme.Colors.OnAccentContainer else DefaultTheme.Colors.TextSecondary,
        shape = DefaultTheme.Shapes.Medium,
        border = BorderStroke(
            width = DefaultTheme.Dimensions.BORDER_THICKNESS,
            color = if (selected) DefaultTheme.Colors.Interactive else DefaultTheme.Colors.Divider
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = DefaultTheme.Typography.LabelBold,
            modifier = Modifier.padding(
                horizontal = DefaultTheme.Dimensions.CHIP_HORIZONTAL_PADDING,
                vertical = DefaultTheme.Dimensions.CHIP_VERTICAL_PADDING
            )
        )
    }
}

@Composable
fun PokerLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        style = DefaultTheme.Typography.LabelBold,
        color = DefaultTheme.Colors.TextSecondary,
        modifier = modifier
    )
}
package com.snaky.poker.cev.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.snaky.poker.cev.ui.theme.DefaultTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PokerTooltipArea(
    tooltipText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipArea(
        modifier = modifier,
        delayMillis = DefaultTheme.Dimensions.TOOLTIP_DELAY,
        tooltip = {
            Surface(
                color = DefaultTheme.Colors.TooltipBackground,
                contentColor = DefaultTheme.Colors.TooltipOnBackground,
                shape = DefaultTheme.Shapes.Small,
                tonalElevation = DefaultTheme.Dimensions.TOOLTIP_ELEVATION
            ) {
                Text(
                    text = tooltipText,
                    modifier = Modifier.padding(DefaultTheme.Dimensions.TOOLTIP_PADDING),
                    style = DefaultTheme.Typography.Small
                )
            }
        },
        content = content
    )
}
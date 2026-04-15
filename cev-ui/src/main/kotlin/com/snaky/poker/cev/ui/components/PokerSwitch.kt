package com.snaky.poker.cev.ui.components

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.snaky.poker.cev.ui.theme.DefaultTheme

@Composable
fun PokerSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier.scale(0.8f),
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = DefaultTheme.Colors.PrimaryAction,
            checkedThumbColor = Color.White,
            uncheckedTrackColor = DefaultTheme.Colors.DisabledContainer,
            uncheckedBorderColor = DefaultTheme.Colors.Divider
        )
    )
}
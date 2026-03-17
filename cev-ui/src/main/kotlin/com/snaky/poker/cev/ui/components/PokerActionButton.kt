package com.snaky.poker.cev.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.snaky.poker.cev.ui.theme.DefaultTheme

@Composable
fun PokerActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = DefaultTheme.Colors.PrimaryAction,
    contentColor: Color = DefaultTheme.Colors.TextOnAction,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(DefaultTheme.Dimensions.BUTTON_HEIGHT),
        shape = DefaultTheme.Shapes.Large,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = DefaultTheme.Colors.DisabledContainer,
            disabledContentColor = DefaultTheme.Colors.DisabledContent
        )
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(DefaultTheme.Dimensions.FORM_SPACING_SMALL))
        }
        Text(text.uppercase(), style = DefaultTheme.Typography.ButtonLabel)
    }
}
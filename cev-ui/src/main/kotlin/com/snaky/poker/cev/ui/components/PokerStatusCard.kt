package com.snaky.poker.cev.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.snaky.poker.cev.ui.theme.DefaultTheme


data class StatusData(
    val count: Int,
    val label: String,
    val color: Color
)

@Composable
fun PokerStatusCard(
    primaryStatus: StatusData,
    secondaryStatuses: List<StatusData> = emptyList(),
    modifier: Modifier = Modifier
) {
    val isTotalSingle = secondaryStatuses.isEmpty()

    Surface(
        modifier = modifier,
        shape = DefaultTheme.Shapes.Medium,
        border = BorderStroke(DefaultTheme.Dimensions.BORDER_THICKNESS, DefaultTheme.Colors.Divider),
        color = DefaultTheme.Colors.WindowBackground
    ) {
        Row(
            modifier = Modifier.padding(horizontal = DefaultTheme.Dimensions.FORM_SPACING_LARGE),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Statut principal
            PokerStatusIndicator(
                status = primaryStatus,
                isLarge = isTotalSingle
            )

            if (secondaryStatuses.isNotEmpty()) {
                Spacer(Modifier.width(DefaultTheme.Dimensions.SECTION_SPACING * 2))

                if (secondaryStatuses.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.CHIP_VERTICAL_PADDING)) {
                        secondaryStatuses.forEach { status ->
                            PokerStatusIndicator(status = status, isLarge = false)
                        }
                    }
                } else {
                    PokerStatusIndicator(status = secondaryStatuses[0], isLarge = false)
                }
            }
        }
    }
}
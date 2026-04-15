package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.window.WindowDraggableArea
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.snaky.poker.cev.ui.model.LiveSessionUiState
import com.snaky.poker.cev.ui.theme.DefaultTheme

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WindowScope.LiveWidgetView(
    uiState: LiveSessionUiState,
    onDoubleClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .clip(DefaultTheme.Shapes.Medium),
        color = Color.Black.copy(alpha = 0.85f),
        border = BorderStroke(
            DefaultTheme.Dimensions.BORDER_THICKNESS,
            DefaultTheme.Colors.Divider
        )
    ) {
        WindowDraggableArea(
            modifier = Modifier.combinedClickable(
                onClick = { },
                onDoubleClick = onDoubleClick,
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(DefaultTheme.Dimensions.CONTAINER_PADDING / 2),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = uiState.displayDuration,
                    color = Color.White,
                    style = DefaultTheme.Typography.TitleBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${uiState.totalGames} Games",
                    color = Color.White.copy(alpha = 0.7f),
                    style = DefaultTheme.Typography.Small
                )
            }
        }
    }
}
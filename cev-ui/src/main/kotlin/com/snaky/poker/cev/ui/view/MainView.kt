package com.snaky.poker.cev.ui.view

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.* // On passe tout en M3
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaky.poker.cev.ui.model.MainViewModel
import com.snaky.poker.cev.ui.theme.DefaultTheme

enum class AppScreen { RESULTS, VARIANCE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(viewModel: MainViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.RESULTS) }
    val isLocked = viewModel.isNavigationLocked

    Scaffold(
        containerColor = DefaultTheme.Colors.WindowBackground, // M3 utilise containerColor
        topBar = {
            // Utilisation d'une Surface M3 pour la TopBar
            Surface(
                tonalElevation = 0.dp, // Désactive la teinte auto de M3
                shadowElevation = 4.dp,
                color = DefaultTheme.Colors.PrimaryAction
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Titre
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spin-cEV",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "Calculator",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Light),
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }

                    // Navigation
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(32.dp)
                    ) {
                        AppScreen.entries.forEach { screen ->
                            val isSelected = currentScreen == screen
                            val label = if (screen == AppScreen.RESULTS) "Results" else "Variance Simulation"
                            val contentAlpha by animateFloatAsState(if (isLocked) 0.4f else if (isSelected) 1f else 0.7f)

                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .alpha(contentAlpha)
                                    .clickable(enabled = !isLocked) { currentScreen = screen }
                                    .padding(horizontal = 8.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 14.sp)
                                )
                                if (isSelected) {
                                    Box(
                                        Modifier
                                            .padding(top = 4.dp)
                                            .width(24.dp)
                                            .height(3.dp)
                                            .background(Color.White, shape = RoundedCornerShape(1.5.dp))
                                    )
                                } else {
                                    Spacer(Modifier.height(7.dp))
                                }
                            }
                        }
                    }

                    if (isLocked) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp).align(Alignment.CenterEnd),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    ) { padding ->
        // Ici, on utilise une Surface avec tonalElevation = 0.dp pour le contenu
        Surface(
            modifier = Modifier.padding(padding).fillMaxSize(),
            color = DefaultTheme.Colors.WindowBackground,
            tonalElevation = 0.dp
        ) {
            when (currentScreen) {
                AppScreen.RESULTS -> ResultsView(
                    viewModel = viewModel.resultsModel,
                    onBusyState = { isBusy -> viewModel.isNavigationLocked = isBusy }
                )
                AppScreen.VARIANCE -> VarianceSimulationView(
                    viewModel.varianceModel,
                    onBusyState = { isBusy -> viewModel.isNavigationLocked = isBusy }
                )
            }
        }
    }
}
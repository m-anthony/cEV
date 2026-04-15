package com.snaky.poker.cev.ui.view

import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.snaky.poker.cev.ui.LiveMigrationBanner
import com.snaky.poker.cev.ui.MigrationDialogs
import com.snaky.poker.cev.ui.model.MainViewModel
import com.snaky.poker.cev.ui.theme.DefaultTheme
import org.apache.logging.log4j.kotlin.logger
import java.awt.Desktop
import java.net.URI

private enum class AppScreen(
    val displayTitle: String
) {
    RESULTS("Results"),
    VARIANCE("Variance Simulation"),
    LIVE("Live Session (Beta)");
}

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
                        AppScreen.entries
                            .filter { it != AppScreen.LIVE || viewModel.liveSessionModel.enabled }
                            .forEach { screen ->
                                val isSelected = currentScreen == screen
                                val label = screen.displayTitle
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            UpdateBanner(viewModel.latestVersion)
            LiveMigrationBanner(viewModel.migrationViewModel)

            // Ici, on utilise une Surface avec tonalElevation = 0.dp pour le contenu
            Surface(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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

                    AppScreen.LIVE -> LiveSessionView(viewModel.liveSessionModel)

                }
            }

        }
    }

    MigrationDialogs((viewModel.migrationViewModel))
}

@Composable
fun UpdateBanner(latestVersion: String?) {

    // AnimatedVisibility makes the banner slide in smoothly
    AnimatedVisibility(
        visible = latestVersion != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF3CD)) // Light yellow/amber background
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "🚀 a new version ($latestVersion) is available ! ",
                fontSize = 13.sp,
                color = Color(0xFF856404)
            )
            Text(
                text = "Download update",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0056b3),
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    try {
                        Desktop.getDesktop().browse(URI("https://github.com/m-anthony/cEV/releases/latest"))
                    } catch (e: Exception) {
                        logger.warn { "Could not open browser: ${e.message}" }
                    }
                }
            )
        }
    }
}

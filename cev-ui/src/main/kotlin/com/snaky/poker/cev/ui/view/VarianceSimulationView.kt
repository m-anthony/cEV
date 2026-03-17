package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.snaky.poker.cev.ui.model.VarianceViewModel
import com.snaky.poker.cev.ui.theme.DefaultTheme

@Composable
fun VarianceSimulationView(
    viewModel: VarianceViewModel,
    onBusyState: (Boolean) -> Unit
) {
    LaunchedEffect(viewModel.isCalculating) {
        onBusyState(viewModel.isCalculating)
    }

    Row(modifier = Modifier.fillMaxSize()) {
        // Colonne de gauche : Formulaire (Largeur fixe de 350.dp pour le bureau)
        VarianceParameterForm(
            viewModel = viewModel,
            modifier = Modifier.width(350.dp).fillMaxHeight()
        )

        VerticalDivider(
            thickness = DefaultTheme.Dimensions.BORDER_THICKNESS,
            color = MaterialTheme.colorScheme.outlineVariant
        )

        // Colonne de droite : Résultats
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(DefaultTheme.Dimensions.CONTAINER_PADDING),
            contentAlignment = Alignment.TopCenter
        ) {
            val report = viewModel.report
            val lastParams = viewModel.lastAppliedParams

            when {
                // CASE 1: Simulation en cours
                viewModel.isCalculating -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Simulating variance...",
                            style = DefaultTheme.Typography.Header, // Utilisation de ton style Header
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(Modifier.height(DefaultTheme.Dimensions.FORM_SPACING_LARGE))

                        LinearProgressIndicator(
                            progress = { viewModel.simulationProgress },
                            modifier = Modifier
                                .width(300.dp) // On peut laisser 300.dp ici, c'est spécifique à cette barre
                                .height(8.dp),
                            color = DefaultTheme.Colors.PrimaryAction,
                            trackColor = MaterialTheme.colorScheme.primaryContainer,
                            strokeCap = StrokeCap.Round
                        )

                        Spacer(Modifier.height(DefaultTheme.Dimensions.CHIP_SPACING))

                        Text(
                            text = viewModel.progressText,
                            style = DefaultTheme.Typography.FieldLabel,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // CASE 2: Résultats prêts
                report != null && lastParams != null -> {
                    VarianceResultTable(
                        report = report,
                        buyInCents = lastParams.buyInCents,
                        gamesCount = lastParams.gamesCount,
                        displayInBuyIns = viewModel.displayInBuyIns,
                        onToggleDisplayMode = viewModel::toggleDisplayMode
                    )
                }

                // CASE 3: État initial (Idle)
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Assessment,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(DefaultTheme.Dimensions.SECTION_SPACING))
                        Text(
                            text = "Configure parameters and run simulation",
                            style = DefaultTheme.Typography.Header, // Harmonisé avec le reste
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
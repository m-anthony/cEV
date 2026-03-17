package com.snaky.poker.cev.ui.view

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.ui.components.PokerActionButton
import com.snaky.poker.cev.ui.model.VarianceViewModel
import com.snaky.poker.cev.ui.theme.DefaultTheme

// Propagation de l'état d'activation
val LocalFormEnabled = compositionLocalOf { true }

const val DEFAULT_SIMU = 10_000

data class VarianceUiState(
    val gamesCount: Int = 1000,
    val initialStack: Int = 500,
    val cEV: Int = 35,
    val rakeBackPercent: Int = 0,
    val rakeBackThresholdCents: Int = 0,
    val simulationCount: Int = DEFAULT_SIMU,
)

@Composable
fun VarianceParameterForm(
    viewModel: VarianceViewModel,
    modifier: Modifier = Modifier
) {
    var gamesText by remember { mutableStateOf("10000") }
    var stackText by remember { mutableStateOf("500") }
    var cevText by remember { mutableStateOf("") }
    var rbPercentText by remember { mutableStateOf("") }
    var rbThresholdText by remember { mutableStateOf("") }
    var simusText by remember { mutableStateOf(DEFAULT_SIMU.toString()) }

    val currentUiState = remember(gamesText, stackText, cevText, rbPercentText, rbThresholdText, simusText) {
        buildUiState(gamesText, cevText, rbPercentText, rbThresholdText, simusText, stackText)
    }

    val isOutdated = viewModel.isDataOutdated(currentUiState)
    val invalidFields = remember { mutableStateListOf<String>() }
    val canLaunch = invalidFields.isEmpty() && viewModel.currentPayoutScheme != null

    // Centralisation du "Enabled" via le CompositionLocal
    CompositionLocalProvider(LocalFormEnabled provides !viewModel.isCalculating) {
        Column(
            modifier = modifier
                .padding(DefaultTheme.Dimensions.CONTAINER_PADDING)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.FORM_SPACING_LARGE)
        ) {
            Text("Configuration", style = DefaultTheme.Typography.Header)

            Row(horizontalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.FORM_SPACING_SMALL)) {
                NumericFieldM3(
                    label = "Games",
                    value = gamesText,
                    onValueChange = { gamesText = it },
                    modifier = Modifier.weight(1.2f),
                    errorRegistry = invalidFields,
                    mandatory = true
                )
                NumericFieldM3(
                    label = "cEV",
                    value = cevText,
                    onValueChange = { cevText = it },
                    allowNegative = true,
                    modifier = Modifier.weight(0.8f),
                    errorRegistry = invalidFields,
                    maxCharacters = 3,
                    mandatory = true
                )
                NumericFieldM3(
                    label = "Stack",
                    value = stackText,
                    onValueChange = { stackText = it },
                    modifier = Modifier.weight(1f),
                    maxCharacters = 3,
                    errorRegistry = invalidFields,
                    mandatory = true
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.FORM_SPACING_SMALL)) {
                AppDropdownM3(
                    label = "Room",
                    options = Room.entries,
                    selectedOption = viewModel.selectedRoom,
                    expanded = viewModel.isRoomMenuExpanded,
                    onExpandedChange = { viewModel.toggleRoomMenu(it) },
                    onOptionSelected = {
                        viewModel.onRoomSelected(it)
                        viewModel.toggleRoomMenu(false)
                    },
                    optionToString = { it.name },
                    modifier = Modifier.weight(1.5f)
                )
                AppDropdownM3(
                    label = "Buy-in",
                    options = viewModel.availableBuyIns,
                    selectedOption = viewModel.selectedBuyInCents,
                    expanded = viewModel.isBuyInMenuExpanded,
                    onExpandedChange = { viewModel.toggleBuyInMenu(it) },
                    onOptionSelected = {
                        viewModel.onBuyInSelected(it)
                        viewModel.toggleBuyInMenu(false)
                    },
                    modifier = Modifier.weight(1f),
                    optionToString = { formatCents(it) }
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.FORM_SPACING_SMALL)) {
                Text("Rakeback & Bonus", style = DefaultTheme.Typography.SectionLabel, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(DefaultTheme.Dimensions.FORM_SPACING_SMALL)) {
                    NumericFieldM3(
                        label = "Rate (%)",
                        value = rbPercentText,
                        onValueChange = { rbPercentText = it },
                        modifier = Modifier.weight(1f),
                        maxCharacters = 2,
                        errorRegistry = invalidFields
                    )
                    NumericFieldM3(
                        label = "Release every (€)",
                        value = rbThresholdText,
                        onValueChange = { rbThresholdText = it },
                        modifier = Modifier.weight(1.5f),
                        allowDecimals = true,
                        maxCharacters = 5,
                        errorRegistry = invalidFields
                    )
                }
            }

            NumericFieldM3(label = "Simulations count", value = simusText, onValueChange = { simusText = it }, modifier = Modifier.fillMaxWidth(), errorRegistry = invalidFields, mandatory = true)

            Spacer(modifier = Modifier.weight(1f))

            SimulationActionButton(
                isCalculating = viewModel.isCalculating,
                canLaunch = canLaunch,
                isOutdated = isOutdated,
                hasResults = viewModel.report != null,
                onStart = { viewModel.startSimulation(currentUiState) },
                onStop = { viewModel.stopSimulation() }
            )
        }
    }
}

@Composable
fun NumericFieldM3(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    errorRegistry: MutableList<String>,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
    allowDecimals: Boolean = false,
    maxCharacters: Int = 9,
    mandatory: Boolean = false
) {
    val enabled = LocalFormEnabled.current
    val isError = mandatory && (value.toIntOrNull()?.takeUnless { !allowNegative && it == 0 } == null)
    val isOptionalAndEmpty = !mandatory && value.isEmpty()

    LaunchedEffect(isError) {
        if (isError) {
            if (!errorRegistry.contains(label)) errorRegistry.add(label)
        } else {
            errorRegistry.remove(label)
        }
    }

    OutlinedTextField(
        value = value,
        onValueChange = { input ->
            val cleanedInput = input.replace(',', '.')
            val dynamicMax = if (allowNegative && cleanedInput.startsWith("-")) {
                maxCharacters + 1
            } else {
                maxCharacters
            }
            if (cleanedInput.length <= dynamicMax) {

                val regex = when {
                    allowNegative && allowDecimals -> "^-?[0-9]*\\.?[0-9]*$"
                    allowNegative -> "^-?[0-9]*$"
                    allowDecimals -> "^[0-9]*\\.?[0-9]*$"
                    else -> "^[0-9]*$"
                }

                if (cleanedInput.isEmpty() || cleanedInput.matches(Regex(regex))) {
                    onValueChange(cleanedInput)
                }
            }
        },
        label = {
            Text(
                text = label,
                style = DefaultTheme.Typography.FieldLabel,
                color = if (isOptionalAndEmpty) DefaultTheme.Colors.OptionalLabel else DefaultTheme.Colors.TextSecondary
            )
        },
        modifier = modifier,
        enabled = enabled,
        singleLine = true,
        isError = isError,
        shape = DefaultTheme.Shapes.Medium,
        colors = OutlinedTextFieldDefaults.colors(
            errorContainerColor = DefaultTheme.Colors.ErrorBackground,
            errorBorderColor = DefaultTheme.Colors.ErrorOutline,
            // Style discret pour les champs optionnels vides
            unfocusedBorderColor = if (isOptionalAndEmpty) DefaultTheme.Colors.OptionalOutline else MaterialTheme.colorScheme.outline,
            unfocusedContainerColor = DefaultTheme.Colors.Transparent,
            focusedContainerColor = DefaultTheme.Colors.Transparent
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> AppDropdownM3(
    label: String,
    options: List<T>,
    selectedOption: T,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    optionToString: (T) -> String = { it.toString() },
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    val enabled = LocalFormEnabled.current
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { onExpandedChange(it) },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = optionToString(selectedOption),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            enabled = enabled,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = enabled).fillMaxWidth(),
            shape = DefaultTheme.Shapes.Medium
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { onExpandedChange(false) },
            modifier = Modifier.heightIn(max = DefaultTheme.Dimensions.DROPDOWN_MAX_HEIGHT)
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionToString(option)) },
                    onClick = { onOptionSelected(option); onExpandedChange(false)}
                )
            }
        }
    }
}

@Composable
fun SimulationActionButton(
    isCalculating: Boolean,
    canLaunch: Boolean,
    isOutdated: Boolean,
    hasResults: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    if (isCalculating) {
        PokerActionButton(
            text = "STOP SIMULATION",
            onClick = onStop,
            modifier = Modifier.fillMaxWidth(),
            contentColor = DefaultTheme.Colors.TextOnActionTechnical,
            containerColor = DefaultTheme.Colors.ActionTechnicalContainer, // On garde l'accès au Material si spécifique
            icon = Icons.Outlined.Stop
        )
    } else {
        PokerActionButton(
            text = when {
                isOutdated -> "UPDATE RESULTS"
                hasResults -> "RERUN SIMULATION"
                else -> "RUN SIMULATION"
            },
            onClick = onStart,
            enabled = canLaunch,
            modifier = Modifier.fillMaxWidth(),
            icon = if (isOutdated && hasResults) Icons.Outlined.Refresh else Icons.Outlined.PlayArrow
        )
    }
}

private fun buildUiState(gt: String, ct: String, rpt: String, rtt: String, st: String, skt: String) = VarianceUiState(
    gamesCount = gt.toIntOrNull() ?: 0,
    cEV = ct.toIntOrNull() ?: Int.MIN_VALUE,
    rakeBackPercent = rpt.toIntOrNull() ?: 0,
    rakeBackThresholdCents = ((rtt.toFloatOrNull() ?: 0f) * 100).toInt(),
    simulationCount = st.toIntOrNull() ?: DEFAULT_SIMU,
    initialStack = skt.toIntOrNull() ?: 500
)

fun formatCents(cents: Int): String = "%.2f €".format(cents / 100.0)
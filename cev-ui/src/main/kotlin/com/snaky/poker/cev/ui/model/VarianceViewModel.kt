package com.snaky.poker.cev.ui.model

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snaky.poker.cev.core.PayoutSchemeProvider
import com.snaky.poker.cev.core.model.PayoutScheme
import com.snaky.poker.cev.core.model.Room
import com.snaky.poker.cev.ui.VarianceParams
import com.snaky.poker.cev.ui.VarianceReport
import com.snaky.poker.cev.ui.VarianceSimulationEngine
import com.snaky.poker.cev.ui.view.VarianceUiState
import kotlinx.coroutines.*
import org.apache.logging.log4j.kotlin.logger
import java.util.concurrent.atomic.LongAdder
import kotlin.time.measureTimedValue

class VarianceViewModel {
    var selectedRoom by mutableStateOf(Room.entries.first())
        private set
    var isRoomMenuExpanded by mutableStateOf(false)
    var selectedBuyInCents by mutableStateOf(100)
        private set
    var isBuyInMenuExpanded by mutableStateOf(false)
    var isCalculating by mutableStateOf(false)
        private set
    var simulationProgress by mutableStateOf(0f) //from 0f to 1f
        private set

    val progressText by derivedStateOf {
        val total = lastAppliedParams?.simulationCount ?: 0
        val current = (simulationProgress * total).toInt()
        "$current / $total"
    }

    val availableBuyIns by derivedStateOf {
        PayoutSchemeProvider.getBuyInsForRoom(selectedRoom)
    }

    val currentPayoutScheme by derivedStateOf {
        PayoutSchemeProvider.getScheme(selectedRoom, selectedBuyInCents)
        // Retourne null si incohérence -> le bouton sera bloqué
    }

    // Le rapport actuel (null si calcul en cours ou non lancé)
    var report by mutableStateOf<VarianceReport?>(null)
        private set

    // Les paramètres utilisés pour le DERNIER rapport
    var lastAppliedParams by mutableStateOf<VarianceParams?>(null)
        private set

    // Mode d'affichage : true = Buy-ins, false = Monnaie
    var displayInBuyIns by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var simulationJob: Job? = null
    private var progressAdder = LongAdder()


    fun onRoomSelected(room: Room) {
        selectedRoom = room
        // On utilise directement la liste filtrée pour garantir la cohérence immédiate
        val buyIns = PayoutSchemeProvider.getBuyInsForRoom(room)
        if (selectedBuyInCents !in buyIns) {
            selectedBuyInCents = buyIns.firstOrNull() ?: -1 // -1 ou 0 pour marquer l'incohérence
        }
    }

    fun onBuyInSelected(cents: Int) {
        selectedBuyInCents = cents
    }

    fun toggleDisplayMode() { displayInBuyIns = !displayInBuyIns }
    fun toggleRoomMenu(expanded: Boolean) { isRoomMenuExpanded = expanded }
    fun toggleBuyInMenu(expanded: Boolean) { isBuyInMenuExpanded = expanded }

    fun isDataOutdated(currentUiState: VarianceUiState): Boolean {
        val last = lastAppliedParams ?: return false
        val scheme = currentPayoutScheme ?: return true
        val current = buildVarianceParams(scheme, currentUiState)
        return last != current
    }

    //TODO : VarianceUiState doit etre orienté UI (eurs et pas cents ....)
    fun startSimulation(viewParams: VarianceUiState) {
        // On annule un éventuel calcul précédent par sécurité
        report = null
        simulationJob?.cancel()
        simulationProgress = 0f
        progressAdder = LongAdder()

        val payoutScheme = currentPayoutScheme
        if(payoutScheme == null){
            //shouldn't happen, the view must disable the button while there is no valid scheme selected
            logger.error {"no payoutScheme selected, the view should prevent that"}
            return
        }
        val finalParams = buildVarianceParams(payoutScheme, viewParams).also { lastAppliedParams = it }

        simulationJob = scope.launch(Dispatchers.Default) {
            isCalculating = true

            // --- 1. LE TICKER (Observation de la progression) ---
            val tickerJob = launch {
                while (isActive) {
                    // Lecture sans contention via sum()
                    simulationProgress = progressAdder.sum().toFloat() / finalParams.simulationCount
                    delay(32) // ~30 FPS pour l'UI
                }
            }

            try {
                measureTimedValue {
                    report = VarianceSimulationEngine.run(finalParams, progressAdder)
                }.also {
                    this@VarianceViewModel.logger.info { "Simulation took : ${it.duration}" }
                }
            } catch (_: CancellationException) {
                // simulation has been cancelled
            } finally {
                tickerJob.cancel()
                simulationProgress = 1f // On force le 100% à la fin
                isCalculating = false
            }
        }
    }

    private fun buildVarianceParams(
        payoutScheme: PayoutScheme,
        viewParams: VarianceUiState
    ): VarianceParams = VarianceParams(
        buyInCents = selectedBuyInCents,
        payoutScheme = payoutScheme,
        gamesCount = viewParams.gamesCount,
        simulationCount = viewParams.simulationCount,
        cEV = viewParams.cEV,
        initialStack = viewParams.initialStack,
        rakeBackRate = viewParams.rakeBackPercent / 100.0,
        rakeBackThresholdCents = viewParams.rakeBackThresholdCents.let { if(it == 0) selectedBuyInCents else it}
    )

    fun stopSimulation() {
        simulationJob?.cancel()
        lastAppliedParams = null
        report = null

        // isCalculating passera à false via le bloc 'finally' de la coroutine
    }

}

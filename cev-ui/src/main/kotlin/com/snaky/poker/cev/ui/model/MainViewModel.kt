package com.snaky.poker.cev.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.snaky.poker.cev.ui.LiveMigrationViewModel
import com.snaky.poker.cev.ui.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File


class MainViewModel(api: PokerCalculatorAPI) {
    val resultsModel = ResultsViewModel(api)
    val varianceModel = VarianceViewModel()
    val liveSessionModel = LiveSessionViewModel()
    val migrationViewModel = LiveMigrationViewModel()

    var isNavigationLocked by mutableStateOf(false)
    val latestVersion get() = UpdateChecker.latestVersion

    init {

        CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
            UpdateChecker.initialize()
        }

        liveSessionModel.onSessionFinished {
            resultsModel.markUpdateAvailable()
        }
    }
}

//TODO : move all references to cev-core computation there
interface PokerCalculatorAPI {
    val currentSpinCount: Int
    suspend fun calculateFromDirectories(directories: List<File>): ProcessingResults
}


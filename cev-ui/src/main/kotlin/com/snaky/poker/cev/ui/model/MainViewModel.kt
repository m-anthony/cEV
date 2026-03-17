package com.snaky.poker.cev.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File


class MainViewModel(api: PokerCalculatorAPI) {
    val resultsModel = ResultsViewModel(api)
    val varianceModel = VarianceViewModel()
    var isNavigationLocked by mutableStateOf(false)

}

//TODO : move all references to cev-core computation there
interface PokerCalculatorAPI {
    val currentSpinCount: Int
    suspend fun calculateFromDirectories(directories: List<File>): ProcessingResults
}


package com.snaky.poker.cev.ui.model

import com.snaky.poker.cev.ui.config.ConfigurationManager
import com.snaky.poker.cev.ui.config.LiveWidgetConfig

internal class LiveSessionWidgetHandler() {

    fun toggle(enabled: Boolean) = update(if (enabled) LiveWidgetConfig() else null)

    fun updatePosition(x: Int, y: Int) {
        val current = ConfigurationManager.configuration.liveWidgetConfig ?: return
        if (current.x != x || current.y != y) {
            update(current.copy(x = x, y = y))
        }
    }

    private fun update(config: LiveWidgetConfig?) {
        ConfigurationManager.update { it.copy(liveWidgetConfig = config) }
    }
}
package com.theveloper.pixelplay.presentation.navigation

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lets places without a NavController (the full player, song sheets) open a route. The
 * player's navigation effect collects it: it collapses the player first, then navigates.
 */
object RouteRequests {
    private val requests = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val flow: SharedFlow<String> = requests.asSharedFlow()

    fun open(route: String) {
        requests.tryEmit(route)
    }
}

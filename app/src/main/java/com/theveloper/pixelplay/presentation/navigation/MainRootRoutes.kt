package com.theveloper.pixelplay.presentation.navigation

/** Bottom-nav tabs, in bar order. The index drives the slide direction between tabs. */
internal fun isMainRootRoute(route: String?): Boolean = mainRootRouteIndex(route) != null

internal fun mainRootRouteIndex(route: String?): Int? = when (route) {
    Screen.Home.route -> 0
    Screen.Radio.route -> 1
    Screen.Search.route -> 2
    Screen.Friends.route -> 3
    Screen.Library.route -> 4
    else -> null
}

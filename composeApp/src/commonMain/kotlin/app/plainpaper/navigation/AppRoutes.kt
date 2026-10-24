package app.plainpaper.navigation

import kotlinx.serialization.Serializable

sealed interface AppRoute

/** Tab destinations keep the floating tab bar and Scan FAB visible. */
sealed interface TabRoute : AppRoute

@Serializable
data object Home : TabRoute

@Serializable
data object Folders : TabRoute

@Serializable
data object Settings : TabRoute

@Serializable
data object ImportPlaceholder : AppRoute

/** "See Pro" destination until the Pro purchase feature exists. */
@Serializable
data object ProPlaceholder : AppRoute

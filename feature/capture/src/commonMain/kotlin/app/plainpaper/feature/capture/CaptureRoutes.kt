package app.plainpaper.feature.capture

import kotlinx.serialization.Serializable

/** Type-safe routes the app's NavHost registers (contracts/kotlin-api.md). */
@Serializable
data object CameraRoute

@Serializable
data object ImportRoute

@Serializable
data class HandoffRoute(
    val sessionId: String,
)

package com.crazestudio.anisteam.data

data class SourcePolicy(
    val id: String,
    val baseUrl: String,
    val enabled: Boolean = false,
    val authorizedForUse: Boolean = false,
    val allowsAutomatedAccess: Boolean = false,
    val allowsPlaybackIntegration: Boolean = false
)

object SourceRegistry {
    val sources = listOf(
        SourcePolicy(
            id = "animesalt",
            baseUrl = "https://animesalt.cx",
            enabled = true,
            authorizedForUse = false,
            allowsAutomatedAccess = false,
            allowsPlaybackIntegration = false
        )
    )
}

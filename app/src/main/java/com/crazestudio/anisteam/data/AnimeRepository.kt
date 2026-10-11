package com.crazestudio.anisteam.data

class AnimeRepository(private val sources: List<SourcePolicy> = SourceRegistry.sources) {
    fun enabledSources() = sources.filter {
        it.enabled && it.authorizedForUse &&
        it.allowsAutomatedAccess && it.allowsPlaybackIntegration
    }
}

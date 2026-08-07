package com.newbieking.springtestgen.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.newbieking.springtestgen.psi.EndpointMetadata

/**
 * Manages AI provider fallback and degradation strategy.
 *
 * When the primary AI provider fails, the manager tries fallback providers
 * in priority order. If all providers fail, it falls back to deterministic
 * template generation and records the degradation event.
 *
 * Degradation events are:
 * - Logged to the IntelliJ diagnostic log
 * - Emitted as [DegradationEvent] instances for downstream consumers
 *   (e.g., UI notifications, status bar hints)
 *
 * This class is PSI-free and safe to call from any thread.
 */
class AIDegradationManager(private val project: Project?) {

    private val log = Logger.getInstance(AIDegradationManager::class.java)

    /** Recent degradation events, kept for UI notification purposes. */
    private val recentEvents = mutableListOf<DegradationEvent>()
    private val maxEvents = 50

    /**
     * Attempt AI generation with automatic fallback.
     *
     * Tries providers in priority order. Returns the first successful result,
     * or records a degradation event if all providers fail.
     *
     * @param registry the provider registry to source providers from
     * @param prompt resolved prompt text to send
     * @param operation the generation operation to perform on each provider
     * @return the generation result (may be a failure if all providers failed)
     */
    suspend fun generateWithFallback(
        registry: AIProviderRegistry,
        prompt: String,
        operation: suspend (AIProvider, String) -> AIGenerationResult
    ): AIGenerationResult {
        val providers = registry.getEnabledProviders()
        if (providers.isEmpty()) {
            log.warn("No AI providers registered; cannot attempt generation")
            return AIGenerationResult(
                content = null,
                providerId = "none",
                success = false,
                errorMessage = "No AI providers registered"
            )
        }

        var lastResult: AIGenerationResult? = null
        for ((index, provider) in providers.withIndex()) {
            if (!provider.isAvailable()) {
                log.info("Skipping unavailable provider: ${provider.id} (${provider.displayName})")
                continue
            }

            log.info("Attempting AI generation with provider: ${provider.id} (priority ${index})")
            val result = operation(provider, prompt)

            if (result.success && result.content != null) {
                if (index > 0) {
                    // We fell back to a non-primary provider
                    log.info("Successfully fell back to provider ${provider.id} after primary failure")
                }
                return result
            }

            // This provider failed; record and try the next one
            lastResult = result
            log.warn("Provider ${provider.id} failed: ${result.errorMessage}")

            // Record degradation event if there's a next provider to try
            val nextProvider = providers.getOrNull(index + 1)
            if (nextProvider != null) {
                recordEvent(DegradationEvent(
                    failedProviderId = provider.id,
                    failureReason = result.errorMessage ?: "Unknown error",
                    fallbackProviderId = nextProvider.id,
                    fallbackType = FallbackType.ALTERNATE_PROVIDER
                ))
            }
        }

        // All providers failed
        val finalError = lastResult?.errorMessage ?: "All providers failed"
        log.warn("All AI providers failed. Falling back to deterministic template. Last error: $finalError")

        recordEvent(DegradationEvent(
            failedProviderId = lastResult?.providerId ?: "unknown",
            failureReason = finalError,
            fallbackProviderId = null,
            fallbackType = FallbackType.DETERMINISTIC_TEMPLATE
        ))

        return AIGenerationResult(
            content = null,
            providerId = lastResult?.providerId ?: "none",
            success = false,
            errorMessage = "All providers failed; fallback to deterministic template. Last error: $finalError"
        )
    }

    /**
     * Attempt mock request body generation with fallback.
     *
     * @param registry the provider registry
     * @param metadata endpoint metadata
     * @param prompt resolved prompt text
     * @return generation result
     */
    suspend fun generateMockRequestBodyWithFallback(
        registry: AIProviderRegistry,
        metadata: EndpointMetadata,
        prompt: String
    ): AIGenerationResult {
        return generateWithFallback(registry, prompt) { provider, p ->
            provider.generateMockRequestBody(metadata, p)
        }
    }

    /**
     * Attempt expected response generation with fallback.
     *
     * @param registry the provider registry
     * @param metadata endpoint metadata
     * @param prompt resolved prompt text
     * @return generation result
     */
    suspend fun generateExpectedResponseWithFallback(
        registry: AIProviderRegistry,
        metadata: EndpointMetadata,
        prompt: String
    ): AIGenerationResult {
        return generateWithFallback(registry, prompt) { provider, p ->
            provider.generateExpectedResponse(metadata, p)
        }
    }

    /**
     * Attempt generic prompt-based generation with fallback.
     *
     * @param registry the provider registry
     * @param prompt resolved prompt text
     * @return generation result
     */
    suspend fun generateFromPromptWithFallback(
        registry: AIProviderRegistry,
        prompt: String
    ): AIGenerationResult {
        return generateWithFallback(registry, prompt) { provider, p ->
            provider.generateFromPrompt(p)
        }
    }

    /**
     * Get recent degradation events for UI display.
     * Returns events in chronological order (most recent last).
     */
    fun getRecentEvents(): List<DegradationEvent> = recentEvents.toList()

    /**
     * Get the most recent degradation event, or null if none.
     */
    fun getLatestEvent(): DegradationEvent? = recentEvents.lastOrNull()

    /**
     * Clear all recorded degradation events.
     */
    fun clearEvents() {
        recentEvents.clear()
    }

    /**
     * Check if a degradation has occurred recently.
     * @param withinMs time window in milliseconds (default: 5 minutes)
     */
    fun hasDegradedRecently(withinMs: Long = 300_000): Boolean {
        val cutoff = System.currentTimeMillis() - withinMs
        return recentEvents.any { it.timestampMs >= cutoff }
    }

    private fun recordEvent(event: DegradationEvent) {
        recentEvents.add(event)
        if (recentEvents.size > maxEvents) {
            recentEvents.removeAt(0)
        }
        log.info("Degradation event: provider=${event.failedProviderId}, reason=${event.failureReason}, fallback=${event.fallbackProviderId ?: event.fallbackType.name}")
    }

    companion object {
        fun getInstance(project: Project): AIDegradationManager {
            return project.getService(AIDegradationManager::class.java)
        }
    }
}
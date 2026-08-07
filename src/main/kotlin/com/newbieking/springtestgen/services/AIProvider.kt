package com.newbieking.springtestgen.services

import com.intellij.openapi.project.Project
import com.newbieking.springtestgen.psi.EndpointMetadata

/**
 * Configuration for a single AI provider instance.
 *
 * Each provider has its own endpoint, model, API key, timeout, and retry settings.
 * The [priority] field determines fallback order: lower values are tried first.
 */
data class AIProviderConfig(
    val id: String,
    val displayName: String,
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val timeoutSeconds: Int = 30,
    val maxRetries: Int = 1,
    val priority: Int = 0,
    val enabled: Boolean = true
) {
    companion object {
        /** Create a default OpenAI-compatible provider config. */
        fun openaiDefault(
            apiKey: String = "",
            model: String = "gpt-3.5-turbo",
            baseUrl: String = "https://api.openai.com/v1"
        ): AIProviderConfig = AIProviderConfig(
            id = "openai",
            displayName = "OpenAI",
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            priority = 0
        )

        /** Create a default Anthropic-compatible provider config. */
        fun anthropicDefault(
            apiKey: String = "",
            model: String = "claude-3-haiku-20240307",
            baseUrl: String = "https://api.anthropic.com/v1"
        ): AIProviderConfig = AIProviderConfig(
            id = "anthropic",
            displayName = "Anthropic",
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            priority = 1
        )
    }
}

/**
 * Result of an AI generation attempt from a specific provider.
 *
 * Tracks which provider was used and whether the call succeeded,
 * enabling the degradation manager to make informed fallback decisions.
 */
data class AIGenerationResult(
    val content: String?,
    val providerId: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val latencyMs: Long = 0
)

/**
 * Abstraction for an AI provider that can generate content via prompts.
 *
 * Implementations are PSI-free: they receive only serialisable data
 * (strings, metadata DTOs) and never access the IntelliJ PSI tree.
 */
interface AIProvider {
    /** Unique identifier matching [AIProviderConfig.id]. */
    val id: String

    /** Human-readable name for logging and UI. */
    val displayName: String

    /**
     * Check whether this provider is currently available
     * (has valid configuration and can accept requests).
     */
    fun isAvailable(): Boolean

    /**
     * Generate a mock request body JSON for the given endpoint metadata.
     *
     * @param metadata endpoint metadata (PSI-free DTO)
     * @param prompt resolved prompt text to send to the AI
     * @return generation result indicating success/failure and content
     */
    suspend fun generateMockRequestBody(metadata: EndpointMetadata, prompt: String): AIGenerationResult

    /**
     * Generate an expected response JSON for the given endpoint metadata.
     *
     * @param metadata endpoint metadata (PSI-free DTO)
     * @param prompt resolved prompt text to send to the AI
     * @return generation result indicating success/failure and content
     */
    suspend fun generateExpectedResponse(metadata: EndpointMetadata, prompt: String): AIGenerationResult

    /**
     * Generic prompt-based generation for AI-full mode.
     *
     * @param prompt resolved prompt text
     * @return generation result
     */
    suspend fun generateFromPrompt(prompt: String): AIGenerationResult
}

/**
 * Event emitted when a degradation occurs.
 * Used for logging and user-facing notifications.
 */
data class DegradationEvent(
    val timestampMs: Long = System.currentTimeMillis(),
    val failedProviderId: String,
    val failureReason: String,
    val fallbackProviderId: String?,
    val fallbackType: FallbackType
)

enum class FallbackType {
    /** Fell back to another AI provider. */
    ALTERNATE_PROVIDER,
    /** All providers failed; fell back to deterministic template. */
    DETERMINISTIC_TEMPLATE,
    /** Generation skipped entirely due to no available path. */
    SKIPPED
}
package com.newbieking.springtestgen.services

import com.newbieking.springtestgen.prompt.PromptTemplateService
import com.newbieking.springtestgen.psi.EndpointMetadata

/**
 * AI generation service interface.
 *
 * Provides methods for AI-assisted test generation with automatic
 * provider fallback and response validation.
 *
 * Implementations should use [AIProviderRegistry] and [AIDegradationManager]
 * for multi-provider support and degradation handling.
 */
interface AIGenerationService {

    /**
     * Generate a mock request body JSON sample for the given endpoint.
     *
     * Uses the provider registry with automatic fallback.
     * Returns null if all providers fail (deterministic fallback should be used).
     *
     * @param metadata endpoint metadata (PSI-free DTO)
     * @return JSON string, or null if generation failed
     */
    suspend fun generateMockRequestBody(metadata: EndpointMetadata): String?

    /**
     * Generate an expected response JSON for the given endpoint.
     *
     * Uses the provider registry with automatic fallback.
     * Returns null if all providers fail.
     *
     * @param metadata endpoint metadata (PSI-free DTO)
     * @return JSON string, or null if generation failed
     */
    suspend fun generateExpectedResponse(metadata: EndpointMetadata): String?

    /**
     * Generate a full test class using AI (AI-full mode).
     *
     * The generated code is validated using [AIResponseValidator] before returning.
     * If validation fails, returns a [AIGenerationResult] with success=false and
     * actionable error messages.
     *
     * @param prompt resolved prompt text for full test class generation
     * @param expectedPackage expected package name for validation
     * @param expectedClassName expected test class name for validation
     * @param requiredImports list of import patterns required in the output
     * @return generation result with validated content or error details
     */
    suspend fun generateFullTestClass(
        prompt: String,
        expectedPackage: String,
        expectedClassName: String,
        requiredImports: List<String> = emptyList()
    ): AIGenerationResult

    /**
     * Check if AI generation is currently available
     * (at least one provider is registered and enabled).
     */
    fun isAvailable(): Boolean

    /**
     * Get the most recent degradation event, if any.
     * Useful for showing user notifications.
     */
    fun getLatestDegradationEvent(): DegradationEvent?
}

/**
 * Default implementation of [AIGenerationService] that integrates
 * with [AIProviderRegistry], [AIDegradationManager], and [PromptTemplateService].
 */
class DefaultAIGenerationService(
    private val registry: AIProviderRegistry,
    private val degradationManager: AIDegradationManager,
    private val promptService: PromptTemplateService = PromptTemplateService(),
    private val validator: AIResponseValidator = AIResponseValidator(),
    private val project: com.intellij.openapi.project.Project? = null
) : AIGenerationService {

    override fun isAvailable(): Boolean = registry.hasAvailableProvider()

    override fun getLatestDegradationEvent(): DegradationEvent? =
        degradationManager.getLatestEvent()

    override suspend fun generateMockRequestBody(metadata: EndpointMetadata): String? {
        val bodyType = metadata.requestBodyType ?: return null
        val resolved = promptService.resolve(
            PromptTemplateService.CONTROLLER_MOCK_REQUEST_BODY,
            mapOf("bodyType" to bodyType)
        )
        val result = degradationManager.generateMockRequestBodyWithFallback(
            registry, metadata, resolved.text
        )
        if (!result.success) {
            notifyDegradationIfNeeded()
        }
        return if (result.success) result.content else null
    }

    override suspend fun generateExpectedResponse(metadata: EndpointMetadata): String? {
        val resolved = promptService.resolve(
            PromptTemplateService.CONTROLLER_EXPECTED_RESPONSE,
            mapOf(
                "httpMethod" to metadata.httpMethod.name,
                "path" to metadata.path
            )
        )
        val result = degradationManager.generateExpectedResponseWithFallback(
            registry, metadata, resolved.text
        )
        if (!result.success) {
            notifyDegradationIfNeeded()
        }
        return if (result.success) result.content else null
    }

    override suspend fun generateFullTestClass(
        prompt: String,
        expectedPackage: String,
        expectedClassName: String,
        requiredImports: List<String>
    ): AIGenerationResult {
        val result = degradationManager.generateFromPromptWithFallback(registry, prompt)

        if (!result.success || result.content == null) {
            notifyDegradationIfNeeded()
            return result
        }

        // Validate the AI-generated code before accepting
        val code = validator.stripMarkdownFences(result.content)
        val validation = validator.validate(code, expectedPackage, expectedClassName, requiredImports)

        if (!validation.valid) {
            project?.let { AIDegradationNotifier.notifyValidationFailure(it, validation.errorSummary()) }
            return AIGenerationResult(
                content = null,
                providerId = result.providerId,
                success = false,
                errorMessage = "AI response validation failed: ${validation.errorSummary()}"
            )
        }

        return AIGenerationResult(
            content = code,
            providerId = result.providerId,
            success = true,
            latencyMs = result.latencyMs
        )
    }

    private fun notifyDegradationIfNeeded() {
        val event = degradationManager.getLatestEvent() ?: return
        project?.let { AIDegradationNotifier.notifyDegradation(it, event) }
    }
}
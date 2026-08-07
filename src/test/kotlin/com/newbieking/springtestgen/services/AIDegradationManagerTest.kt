package com.newbieking.springtestgen.services

import com.newbieking.springtestgen.psi.EndpointMetadata
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for AIDegradationManager — fallback chain execution and degradation event tracking.
 */
class AIDegradationManagerTest {

    /** Provider that always succeeds */
    private class SuccessProvider(
        override val id: String,
        override val displayName: String = "Success-$id"
    ) : AIProvider {

        override fun isAvailable(): Boolean = true

        override suspend fun generateMockRequestBody(
            metadata: EndpointMetadata,
            prompt: String
        ) = AIGenerationResult(
            content = """{ "mock": "$id" }""",
            providerId = id, success = true, errorMessage = null, latencyMs = 5
        )

        override suspend fun generateExpectedResponse(
            metadata: EndpointMetadata,
            prompt: String
        ) = AIGenerationResult(
            content = """{ "expected": "$id" }""",
            providerId = id, success = true, errorMessage = null, latencyMs = 5
        )

        override suspend fun generateFromPrompt(prompt: String) = AIGenerationResult(
            content = "result from $id",
            providerId = id, success = true, errorMessage = null, latencyMs = 5
        )
    }

    /**
     * Provider that is available but returns failed results.
     * This simulates a provider that is configured but encounters API errors.
     */
    private class FailingProvider(
        override val id: String,
        override val displayName: String = "Failing-$id",
        private val errorMsg: String = "API error: rate limit exceeded"
    ) : AIProvider {

        override fun isAvailable(): Boolean = true  // Available but will fail on actual calls

        override suspend fun generateMockRequestBody(
            metadata: EndpointMetadata,
            prompt: String
        ) = AIGenerationResult(
            content = null, providerId = id, success = false,
            errorMessage = errorMsg, latencyMs = 100
        )

        override suspend fun generateExpectedResponse(
            metadata: EndpointMetadata,
            prompt: String
        ) = AIGenerationResult(
            content = null, providerId = id, success = false,
            errorMessage = errorMsg, latencyMs = 100
        )

        override suspend fun generateFromPrompt(prompt: String) = AIGenerationResult(
            content = null, providerId = id, success = false,
            errorMessage = errorMsg, latencyMs = 100
        )
    }

    /** Provider that is unavailable (e.g., not configured or offline) */
    private class UnavailableProvider(
        override val id: String,
        override val displayName: String = "Unavailable-$id"
    ) : AIProvider {

        override fun isAvailable(): Boolean = false

        override suspend fun generateMockRequestBody(
            metadata: EndpointMetadata, prompt: String
        ) = AIGenerationResult(
            content = null, providerId = id, success = false,
            errorMessage = "Provider unavailable", latencyMs = 0
        )

        override suspend fun generateExpectedResponse(
            metadata: EndpointMetadata, prompt: String
        ) = AIGenerationResult(
            content = null, providerId = id, success = false,
            errorMessage = "Provider unavailable", latencyMs = 0
        )

        override suspend fun generateFromPrompt(prompt: String) = AIGenerationResult(
            content = null, providerId = id, success = false,
            errorMessage = "Provider unavailable", latencyMs = 0
        )
    }

    private fun makeConfig(id: String, priority: Int = 0) = AIProviderConfig(
        id = id, displayName = "Provider $id",
        baseUrl = "https://api.$id.example.com", apiKey = "key-$id",
        model = "model-$id", priority = priority
    )

    // --- Primary success tests ---

    @Test
    fun `generateFromPromptWithFallback returns primary result when primary succeeds`() {
        val registry = AIProviderRegistry(null)
        registry.register(SuccessProvider("openai"), makeConfig("openai", priority = 0))
        registry.register(SuccessProvider("anthropic"), makeConfig("anthropic", priority = 1))

        val manager = AIDegradationManager(null)
        val result = runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("openai", result.providerId)
    }

    // --- Fallback tests ---

    @Test
    fun `generateFromPromptWithFallback falls back to secondary when primary fails`() {
        val registry = AIProviderRegistry(null)
        registry.register(FailingProvider("openai"), makeConfig("openai", priority = 0))
        registry.register(SuccessProvider("anthropic"), makeConfig("anthropic", priority = 1))

        val manager = AIDegradationManager(null)
        val result = runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        assertNotNull(result)
        assertTrue(result.success)
        assertEquals("anthropic", result.providerId)
    }

    @Test
    fun `generateFromPromptWithFallback records degradation event on fallback`() {
        val registry = AIProviderRegistry(null)
        registry.register(FailingProvider("openai"), makeConfig("openai", priority = 0))
        registry.register(SuccessProvider("anthropic"), makeConfig("anthropic", priority = 1))

        val manager = AIDegradationManager(null)
        runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        val events = manager.getRecentEvents()
        assertTrue(events.isNotEmpty(), "Expected at least one degradation event")
        assertEquals(FallbackType.ALTERNATE_PROVIDER, events[0].fallbackType)
        assertEquals("openai", events[0].failedProviderId)
    }

    // --- All providers fail tests ---

    @Test
    fun `generateFromPromptWithFallback returns failure when all providers fail`() {
        val registry = AIProviderRegistry(null)
        registry.register(FailingProvider("openai"), makeConfig("openai", priority = 0))
        registry.register(FailingProvider("anthropic"), makeConfig("anthropic", priority = 1))

        val manager = AIDegradationManager(null)
        val result = runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        assertNotNull(result)
        assertFalse(result.success)
    }

    @Test
    fun `generateFromPromptWithFallback records DETERMINISTIC_TEMPLATE when all fail`() {
        val registry = AIProviderRegistry(null)
        registry.register(FailingProvider("openai"), makeConfig("openai", priority = 0))

        val manager = AIDegradationManager(null)
        runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        val events = manager.getRecentEvents()
        assertTrue(events.isNotEmpty())
        val lastEvent = events.last()
        assertEquals(FallbackType.DETERMINISTIC_TEMPLATE, lastEvent.fallbackType)
    }

    // --- hasDegradedRecently tests ---

    @Test
    fun `hasDegradedRecently returns true after fallback event`() {
        val registry = AIProviderRegistry(null)
        registry.register(FailingProvider("openai"), makeConfig("openai", priority = 0))
        registry.register(SuccessProvider("anthropic"), makeConfig("anthropic", priority = 1))

        val manager = AIDegradationManager(null)
        runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        assertTrue(manager.hasDegradedRecently())
    }

    @Test
    fun `hasDegradedRecently returns false when no degradation`() {
        val registry = AIProviderRegistry(null)
        registry.register(SuccessProvider("openai"), makeConfig("openai", priority = 0))

        val manager = AIDegradationManager(null)
        runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        assertFalse(manager.hasDegradedRecently())
    }

    // --- Event history tests ---

    @Test
    fun `getRecentEvents returns events in chronological order`() {
        val registry = AIProviderRegistry(null)
        registry.register(FailingProvider("openai"), makeConfig("openai", priority = 0))
        registry.register(FailingProvider("anthropic"), makeConfig("anthropic", priority = 1))

        val manager = AIDegradationManager(null)
        runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        val events = manager.getRecentEvents()
        assertTrue(events.size >= 2, "Expected at least 2 events for 2 failed providers")
        assertEquals("openai", events[0].failedProviderId)
        assertEquals(FallbackType.ALTERNATE_PROVIDER, events[0].fallbackType)
        assertEquals(FallbackType.DETERMINISTIC_TEMPLATE, events.last().fallbackType)
    }

    // --- No providers tests ---

    @Test
    fun `generateFromPromptWithFallback handles empty registry`() {
        val registry = AIProviderRegistry(null)
        val manager = AIDegradationManager(null)

        val result = runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        assertNotNull(result)
        assertFalse(result.success)
    }

    // --- Unavailable provider skipped tests ---

    @Test
    fun `unavailable provider is skipped in fallback chain`() {
        val registry = AIProviderRegistry(null)
        registry.register(UnavailableProvider("unavailable"), makeConfig("unavailable", priority = 0))
        registry.register(SuccessProvider("backup"), makeConfig("backup", priority = 1))

        val manager = AIDegradationManager(null)
        val result = runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        assertTrue(result.success)
        assertEquals("backup", result.providerId)
    }

    @Test
    fun `unavailable provider does not record degradation event`() {
        val registry = AIProviderRegistry(null)
        registry.register(UnavailableProvider("unavailable"), makeConfig("unavailable", priority = 0))
        registry.register(SuccessProvider("backup"), makeConfig("backup", priority = 1))

        val manager = AIDegradationManager(null)
        runBlocking { manager.generateFromPromptWithFallback(registry, "test prompt") }

        assertFalse(manager.hasDegradedRecently())
    }
}
package com.newbieking.springtestgen.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AIProviderConfigTest {

    @Test
    fun `openaiDefault creates correct default config`() {
        val config = AIProviderConfig.openaiDefault()
        assertEquals("openai", config.id)
        assertEquals("OpenAI", config.displayName)
        assertEquals("https://api.openai.com/v1", config.baseUrl)
        assertEquals("", config.apiKey)
        assertEquals("gpt-3.5-turbo", config.model)
        assertEquals(0, config.priority)
        assertTrue(config.enabled)
    }

    @Test
    fun `openaiDefault accepts custom parameters`() {
        val config = AIProviderConfig.openaiDefault(
            apiKey = "sk-test",
            model = "gpt-4",
            baseUrl = "https://custom.api.com/v1"
        )
        assertEquals("sk-test", config.apiKey)
        assertEquals("gpt-4", config.model)
        assertEquals("https://custom.api.com/v1", config.baseUrl)
    }

    @Test
    fun `anthropicDefault creates correct default config`() {
        val config = AIProviderConfig.anthropicDefault()
        assertEquals("anthropic", config.id)
        assertEquals("Anthropic", config.displayName)
        assertEquals("https://api.anthropic.com/v1", config.baseUrl)
        assertEquals("", config.apiKey)
        assertEquals("claude-3-haiku-20240307", config.model)
        assertEquals(1, config.priority)
    }

    @Test
    fun `custom provider config has correct defaults`() {
        val config = AIProviderConfig(
            id = "custom",
            displayName = "Custom Provider",
            baseUrl = "https://custom.api.com",
            apiKey = "key",
            model = "custom-model"
        )
        assertEquals(30, config.timeoutSeconds)
        assertEquals(1, config.maxRetries)
        assertEquals(0, config.priority)
        assertTrue(config.enabled)
    }

    @Test
    fun `AIGenerationResult success has correct fields`() {
        val result = AIGenerationResult(
            content = "test content",
            providerId = "openai",
            success = true,
            latencyMs = 100
        )
        assertEquals("test content", result.content)
        assertEquals("openai", result.providerId)
        assertTrue(result.success)
        assertEquals(100, result.latencyMs)
        assertEquals(null, result.errorMessage)
    }

    @Test
    fun `AIGenerationResult failure has error message`() {
        val result = AIGenerationResult(
            content = null,
            providerId = "openai",
            success = false,
            errorMessage = "Connection timeout"
        )
        assertEquals(null, result.content)
        assertEquals("Connection timeout", result.errorMessage)
    }

    @Test
    fun `DegradationEvent records correct fallback type`() {
        val event = DegradationEvent(
            failedProviderId = "openai",
            failureReason = "Timeout",
            fallbackProviderId = "anthropic",
            fallbackType = FallbackType.ALTERNATE_PROVIDER
        )
        assertEquals("openai", event.failedProviderId)
        assertEquals(FallbackType.ALTERNATE_PROVIDER, event.fallbackType)
        assertEquals("anthropic", event.fallbackProviderId)
    }

    @Test
    fun `DegradationEvent deterministic fallback has no provider`() {
        val event = DegradationEvent(
            failedProviderId = "openai",
            failureReason = "All providers failed",
            fallbackProviderId = null,
            fallbackType = FallbackType.DETERMINISTIC_TEMPLATE
        )
        assertEquals(null, event.fallbackProviderId)
        assertEquals(FallbackType.DETERMINISTIC_TEMPLATE, event.fallbackType)
    }
}
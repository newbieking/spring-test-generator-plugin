package com.newbieking.springtestgen.services

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestPolicyConfigTest {

    // --- GenerationMode ---

    @Test
    fun `GenerationMode fromId returns correct enum for known IDs`() {
        assertEquals(GenerationMode.DETERMINISTIC, GenerationMode.fromId("deterministic"))
        assertEquals(GenerationMode.AI_ASSISTED, GenerationMode.fromId("ai-assisted"))
        assertEquals(GenerationMode.AI_FULL, GenerationMode.fromId("ai-full"))
    }

    @Test
    fun `GenerationMode fromId defaults to DETERMINISTIC for unknown ID`() {
        assertEquals(GenerationMode.DETERMINISTIC, GenerationMode.fromId("unknown"))
        assertEquals(GenerationMode.DETERMINISTIC, GenerationMode.fromId(""))
    }

    @Test
    fun `GenerationMode has three entries`() {
        assertEquals(3, GenerationMode.entries.size)
    }

    // --- TestFrameworkOption ---

    @Test
    fun `TestFrameworkOption fromId returns correct enum for known IDs`() {
        assertEquals(TestFrameworkOption.MOCK_MVC, TestFrameworkOption.fromId("MOCK_MVC"))
        assertEquals(TestFrameworkOption.WEB_TEST_CLIENT, TestFrameworkOption.fromId("WEB_TEST_CLIENT"))
        assertEquals(TestFrameworkOption.REST_ASSURED, TestFrameworkOption.fromId("REST_ASSURED"))
    }

    @Test
    fun `TestFrameworkOption fromId defaults to MOCK_MVC for unknown ID`() {
        assertEquals(TestFrameworkOption.MOCK_MVC, TestFrameworkOption.fromId("unknown"))
    }

    @Test
    fun `only MOCK_MVC has generatorAvailable true`() {
        assertTrue(TestFrameworkOption.MOCK_MVC.generatorAvailable)
        assertFalse(TestFrameworkOption.WEB_TEST_CLIENT.generatorAvailable)
        assertFalse(TestFrameworkOption.REST_ASSURED.generatorAvailable)
    }

    @Test
    fun `available returns only frameworks with implemented generators`() {
        val available = TestFrameworkOption.available()
        assertEquals(1, available.size)
        assertEquals(TestFrameworkOption.MOCK_MVC, available[0])
    }

    // --- SettingsService State defaults ---

    @Test
    fun `State defaults match expected values`() {
        val state = SettingsService.State()
        assertEquals("https://api.openai.com/v1", state.baseUrl)
        assertEquals("", state.apiKey)
        assertEquals("gpt-3.5-turbo", state.model)
        assertTrue(state.enableAI)
        assertEquals(GenerationMode.DETERMINISTIC.id, state.generationMode)
        assertEquals(TestFrameworkOption.MOCK_MVC.id, state.defaultTestFramework)
        assertFalse(state.autoWriteEnabled)
        assertTrue(state.previewBeforeWrite)
        assertFalse(state.enableSecurityScenarios)
        assertFalse(state.enableIdempotencyScenarios)
        assertFalse(state.enableConcurrencyScenarios)
        assertFalse(state.enableStateMachineScenarios)
    }

    @Test
    fun `isAnyRiskScenarioEnabled returns false when all toggles are off`() {
        val service = SettingsService()
        assertFalse(service.isAnyRiskScenarioEnabled())
    }

    @Test
    fun `isAnyRiskScenarioEnabled returns true when any scenario toggle is on`() {
        val service = SettingsService()
        service.setPolicy(
            generationMode = GenerationMode.DETERMINISTIC.id,
            defaultTestFramework = TestFrameworkOption.MOCK_MVC.id,
            autoWriteEnabled = false,
            previewBeforeWrite = true,
            enableSecurityScenarios = true,
            enableIdempotencyScenarios = false,
            enableConcurrencyScenarios = false,
            enableStateMachineScenarios = false
        )
        assertTrue(service.isAnyRiskScenarioEnabled())
    }

    @Test
    fun `getGenerationMode and getDefaultTestFramework return correct enums`() {
        val service = SettingsService()
        assertEquals(GenerationMode.DETERMINISTIC, service.getGenerationMode())
        assertEquals(TestFrameworkOption.MOCK_MVC, service.getDefaultTestFramework())
    }

    @Test
    fun `setPolicy updates all policy fields`() {
        val service = SettingsService()
        service.setPolicy(
            generationMode = GenerationMode.AI_ASSISTED.id,
            defaultTestFramework = TestFrameworkOption.MOCK_MVC.id,
            autoWriteEnabled = true,
            previewBeforeWrite = false,
            enableSecurityScenarios = true,
            enableIdempotencyScenarios = true,
            enableConcurrencyScenarios = false,
            enableStateMachineScenarios = false
        )
        val config = service.getConfig()
        assertEquals(GenerationMode.AI_ASSISTED.id, config.generationMode)
        assertTrue(config.autoWriteEnabled)
        assertFalse(config.previewBeforeWrite)
        assertTrue(config.enableSecurityScenarios)
        assertTrue(config.enableIdempotencyScenarios)
    }
}
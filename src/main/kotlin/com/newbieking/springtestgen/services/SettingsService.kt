package com.newbieking.springtestgen.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent project-level settings for the Spring Test Generator plugin.
 *
 * Covers both AI provider configuration and test generation policy.
 */
@Service(Service.Level.PROJECT)
@State(name = "SpringTestGeneratorSettings", storages = [Storage("springTestGenerator.xml")])
class SettingsService : PersistentStateComponent<SettingsService.State> {

    data class State(
        // --- AI Provider ---
        var baseUrl: String = "https://api.openai.com/v1",
        var apiKey: String = "",
        var model: String = "gpt-3.5-turbo",
        var enableAI: Boolean = true,

        // --- Test Generation Policy ---
        var generationMode: String = GenerationMode.DETERMINISTIC.id,
        var defaultTestFramework: String = TestFrameworkOption.MOCK_MVC.id,
        var autoWriteEnabled: Boolean = false,
        var previewBeforeWrite: Boolean = true,

        // --- Scenario toggles ---
        var enableSecurityScenarios: Boolean = false,
        var enableIdempotencyScenarios: Boolean = false,
        var enableConcurrencyScenarios: Boolean = false,
        var enableStateMachineScenarios: Boolean = false
    )

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getConfig(): State = myState

    fun setConfig(baseUrl: String, apiKey: String, model: String, enableAI: Boolean) {
        myState.baseUrl = baseUrl
        myState.apiKey = apiKey
        myState.model = model
        myState.enableAI = enableAI
    }

    /** Update generation policy fields. */
    fun setPolicy(
        generationMode: String,
        defaultTestFramework: String,
        autoWriteEnabled: Boolean,
        previewBeforeWrite: Boolean,
        enableSecurityScenarios: Boolean,
        enableIdempotencyScenarios: Boolean,
        enableConcurrencyScenarios: Boolean,
        enableStateMachineScenarios: Boolean
    ) {
        myState.generationMode = generationMode
        myState.defaultTestFramework = defaultTestFramework
        myState.autoWriteEnabled = autoWriteEnabled
        myState.previewBeforeWrite = previewBeforeWrite
        myState.enableSecurityScenarios = enableSecurityScenarios
        myState.enableIdempotencyScenarios = enableIdempotencyScenarios
        myState.enableConcurrencyScenarios = enableConcurrencyScenarios
        myState.enableStateMachineScenarios = enableStateMachineScenarios
    }

    /** Convenience: get the resolved generation mode enum. */
    fun getGenerationMode(): GenerationMode =
        GenerationMode.fromId(myState.generationMode)

    /** Convenience: get the resolved default test framework enum. */
    fun getDefaultTestFramework(): TestFrameworkOption =
        TestFrameworkOption.fromId(myState.defaultTestFramework)

    /** Whether any risk scenario category is enabled. */
    fun isAnyRiskScenarioEnabled(): Boolean =
        myState.enableSecurityScenarios ||
            myState.enableIdempotencyScenarios ||
            myState.enableConcurrencyScenarios ||
            myState.enableStateMachineScenarios

    companion object {
        fun getInstance(project: Project): SettingsService {
            return project.getService(SettingsService::class.java)
        }
    }
}

/**
 * Generation mode: deterministic-only, AI-assisted (AI enhances deterministic), or AI-full (AI generates from scratch).
 */
enum class GenerationMode(val id: String, val displayName: String) {
    DETERMINISTIC("deterministic", "Deterministic Only"),
    AI_ASSISTED("ai-assisted", "AI-Assisted"),
    AI_FULL("ai-full", "AI Full Generation");

    companion object {
        private val byId = entries.associateBy { it.id }
        fun fromId(id: String): GenerationMode = byId[id] ?: DETERMINISTIC
    }
}

/**
 * Selectable test output frameworks.
 * Only frameworks with an implemented generator should be exposed in the UI.
 */
enum class TestFrameworkOption(val id: String, val displayName: String, val generatorAvailable: Boolean) {
    MOCK_MVC("MOCK_MVC", "MockMvc", true),
    WEB_TEST_CLIENT("WEB_TEST_CLIENT", "WebTestClient", false),
    REST_ASSURED("REST_ASSURED", "RestAssured", false);

    companion object {
        private val byId = entries.associateBy { it.id }
        fun fromId(id: String): TestFrameworkOption = byId[id] ?: MOCK_MVC

        /** Frameworks that have an implemented generator and should appear in UI. */
        fun available(): List<TestFrameworkOption> = entries.filter { it.generatorAvailable }
    }
}
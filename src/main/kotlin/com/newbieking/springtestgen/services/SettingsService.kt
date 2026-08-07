package com.newbieking.springtestgen.services

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Persistent project-level settings for the Spring Test Generator plugin.
 *
 * Covers both AI provider configuration and test generation policy.
 * Supports multiple AI providers with priority-based fallback ordering.
 */
@Service(Service.Level.PROJECT)
@State(name = "SpringTestGeneratorSettings", storages = [Storage("springTestGenerator.xml")])
class SettingsService : PersistentStateComponent<SettingsService.State> {

    data class State(
        // --- AI Provider (legacy single-provider, kept for backward compatibility) ---
        var baseUrl: String = "https://api.openai.com/v1",
        var apiKey: String = "",
        var model: String = "gpt-3.5-turbo",
        var enableAI: Boolean = true,

        // --- Multi-provider configuration ---
        var providers: MutableList<ProviderState> = mutableListOf(),

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

    /**
     * XML-serializable state for a single AI provider.
     * Used by IntelliJ's PersistentStateComponent for list storage.
     */
    data class ProviderState(
        var id: String = "",
        var displayName: String = "",
        var baseUrl: String = "",
        var apiKey: String = "",
        var model: String = "",
        var timeoutSeconds: Int = 30,
        var maxRetries: Int = 1,
        var priority: Int = 0,
        var enabled: Boolean = true
    ) {
        /** Convert to an AIProviderConfig. */
        fun toProviderConfig(): AIProviderConfig = AIProviderConfig(
            id = id,
            displayName = displayName,
            baseUrl = baseUrl,
            apiKey = apiKey,
            model = model,
            timeoutSeconds = timeoutSeconds,
            maxRetries = maxRetries,
            priority = priority,
            enabled = enabled
        )

        companion object {
            /** Create from an AIProviderConfig. */
            fun fromProviderConfig(config: AIProviderConfig): ProviderState = ProviderState(
                id = config.id,
                displayName = config.displayName,
                baseUrl = config.baseUrl,
                apiKey = config.apiKey,
                model = config.model,
                timeoutSeconds = config.timeoutSeconds,
                maxRetries = config.maxRetries,
                priority = config.priority,
                enabled = config.enabled
            )
        }
    }

    private var myState = State()

    override fun getState(): State = myState
    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, myState)
    }

    fun getConfig(): State = myState

    /** Update the legacy single-provider config fields. */
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

    // --- Multi-provider management ---

    /** Get all provider configs as AIProviderConfig instances. */
    fun getProviderConfigs(): List<AIProviderConfig> =
        myState.providers.map { it.toProviderConfig() }

    /** Get a specific provider config by ID. */
    fun getProviderConfig(id: String): AIProviderConfig? =
        myState.providers.find { it.id == id }?.toProviderConfig()

    /** Add or update a provider config. Changes take effect immediately. */
    fun setProviderConfig(config: AIProviderConfig) {
        val existingIndex = myState.providers.indexOfFirst { it.id == config.id }
        val providerState = ProviderState.fromProviderConfig(config)
        if (existingIndex >= 0) {
            myState.providers[existingIndex] = providerState
        } else {
            myState.providers.add(providerState)
        }
    }

    /** Remove a provider config by ID. Returns true if it existed. */
    fun removeProviderConfig(id: String): Boolean {
        return myState.providers.removeIf { it.id == id }
    }

    /** Reorder providers by setting a new priority order. */
    fun reorderProviders(orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id ->
            myState.providers.find { it.id == id }?.priority = index
        }
    }

    /**
     * Migrate the legacy single-provider fields to the multi-provider list.
     * Called once when upgrading from a version that only had single-provider config.
     * Does nothing if providers are already configured.
     */
    fun migrateLegacyProviderConfig() {
        if (myState.providers.isNotEmpty()) return
        if (myState.baseUrl.isBlank() && myState.apiKey.isBlank()) return

        val legacyConfig = AIProviderConfig.openaiDefault(
            apiKey = myState.apiKey,
            model = myState.model,
            baseUrl = myState.baseUrl
        )
        myState.providers.add(ProviderState.fromProviderConfig(legacyConfig))
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
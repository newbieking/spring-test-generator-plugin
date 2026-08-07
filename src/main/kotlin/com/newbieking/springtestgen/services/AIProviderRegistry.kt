package com.newbieking.springtestgen.services

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Registry for AI providers, supporting registration, removal, switching,
 * and priority-based fallback ordering.
 *
 * The registry is the single source of truth for which providers are
 * available and in what order they should be tried. Configuration changes
 * take effect immediately upon registration/removal.
 *
 * This class is PSI-free and safe to call from any thread.
 */
class AIProviderRegistry(private val project: Project?) {

    private val log = Logger.getInstance(AIProviderRegistry::class.java)

    /** Registered providers keyed by their ID. */
    private val providers = mutableMapOf<String, AIProvider>()

    /** Provider configurations keyed by ID. */
    private val configs = mutableMapOf<String, AIProviderConfig>()

    /** Ordered list of provider IDs by priority (lower = higher priority). */
    private var priorityOrder: List<String> = emptyList()

    /**
     * Register a provider with its configuration.
     * If a provider with the same ID already exists, it is replaced.
     * The priority order is recalculated immediately.
     */
    fun register(provider: AIProvider, config: AIProviderConfig) {
        require(provider.id == config.id) {
            "Provider ID '${provider.id}' does not match config ID '${config.id}'"
        }
        val replaced = providers.containsKey(config.id)
        providers[config.id] = provider
        configs[config.id] = config
        recalculatePriority()
        if (replaced) {
            log.info("Replaced AI provider: ${config.id} (${config.displayName})")
        } else {
            log.info("Registered AI provider: ${config.id} (${config.displayName}, priority: ${config.priority})")
        }
    }

    /**
     * Remove a provider by ID. Returns true if the provider existed.
     * The priority order is recalculated immediately.
     */
    fun unregister(id: String): Boolean {
        val removed = providers.remove(id) != null
        configs.remove(id)
        if (removed) {
            recalculatePriority()
            log.info("Unregistered AI provider: $id")
        }
        return removed
    }

    /**
     * Get a registered provider by ID, or null if not found.
     */
    fun getProvider(id: String): AIProvider? = providers[id]

    /**
     * Get a registered provider config by ID, or null if not found.
     */
    fun getConfig(id: String): AIProviderConfig? = configs[id]

    /**
     * Get the primary (highest-priority, enabled) provider.
     * Returns null if no enabled providers are registered.
     */
    fun getPrimaryProvider(): AIProvider? {
        for (id in priorityOrder) {
            val config = configs[id]
            if (config != null && config.enabled) {
                val provider = providers[id]
                if (provider != null && provider.isAvailable()) {
                    return provider
                }
            }
        }
        return null
    }

    /**
     * Get all enabled providers in priority order.
     */
    fun getEnabledProviders(): List<AIProvider> {
        return priorityOrder.mapNotNull { id ->
            val config = configs[id]
            if (config != null && config.enabled) providers[id] else null
        }
    }

    /**
     * Get fallback providers (all enabled providers after the primary).
     * Returns providers in priority order, excluding the primary.
     */
    fun getFallbackProviders(): List<AIProvider> {
        val enabled = getEnabledProviders()
        val primary = getPrimaryProvider()
        if (primary == null) return enabled
        return enabled.filter { it.id != primary.id }
    }

    /**
     * Get the ordered list of enabled provider IDs.
     */
    fun getProviderOrder(): List<String> = priorityOrder.filter { id ->
        configs[id]?.enabled == true
    }

    /**
     * Check if any provider is registered and enabled.
     */
    fun hasAvailableProvider(): Boolean = getPrimaryProvider() != null

    /**
     * List all registered provider IDs (including disabled ones).
     */
    fun listProviderIds(): Set<String> = providers.keys.toSet()

    /**
     * Check if a provider with the given ID is registered.
     */
    fun isRegistered(id: String): Boolean = providers.containsKey(id)

    /**
     * Update a provider's configuration without replacing the provider instance.
     * Recalculates priority order.
     */
    fun updateConfig(config: AIProviderConfig) {
        if (!configs.containsKey(config.id)) {
            log.warn("Cannot update config for unregistered provider: ${config.id}")
            return
        }
        configs[config.id] = config
        recalculatePriority()
        log.info("Updated config for AI provider: ${config.id}")
    }

    /**
     * Reorder providers by setting a new priority list.
     * Providers not in the new order retain their existing priority.
     */
    fun reorder(newOrder: List<String>) {
        newOrder.forEachIndexed { index, id ->
            configs[id]?.let { config ->
                configs[id] = config.copy(priority = index)
            }
        }
        recalculatePriority()
        log.info("Reordered AI providers: $newOrder")
    }

    private fun recalculatePriority() {
        priorityOrder = configs.values
            .sortedBy { it.priority }
            .map { it.id }
    }

    companion object {
        fun getInstance(project: Project): AIProviderRegistry {
            return project.getService(AIProviderRegistry::class.java)
        }
    }
}
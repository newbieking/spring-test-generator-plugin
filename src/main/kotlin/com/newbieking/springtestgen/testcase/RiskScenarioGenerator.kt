package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.psi.EndpointMetadata

/**
 * Generates risk-oriented test scenarios for Controller endpoints.
 * Each generator targets a specific risk category (security, idempotency, etc.)
 * and produces deterministic skeleton scenarios that can be AI-enhanced.
 */
interface EndpointRiskScenarioGenerator {
    /** The risk category this generator covers. */
    val label: RiskScenarioLabel

    /**
     * Generate risk test scenarios for a single Controller endpoint.
     * Returns deterministic skeleton scenarios; AI enhancement is applied separately.
     */
    fun generate(endpoint: EndpointMetadata): List<TestCaseModel>
}

/**
 * Generates risk-oriented test scenarios for non-Controller classes.
 * Each generator targets a specific risk category (concurrency, state-machine, etc.)
 * and produces deterministic skeleton scenarios with TODO placeholders.
 */
interface ClassRiskScenarioGenerator {
    /** The risk category this generator covers. */
    val label: RiskScenarioLabel

    /**
     * Generate risk test scenarios for a non-Controller class.
     * Returns deterministic skeleton scenarios with TODO placeholders for assertions.
     */
    fun generate(metadata: ClassUnderTestMetadata): List<ClassTestScenario>
}

/**
 * Registry of all risk scenario generators.
 * Selects the appropriate generators based on the enabled risk categories
 * from SettingsService.
 */
class RiskScenarioRegistry(
    private val endpointGenerators: List<EndpointRiskScenarioGenerator> = defaultEndpointGenerators(),
    private val classGenerators: List<ClassRiskScenarioGenerator> = defaultClassGenerators()
) {
    /**
     * Get endpoint risk generators for the given enabled labels.
     */
    fun getEndpointGenerators(enabledLabels: Set<RiskScenarioLabel>): List<EndpointRiskScenarioGenerator> =
        endpointGenerators.filter { it.label in enabledLabels }

    /**
     * Get class risk generators for the given enabled labels.
     */
    fun getClassGenerators(enabledLabels: Set<RiskScenarioLabel>): List<ClassRiskScenarioGenerator> =
        classGenerators.filter { it.label in enabledLabels }

    /**
     * Generate all enabled risk scenarios for a Controller endpoint.
     */
    fun generateForEndpoint(
        endpoint: EndpointMetadata,
        enabledLabels: Set<RiskScenarioLabel>
    ): List<TestCaseModel> =
        getEndpointGenerators(enabledLabels).flatMap { it.generate(endpoint) }

    /**
     * Generate all enabled risk scenarios for a non-Controller class.
     */
    fun generateForClass(
        metadata: ClassUnderTestMetadata,
        enabledLabels: Set<RiskScenarioLabel>
    ): List<ClassTestScenario> =
        getClassGenerators(enabledLabels).flatMap { it.generate(metadata) }

    companion object {
        fun defaultEndpointGenerators(): List<EndpointRiskScenarioGenerator> = listOf(
            SecurityScenarioGenerator(),
            IdempotencyScenarioGenerator()
        )

        fun defaultClassGenerators(): List<ClassRiskScenarioGenerator> = listOf(
            ConcurrencyScenarioGenerator(),
            StateMachineScenarioGenerator()
        )
    }
}
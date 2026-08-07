package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.MethodMetadata
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.model.Visibility

/**
 * Generates concurrency risk scenarios for non-Controller classes.
 *
 * Produces two categories of deterministic skeletons:
 * - **CONCURRENCY_OPTIMISTIC_LOCK**: Tests that concurrent updates with @Version
 *   cause an OptimisticLockingFailureException, expecting the second writer to fail.
 * - **CONCURRENCY_READ_WRITE_CONFLICT**: Tests that a read during a concurrent write
 *   does not return stale or inconsistent data.
 *
 * These scenarios are most relevant for Service and Repository classes that manage
 * mutable shared state. The generated skeletons use `@Disabled` annotations with
 * guidance for the developer to provide concrete test setup.
 */
class ConcurrencyScenarioGenerator : ClassRiskScenarioGenerator {

    override val label: RiskScenarioLabel = RiskScenarioLabel.CONCURRENCY

    override fun generate(metadata: ClassUnderTestMetadata): List<ClassTestScenario> {
        if (metadata.targetType !in CONCURRENT_TARGET_TYPES) return emptyList()

        return metadata.methods
            .filter { it.visibility == Visibility.PUBLIC }
            .flatMapIndexed { index, method -> generateForMethod(method, index, metadata) }
    }

    private fun generateForMethod(
        method: MethodMetadata,
        methodOrdinal: Int,
        metadata: ClassUnderTestMetadata
    ): List<ClassTestScenario> {
        val normalArgs = method.parameters.map { ArgumentValue("null", ArgumentSemantic.NORMAL) }
        val methodName = method.name

        val scenarios = mutableListOf<ClassTestScenario>()

        // Optimistic lock scenario — relevant for update methods
        if (isUpdateMethod(method, metadata)) {
            scenarios += ClassTestScenario(
                id = "$methodName-$methodOrdinal-concurrency-optimistic-lock",
                displayName = "$methodName concurrent update should fail with optimistic lock error",
                method = method,
                methodOrdinal = methodOrdinal,
                scenarioType = ClassTestScenarioType.CONCURRENCY_OPTIMISTIC_LOCK,
                arguments = normalArgs,
                disabledReason = "Set up a @Version-annotated entity and simulate concurrent modification " +
                    "before enabling this scenario. Assert OptimisticLockingFailureException.",
                riskLabel = RiskScenarioLabel.CONCURRENCY
            )
        }

        // Read-write conflict scenario — relevant for read methods on mutable state
        if (isReadMethod(method, metadata)) {
            scenarios += ClassTestScenario(
                id = "$methodName-$methodOrdinal-concurrency-read-write-conflict",
                displayName = "$methodName should not return stale data during concurrent write",
                method = method,
                methodOrdinal = methodOrdinal,
                scenarioType = ClassTestScenarioType.CONCURRENCY_READ_WRITE_CONFLICT,
                arguments = normalArgs,
                disabledReason = "Simulate a concurrent write while reading and assert the read returns " +
                    "consistent data before enabling this scenario.",
                riskLabel = RiskScenarioLabel.CONCURRENCY
            )
        }

        return scenarios
    }

    private fun isUpdateMethod(method: MethodMetadata, metadata: ClassUnderTestMetadata): Boolean {
        val name = method.name.lowercase()
        return name.startsWith("save") || name.startsWith("update") ||
            name.startsWith("create") || name.startsWith("insert") ||
            name.startsWith("delete") || name.startsWith("remove") ||
            name.startsWith("put") || name.startsWith("post") ||
            metadata.targetType == TargetType.REPOSITORY ||
            metadata.targetType == TargetType.MAPPER
    }

    private fun isReadMethod(method: MethodMetadata, @Suppress("UNUSED_PARAMETER") metadata: ClassUnderTestMetadata): Boolean {
        val name = method.name.lowercase()
        return name.startsWith("find") || name.startsWith("get") ||
            name.startsWith("read") || name.startsWith("query") ||
            name.startsWith("list") || name.startsWith("search") ||
            name.startsWith("count") || name.startsWith("exists")
    }

    companion object {
        private val CONCURRENT_TARGET_TYPES = setOf(
            TargetType.SERVICE,
            TargetType.COMPONENT,
            TargetType.REPOSITORY,
            TargetType.MAPPER
        )
    }
}
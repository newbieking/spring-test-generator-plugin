package com.newbieking.springtestgen.strategy

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.TargetType

/** Describes a generation strategy without coupling the model to PSI or a source template. */
interface TestGenerationStrategy {
    val id: String
    val targetTypes: Set<TargetType>
    val framework: TestFramework
    val requiresInfrastructure: Boolean

    fun supports(metadata: ClassUnderTestMetadata): Boolean = metadata.targetType in targetTypes
}

enum class TestFramework {
    JUNIT5,
    JUNIT5_MOCKITO,
    SPRING_SLICE
}

sealed interface TestStrategySelection {
    data class Supported(val strategy: TestGenerationStrategy) : TestStrategySelection

    data class Unsupported(
        val targetType: TargetType,
        val reason: String
    ) : TestStrategySelection
}

/** Chooses a registered strategy and reports unsupported targets explicitly. */
class TestStrategyRegistry(
    private val strategies: List<TestGenerationStrategy> = defaultStrategies()
) {
    fun select(metadata: ClassUnderTestMetadata): TestStrategySelection =
        strategies.firstOrNull { it.supports(metadata) }?.let(TestStrategySelection::Supported)
            ?: TestStrategySelection.Unsupported(
                targetType = metadata.targetType,
                reason = metadata.unsupportedReason
                    ?: "No test generation strategy is registered for ${metadata.targetType}."
            )

    companion object {
        fun defaultStrategies(): List<TestGenerationStrategy> = listOf(
            StaticUtilityStrategy,
            MockitoUnitStrategy
        )
    }
}

private object StaticUtilityStrategy : TestGenerationStrategy {
    override val id: String = "junit5-utility"
    override val targetTypes: Set<TargetType> = setOf(TargetType.UTILITY)
    override val framework: TestFramework = TestFramework.JUNIT5
    override val requiresInfrastructure: Boolean = false
}

private object MockitoUnitStrategy : TestGenerationStrategy {
    override val id: String = "junit5-mockito-unit"
    override val targetTypes: Set<TargetType> = setOf(
        TargetType.SERVICE,
        TargetType.COMPONENT,
        TargetType.REPOSITORY,
        TargetType.MAPPER
    )
    override val framework: TestFramework = TestFramework.JUNIT5_MOCKITO
    override val requiresInfrastructure: Boolean = false
}

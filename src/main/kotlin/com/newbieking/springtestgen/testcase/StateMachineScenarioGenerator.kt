package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.MethodMetadata
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.model.Visibility

/**
 * Generates state-machine risk scenarios for non-Controller classes.
 *
 * Produces two categories of deterministic skeletons:
 * - **STATE_MACHINE_VALID_TRANSITION**: Tests that a valid state transition
 *   (e.g., PENDING → APPROVED) succeeds without error.
 * - **STATE_MACHINE_INVALID_TRANSITION**: Tests that an invalid state transition
 *   (e.g., REJECTED → APPROVED) is rejected, typically throwing IllegalStateException.
 *
 * These scenarios are most relevant for Service and Component classes that manage
 * entity lifecycle / workflow states. The generated skeletons use `@Disabled`
 * annotations since the developer must define the actual state model and transitions.
 */
class StateMachineScenarioGenerator : ClassRiskScenarioGenerator {

    override val label: RiskScenarioLabel = RiskScenarioLabel.STATE_MACHINE

    override fun generate(metadata: ClassUnderTestMetadata): List<ClassTestScenario> {
        if (metadata.targetType !in STATE_MACHINE_TARGET_TYPES) return emptyList()

        return metadata.methods
            .filter { it.visibility == Visibility.PUBLIC }
            .flatMapIndexed { index, method -> generateForMethod(method, index) }
    }

    private fun generateForMethod(method: MethodMetadata, methodOrdinal: Int): List<ClassTestScenario> {
        val normalArgs = method.parameters.map { ArgumentValue("null", ArgumentSemantic.NORMAL) }
        val methodName = method.name

        // Only generate state-machine scenarios for methods that look like state transitions
        if (!looksLikeStateTransition(methodName)) return emptyList()

        return listOf(
            ClassTestScenario(
                id = "$methodName-$methodOrdinal-state-machine-valid-transition",
                displayName = "$methodName valid state transition should succeed",
                method = method,
                methodOrdinal = methodOrdinal,
                scenarioType = ClassTestScenarioType.STATE_MACHINE_VALID_TRANSITION,
                arguments = normalArgs,
                disabledReason = "Define the valid initial state and expected final state " +
                    "before enabling this scenario. Assert the transition succeeds.",
                riskLabel = RiskScenarioLabel.STATE_MACHINE
            ),
            ClassTestScenario(
                id = "$methodName-$methodOrdinal-state-machine-invalid-transition",
                displayName = "$methodName invalid state transition should be rejected",
                method = method,
                methodOrdinal = methodOrdinal,
                scenarioType = ClassTestScenarioType.STATE_MACHINE_INVALID_TRANSITION,
                arguments = normalArgs,
                disabledReason = "Define the invalid initial state (e.g., already terminal) " +
                    "before enabling this scenario. Assert IllegalStateException or equivalent.",
                riskLabel = RiskScenarioLabel.STATE_MACHINE
            )
        )
    }

    /** Heuristic: method names that commonly represent state transitions. */
    private fun looksLikeStateTransition(methodName: String): Boolean {
        val lower = methodName.lowercase()
        return lower.startsWith("approve") || lower.startsWith("reject") ||
            lower.startsWith("cancel") || lower.startsWith("submit") ||
            lower.startsWith("confirm") || lower.startsWith("complete") ||
            lower.startsWith("activate") || lower.startsWith("deactivate") ||
            lower.startsWith("suspend") || lower.startsWith("resume") ||
            lower.startsWith("close") || lower.startsWith("reopen") ||
            lower.startsWith("transition") || lower.startsWith("advance") ||
            lower.startsWith("publish") || lower.startsWith("archive") ||
            lower.startsWith("expire") || lower.startsWith("rollback")
    }

    companion object {
        private val STATE_MACHINE_TARGET_TYPES = setOf(
            TargetType.SERVICE,
            TargetType.COMPONENT
        )
    }
}
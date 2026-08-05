package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.DependencyKind
import com.newbieking.springtestgen.model.DependencyMetadata
import com.newbieking.springtestgen.model.MethodMetadata
import com.newbieking.springtestgen.model.ParameterMetadata
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.model.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicClassTestCaseGeneratorTest {

    private val generator = DeterministicClassTestCaseGenerator()

    @Test
    fun `creates editable service scenarios and skips private methods`() {
        val metadata = ClassUnderTestMetadata(
            simpleName = "PaymentService",
            qualifiedName = "sample.PaymentService",
            packageName = "sample",
            targetType = TargetType.SERVICE,
            dependencies = listOf(
                DependencyMetadata(
                    name = "repository",
                    type = "sample.PaymentRepository",
                    qualifiedType = "sample.PaymentRepository",
                    kind = DependencyKind.CONSTRUCTOR_PARAMETER,
                    required = true
                )
            ),
            methods = listOf(
                MethodMetadata(
                    name = "charge",
                    returnType = "sample.Receipt",
                    parameters = listOf(
                        ParameterMetadata("orderId", "java.lang.String", "java.lang.String"),
                        ParameterMetadata("amount", "int", null)
                    ),
                    declaredExceptions = emptyList(),
                    visibility = Visibility.PUBLIC,
                    isStatic = false
                ),
                MethodMetadata(
                    name = "internalOnly",
                    returnType = "void",
                    parameters = emptyList(),
                    declaredExceptions = emptyList(),
                    visibility = Visibility.PRIVATE,
                    isStatic = false
                )
            )
        )

        val scenarios = generator.generate(metadata)

        assertEquals(
            setOf(
                ClassTestScenarioType.HAPPY_PATH,
                ClassTestScenarioType.NULL_INPUT,
                ClassTestScenarioType.BOUNDARY_INPUT,
                ClassTestScenarioType.DEPENDENCY_EXCEPTION
            ),
            scenarios.map { it.scenarioType }.toSet()
        )
        assertTrue(scenarios.all { it.method.name == "charge" })
        assertTrue(scenarios.filter { it.scenarioType != ClassTestScenarioType.HAPPY_PATH }.all { it.disabledReason != null })
        assertEquals("null", scenarios.first { it.scenarioType == ClassTestScenarioType.NULL_INPUT }.arguments.first().source)
        assertEquals("", scenarios.first { it.scenarioType == ClassTestScenarioType.BOUNDARY_INPUT }.arguments.first().source.removeSurrounding("\""))
    }
}

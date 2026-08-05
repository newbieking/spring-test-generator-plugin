package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.MethodMetadata
import com.newbieking.springtestgen.model.Visibility

/** Creates editable baseline scenarios without making assumptions about business assertions. */
class DeterministicClassTestCaseGenerator {

    fun generate(metadata: ClassUnderTestMetadata): List<ClassTestScenario> =
        metadata.methods
            .filter { it.visibility == Visibility.PUBLIC }
            .flatMapIndexed { index, method ->
                generateForMethod(method, index, metadata.dependencies.isNotEmpty())
            }

    private fun generateForMethod(
        method: MethodMetadata,
        methodOrdinal: Int,
        hasDependencies: Boolean
    ): List<ClassTestScenario> {
        val normalArguments = method.parameters.map { normalArgument(it.type) }
        val scenarios = mutableListOf(
            ClassTestScenario(
                id = "${method.name}-$methodOrdinal-happy-path",
                displayName = "${method.name} happy path",
                method = method,
                methodOrdinal = methodOrdinal,
                scenarioType = ClassTestScenarioType.HAPPY_PATH,
                arguments = normalArguments
            )
        )

        val nullableIndex = method.parameters.indexOfFirst { isReferenceType(it.type) }
        if (nullableIndex >= 0) {
            scenarios += ClassTestScenario(
                id = "${method.name}-$methodOrdinal-null-input",
                displayName = "${method.name} null input",
                method = method,
                methodOrdinal = methodOrdinal,
                scenarioType = ClassTestScenarioType.NULL_INPUT,
                arguments = normalArguments.withReplaced(nullableIndex, ArgumentValue("null", ArgumentSemantic.NULL)),
                disabledReason = "Define the expected null-input behavior before enabling this generated scenario."
            )
        }

        val boundaryIndex = method.parameters.indexOfFirst { supportsBoundary(it.type) }
        if (boundaryIndex >= 0) {
            scenarios += ClassTestScenario(
                id = "${method.name}-$methodOrdinal-boundary-input",
                displayName = "${method.name} boundary input",
                method = method,
                methodOrdinal = methodOrdinal,
                scenarioType = ClassTestScenarioType.BOUNDARY_INPUT,
                arguments = normalArguments.withReplaced(boundaryIndex, boundaryArgument(method.parameters[boundaryIndex].type)),
                disabledReason = "Define the expected boundary behavior before enabling this generated scenario."
            )
        }

        if (hasDependencies) {
            scenarios += ClassTestScenario(
                id = "${method.name}-$methodOrdinal-dependency-exception",
                displayName = "${method.name} dependency exception",
                method = method,
                methodOrdinal = methodOrdinal,
                scenarioType = ClassTestScenarioType.DEPENDENCY_EXCEPTION,
                arguments = normalArguments,
                disabledReason = "Stub a dependency failure and assert the service's expected exception before enabling this generated scenario."
            )
        }
        return scenarios
    }

    private fun normalArgument(type: String): ArgumentValue = ArgumentValue(argumentFor(type, boundary = false), ArgumentSemantic.NORMAL)

    private fun boundaryArgument(type: String): ArgumentValue = ArgumentValue(argumentFor(type, boundary = true), ArgumentSemantic.BOUNDARY)

    private fun argumentFor(type: String, boundary: Boolean): String {
        val normalized = type.trim()
        return when {
            normalized == "boolean" || normalized == "java.lang.Boolean" -> if (boundary) "false" else "true"
            normalized == "byte" || normalized == "java.lang.Byte" -> if (boundary) "(byte) 0" else "(byte) 1"
            normalized == "short" || normalized == "java.lang.Short" -> if (boundary) "(short) 0" else "(short) 1"
            normalized == "int" || normalized == "java.lang.Integer" -> if (boundary) "0" else "1"
            normalized == "long" || normalized == "java.lang.Long" -> if (boundary) "0L" else "1L"
            normalized == "float" || normalized == "java.lang.Float" -> if (boundary) "0.0f" else "1.0f"
            normalized == "double" || normalized == "java.lang.Double" -> if (boundary) "0.0d" else "1.0d"
            normalized == "char" || normalized == "java.lang.Character" -> if (boundary) "'\\u0000'" else "'a'"
            normalized == "java.lang.String" || normalized == "String" -> if (boundary) "\"\"" else "\"testValue\""
            normalized.endsWith("[]") -> "new ${sourceTypeName(normalized.removeSuffix("[]"))}[0]"
            normalized.startsWith("java.util.List") || normalized.startsWith("java.util.Collection") -> "java.util.List.of()"
            normalized.startsWith("java.util.Set") -> "java.util.Set.of()"
            normalized.startsWith("java.util.Map") -> "java.util.Map.of()"
            normalized == "java.math.BigDecimal" -> if (boundary) "java.math.BigDecimal.ZERO" else "java.math.BigDecimal.ONE"
            else -> "null"
        }
    }

    private fun sourceTypeName(type: String): String = type.substringAfterLast('.')

    private fun isReferenceType(type: String): Boolean = type !in PRIMITIVE_TYPES

    private fun supportsBoundary(type: String): Boolean =
        isReferenceType(type) || type in NUMERIC_TYPES || type in BOOLEAN_TYPES || type in CHAR_TYPES

    private fun <T> List<T>.withReplaced(index: Int, value: T): List<T> = toMutableList().apply { this[index] = value }

    private companion object {
        val PRIMITIVE_TYPES = setOf("boolean", "byte", "short", "int", "long", "float", "double", "char")
        val NUMERIC_TYPES = setOf(
            "byte", "short", "int", "long", "float", "double",
            "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long",
            "java.lang.Float", "java.lang.Double", "java.math.BigDecimal"
        )
        val BOOLEAN_TYPES = setOf("boolean", "java.lang.Boolean")
        val CHAR_TYPES = setOf("char", "java.lang.Character")
    }
}

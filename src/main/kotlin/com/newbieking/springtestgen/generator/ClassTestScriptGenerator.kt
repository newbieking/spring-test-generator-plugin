package com.newbieking.springtestgen.generator

import com.newbieking.springtestgen.model.ClassUnderTestMetadata
import com.newbieking.springtestgen.model.TargetType
import com.newbieking.springtestgen.model.Visibility
import com.newbieking.springtestgen.strategy.TestFramework
import com.newbieking.springtestgen.strategy.TestStrategyRegistry
import com.newbieking.springtestgen.strategy.TestStrategySelection
import com.newbieking.springtestgen.testcase.ClassTestScenario
import com.newbieking.springtestgen.testcase.DeterministicClassTestCaseGenerator

/** Generates editable JUnit 5 tests for utility, Service/Component, Repository, and Mapper targets. */
class ClassTestScriptGenerator(
    private val strategyRegistry: TestStrategyRegistry = TestStrategyRegistry(),
    private val scenarioGenerator: DeterministicClassTestCaseGenerator = DeterministicClassTestCaseGenerator()
) {

    fun generateTestClass(metadata: ClassUnderTestMetadata, testClassName: String): String {
        require(testClassName.isNotBlank()) { "A test class name is required." }
        val selection = strategyRegistry.select(metadata)
        val strategy = (selection as? TestStrategySelection.Supported)?.strategy
            ?: throw IllegalArgumentException((selection as TestStrategySelection.Unsupported).reason)
        require(metadata.targetType in SUPPORTED_TARGET_TYPES) {
            "${metadata.targetType} generation is reserved for a later strategy implementation."
        }

        val scenarios = scenarioGenerator.generate(metadata)
        val usesMockito = strategy.framework == TestFramework.JUNIT5_MOCKITO
        val utilityHasInstanceMethods = metadata.targetType == TargetType.UTILITY &&
            scenarios.any { !it.method.isStatic }
        val utilityCanInstantiate = metadata.constructors.isEmpty() || metadata.constructors.any {
            it.parameters.isEmpty() && it.visibility != Visibility.PRIVATE
        }
        val imports = buildImports(metadata, scenarios, usesMockito)
        val members = buildMembers(metadata, usesMockito, utilityHasInstanceMethods, utilityCanInstantiate)
        val methods = scenarios.joinToString("\n\n") {
            renderScenario(it, metadata, utilityCanInstantiate)
        }
        val body = listOf(members, methods).filter(String::isNotBlank).joinToString("\n\n")
            .ifBlank { "// No public methods were found for deterministic test generation." }
        val indentedBody = body.lines().joinToString("\n") { line ->
            if (line.isBlank()) "" else "    $line"
        }
        val packageName = if (metadata.packageName.isBlank()) "test" else "${metadata.packageName}.test"

        return """
            package $packageName;

            $imports

            public class $testClassName {

            $indentedBody
            }
        """.trimIndent()
    }

    private fun buildImports(
        metadata: ClassUnderTestMetadata,
        scenarios: List<ClassTestScenario>,
        usesMockito: Boolean
    ): String {
        val regular = buildSet {
            metadata.qualifiedName?.let(::add)
            metadata.dependencies.mapNotNull { it.qualifiedType }
                .filterNot { it.startsWith("java.lang.") }
                .forEach(::add)
            add("org.junit.jupiter.api.Test")
            if (scenarios.any { it.disabledReason != null }) add("org.junit.jupiter.api.Disabled")
            if (usesMockito) {
                add("org.junit.jupiter.api.extension.ExtendWith")
                add("org.mockito.Mock")
                add("org.mockito.junit.jupiter.MockitoExtension")
                if (!metadata.isInterface) add("org.mockito.InjectMocks")
            }
        }
        val staticImports = setOf("org.junit.jupiter.api.Assertions.assertDoesNotThrow")

        return (regular.sorted().map { "import $it;" } + staticImports.sorted().map { "import static $it;" })
            .joinToString("\n")
    }

    private fun buildMembers(
        metadata: ClassUnderTestMetadata,
        usesMockito: Boolean,
        utilityHasInstanceMethods: Boolean,
        utilityCanInstantiate: Boolean
    ): String = buildString {
        if (usesMockito) {
            appendLine("@ExtendWith(MockitoExtension.class)")
            appendLine()
            metadata.dependencies
                .distinctBy { it.name to it.type }
                .forEach { dependency ->
                    appendLine("@Mock")
                    appendLine("private ${sourceTypeName(dependency.type)} ${dependency.name};")
                }
            appendLine(if (metadata.isInterface) "@Mock" else "@InjectMocks")
            append("private ${sourceTypeName(metadata.simpleName)} ${targetFieldName(metadata)};")
        } else if (utilityHasInstanceMethods) {
            if (utilityCanInstantiate) {
                append("private final ${sourceTypeName(metadata.simpleName)} ${targetFieldName(metadata)} = new ${sourceTypeName(metadata.simpleName)}();")
            } else {
                append("private ${sourceTypeName(metadata.simpleName)} ${targetFieldName(metadata)};")
            }
        }
    }.trimEnd()

    private fun renderScenario(
        scenario: ClassTestScenario,
        metadata: ClassUnderTestMetadata,
        utilityCanInstantiate: Boolean
    ): String {
        val disabledReason = scenario.disabledReason
            ?: if (metadata.targetType == TargetType.UTILITY && !scenario.method.isStatic && !utilityCanInstantiate) {
                "Provide a usable utility constructor before enabling this generated scenario."
            } else {
                null
            }
        val methodSuffix = scenario.scenarioType.name.lowercase().split('_').joinToString("") {
            it.replaceFirstChar(Char::titlecase)
        }
        val ordinalSuffix = if (scenario.methodOrdinal == 0) "" else (scenario.methodOrdinal + 1).toString()
        val testMethodName = "test${scenario.method.name.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }}$ordinalSuffix$methodSuffix"
        val target = if (scenario.method.isStatic) sourceTypeName(metadata.simpleName) else targetFieldName(metadata)
        val arguments = scenario.arguments.joinToString(", ") { it.source }
        val invocation = "$target.${scenario.method.name}($arguments)"

        return buildString {
            if (disabledReason != null) {
                appendLine("@Disabled(\"${escapeJavaString(disabledReason)}\")")
            }
            appendLine("@Test")
            appendLine("void $testMethodName() throws Exception {")
            appendLine("    // ${scenario.displayName}")
            if (disabledReason != null) {
                appendLine("    // TODO: $disabledReason")
            }
            if (metadata.isInterface && disabledReason == null) {
                appendLine("    $invocation;")
                append("    org.mockito.Mockito.verify($target).${scenario.method.name}($arguments);")
            } else {
                append("    assertDoesNotThrow(() -> $invocation);")
            }
            appendLine()
            append("}")
        }
    }

    private fun targetFieldName(metadata: ClassUnderTestMetadata): String =
        metadata.simpleName.replaceFirstChar { it.lowercase() }

    private fun sourceTypeName(type: String): String =
        QUALIFIED_TYPE_TOKEN.replace(type) { match -> match.value.substringAfterLast('.') }

    private fun escapeJavaString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")

    private companion object {
        val SUPPORTED_TARGET_TYPES = setOf(
            TargetType.UTILITY,
            TargetType.SERVICE,
            TargetType.COMPONENT,
            TargetType.REPOSITORY,
            TargetType.MAPPER
        )
        val QUALIFIED_TYPE_TOKEN = Regex("[A-Za-z_][A-Za-z0-9_$.]*")
    }
}

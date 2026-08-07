package com.newbieking.springtestgen.generator

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.services.*
import com.newbieking.springtestgen.testcase.DeterministicTestCaseGenerator
import com.newbieking.springtestgen.testcase.ExpectedHttpStatus
import com.newbieking.springtestgen.testcase.TestCaseModel
import com.newbieking.springtestgen.testcase.TestScenarioType
import com.newbieking.springtestgen.utils.DiagnosticLogger

/** Generates JUnit 5 and MockMvc tests from deterministic test-case models. */
class TestScriptGenerator(private val project: Project) {

    private val log = Logger.getInstance(TestScriptGenerator::class.java)

    private val aiService: AIGenerationService? by lazy {
        val settings = SettingsService.getInstance(project)
        if (!settings.getConfig().enableAI) return@lazy null

        // Migrate legacy config if needed
        settings.migrateLegacyProviderConfig()

        val registry = AIProviderRegistry.getInstance(project)
        val degradationManager = AIDegradationManager.getInstance(project)

        // Sync providers from settings to registry
        syncProvidersFromSettings(registry, settings)

        if (!registry.hasAvailableProvider()) {
            log.info("No AI providers available; AI generation disabled")
            return@lazy null
        }

        DefaultAIGenerationService(registry, degradationManager, project = project)
    }

    private val testCaseGenerator = DeterministicTestCaseGenerator()

    /**
     * Synchronize provider configurations from settings to the registry.
     * Creates or updates provider instances based on current settings.
     */
    private fun syncProvidersFromSettings(registry: AIProviderRegistry, settings: SettingsService) {
        val configs = settings.getProviderConfigs()
        if (configs.isEmpty()) {
            log.info("No provider configs in settings; registry will be empty")
            return
        }

        for (config in configs) {
            val existingProvider = registry.getProvider(config.id)
            if (existingProvider != null) {
                // Update config if changed
                registry.updateConfig(config)
            } else {
                // Register new provider (currently only OpenAI-compatible)
                val provider = OpenAIApiClient(project, config)
                registry.register(provider, config)
            }
        }

        // Remove providers that are no longer in settings
        val configIds = configs.map { it.id }.toSet()
        for (id in registry.listProviderIds()) {
            if (id !in configIds) {
                registry.unregister(id)
            }
        }
    }

    suspend fun generateTestClass(
        endpoints: List<EndpointMetadata>,
        testClassName: String,
        useAI: Boolean
    ): String {
        require(endpoints.isNotEmpty()) { "At least one endpoint is required to generate a test class." }
        val controllerName = endpoints.first().controllerName
        val controllerQualifiedName = endpoints.first().controllerQualifiedName
        val controllerPackageName = controllerQualifiedName?.substringBeforeLast('.') ?: ""
        val testPackageName = if (controllerPackageName.isBlank()) "test" else "$controllerPackageName.test"

        val testCases = testCaseGenerator.generate(endpoints)
        log.info("Generating test class '$testClassName' for ${testCases.size} deterministic scenario(s); AI enabled: $useAI")
        val methods = testCases.map { testCase -> generateTestMethod(testCase, useAI) }
            .joinToString("\n\n")

        val generatedClass = """
package $testPackageName;

${buildImports(controllerQualifiedName)}

@WebMvcTest($controllerName.class)
public class $testClassName {

    @Autowired
    private MockMvc mockMvc;

    $methods
}
        """.trimIndent()
        log.info("Generated test class '$testClassName' (${generatedClass.length} characters)")
        return generatedClass
    }

    private fun buildImports(controllerQualifiedName: String?): String = buildString {
        if (controllerQualifiedName != null) appendLine("import $controllerQualifiedName;")
        appendLine()
        appendLine("import org.junit.jupiter.api.Test;")
        appendLine("import org.springframework.beans.factory.annotation.Autowired;")
        appendLine("import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;")
        appendLine("import org.springframework.http.MediaType;")
        appendLine("import org.springframework.test.web.servlet.MockMvc;")
        appendLine()
        appendLine("import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;")
        appendLine("import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;")
        appendLine("import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;")
    }.trimEnd()

    private suspend fun generateTestMethod(testCase: TestCaseModel, useAI: Boolean): String {
        val endpoint = testCase.endpoint
        val methodName = endpoint.methodName
        val capitalizedName = methodName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val scenarioSuffix = testCase.id.substringAfter(':').split('-').joinToString("") {
            it.replaceFirstChar { character -> character.titlecase() }
        }
        val httpMethod = endpoint.httpMethod.name
        val path = endpoint.path
        val resolvedPath = endpoint.pathVariables.fold(path) { currentPath, pathVariable ->
            currentPath.replace("{${pathVariable.name}}", "testValue")
        }
        val requestBodyType = endpoint.requestBodyType
        val scenarioType = testCase.scenarioType
        DiagnosticLogger.log("[TestScriptGen] $httpMethod $path | scenario=$scenarioType | requestBodyType=$requestBodyType | requestParams=${endpoint.requestParams.map { it.name }} | useAI=$useAI | hasBodyJson=${testCase.requestBodyJson != null}")
        log.debug("Generating $scenarioType for $httpMethod $path (${endpoint.controllerName}#$methodName)")

        val performBlock = buildString {
            append("mockMvc.perform(")
            when (httpMethod) {
                "GET" -> append("get(\"$resolvedPath\")")
                "POST" -> append("post(\"$resolvedPath\")")
                "PUT" -> append("put(\"$resolvedPath\")")
                "DELETE" -> append("delete(\"$resolvedPath\")")
                "PATCH" -> append("patch(\"$resolvedPath\")")
                else -> append("request(HttpMethod.$httpMethod, \"$resolvedPath\")")
            }
            endpoint.requestParams
                .filterNot { it.name in testCase.omittedRequestParameters }
                .forEach { append(".param(\"${it.name}\", \"testValue\")") }
            if (requestBodyType != null && testCase.scenarioType != TestScenarioType.HAPPY_PATH && testCase.requestBodyJson != null) {
                append(".contentType(MediaType.APPLICATION_JSON).content(\"${escapeJavaString(testCase.requestBodyJson)}\")")
            } else if (requestBodyType != null && useAI) {
                val mockJson = aiService?.generateMockRequestBody(endpoint)
                if (mockJson == null) log.warn("AI request-body generation unavailable for $httpMethod $path; using fallback JSON")
                append(".contentType(MediaType.APPLICATION_JSON).content(\"${escapeJavaString(mockJson ?: "{\"field\":\"value\"}")}\")")
            } else if (requestBodyType != null && testCase.requestBodyJson != null) {
                append(".contentType(MediaType.APPLICATION_JSON).content(\"${escapeJavaString(testCase.requestBodyJson)}\")")
            } else if (requestBodyType != null) {
                append(".contentType(MediaType.APPLICATION_JSON).content(\"{}\")")
            }
            append(")")
        }

        val resultActions = when (testCase.expectedStatus) {
            ExpectedHttpStatus.OK -> ".andExpect(status().isOk())"
            ExpectedHttpStatus.BAD_REQUEST -> ".andExpect(status().isBadRequest())"
        }

        return """
            @Test
            void test${capitalizedName}${scenarioSuffix}() throws Exception {
                // ${testCase.displayName}: $httpMethod $path
                $performBlock
                    .andDo(print())
                    $resultActions;
            }
        """.trimIndent().prependIndent("    ")
    }

    private fun escapeJavaString(value: String): String = value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\r", "\\r")
        .replace("\n", "\\n")
}
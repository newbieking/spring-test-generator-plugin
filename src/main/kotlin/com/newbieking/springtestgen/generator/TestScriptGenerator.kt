package com.newbieking.springtestgen.generator

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.services.AIGenerationService
import com.newbieking.springtestgen.services.OpenAIApiClient
import com.newbieking.springtestgen.services.SettingsService
import com.newbieking.springtestgen.testcase.DeterministicTestCaseGenerator
import com.newbieking.springtestgen.testcase.ExpectedHttpStatus
import com.newbieking.springtestgen.testcase.TestCaseModel
import com.newbieking.springtestgen.testcase.TestScenarioType

/** Generates JUnit 5 and MockMvc tests from deterministic test-case models. */
class TestScriptGenerator(private val project: Project) {

    private val log = Logger.getInstance(TestScriptGenerator::class.java)

    private val aiService: AIGenerationService? by lazy {
        val settings = SettingsService.getInstance(project)
        if (settings.getConfig().enableAI) OpenAIApiClient(project) else null
    }

    private val testCaseGenerator = DeterministicTestCaseGenerator()

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
        appendLine("import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;")
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
        log.debug("Generating ${testCase.scenarioType} for $httpMethod $path (${endpoint.controllerName}#$methodName)")

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

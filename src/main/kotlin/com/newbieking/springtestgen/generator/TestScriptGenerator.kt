package com.newbieking.springtestgen.generator

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.services.AIGenerationService
import com.newbieking.springtestgen.services.OpenAIApiClient
import com.newbieking.springtestgen.services.SettingsService

/**
 * 测试脚本生成器，生成 JUnit 5 + MockMvc 代码
 */
class TestScriptGenerator(private val project: Project) {

    private val log = Logger.getInstance(TestScriptGenerator::class.java)

    private val aiService: AIGenerationService? by lazy {
        val settings = SettingsService.getInstance(project)
        if (settings.getConfig().enableAI) OpenAIApiClient(settings) else null
    }

    /**
     * 生成完整的测试类源代码
     * @param endpoints 端点列表（可能多个）
     * @param testClassName 测试类名
     * @param useAI 是否启用 AI
     * @return 生成的测试类代码字符串
     */
    suspend fun generateTestClass(
        endpoints: List<EndpointMetadata>,
        testClassName: String,
        useAI: Boolean
    ): String {
        require(endpoints.isNotEmpty()) { "At least one endpoint is required to generate a test class." }
        val controllerName = endpoints.first().controllerClass.name
        val packageName = endpoints.first().controllerClass.qualifiedName
            ?.substringBeforeLast('.') ?: ""

        val imports = buildString {
            appendLine("import org.junit.jupiter.api.Test;")
            appendLine("import org.springframework.beans.factory.annotation.Autowired;")
            appendLine("import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;")
            appendLine("import org.springframework.test.web.servlet.MockMvc;")
            appendLine("import org.springframework.http.MediaType;")
            appendLine("import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;")
            appendLine("import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;")
            if (endpoints.any { it.requestBodyType != null }) {
                appendLine("import com.fasterxml.jackson.databind.ObjectMapper;")
            }
        }

        log.info("Generating test class '$testClassName' for ${endpoints.size} endpoint(s); AI enabled: $useAI")
        val methods = endpoints.map { endpoint ->
            generateTestMethod(endpoint, useAI)
        }.joinToString("\n\n")

        val generatedClass = """
package ${packageName}test;

$imports

@WebMvcTest($controllerName.class)
public class $testClassName {

    @Autowired
    private MockMvc mockMvc;

    ${if (endpoints.any { it.requestBodyType != null }) "@Autowired private ObjectMapper objectMapper;" else ""}

    $methods
}
        """.trimIndent()
        log.info("Generated test class '$testClassName' (${generatedClass.length} characters)")
        return generatedClass
    }

    private suspend fun generateTestMethod(endpoint: EndpointMetadata, useAI: Boolean): String {
        val methodName = endpoint.method.name
        val capitalizedName = methodName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        val httpMethod = endpoint.httpMethod.name
        val path = endpoint.path
        val requestBodyType = endpoint.requestBodyType
        log.debug("Generating test method for $httpMethod $path (${endpoint.controllerClass.name}#$methodName), request body: ${requestBodyType != null}")

        // 构建 MockMvc 请求
        val performBlock = buildString {
            append("mockMvc.perform(")
            when (httpMethod) {
                "GET" -> append("get(\"$path\")")
                "POST" -> append("post(\"$path\")")
                "PUT" -> append("put(\"$path\")")
                "DELETE" -> append("delete(\"$path\")")
                "PATCH" -> append("patch(\"$path\")")
                else -> append("request(HttpMethod.$httpMethod, \"$path\")")
            }
            // 添加路径变量占位（可优化）
            if (endpoint.pathVariables.isNotEmpty()) {
                append(".param(\"${endpoint.pathVariables.first().name}\", \"testValue\")")
            }
            // 添加请求参数
            for (param in endpoint.requestParams) {
                append(".param(\"${param.name}\", \"testValue\")")
            }
            // 添加 request body（如果有）
            if (requestBodyType != null && useAI) {
                val mockJson = aiService?.generateMockRequestBody(endpoint)
                if (mockJson == null) {
                    log.warn("AI request-body generation unavailable for $httpMethod $path; using fallback JSON")
                }
                val content = mockJson ?: "{\"field\":\"value\"}"
                append(".contentType(MediaType.APPLICATION_JSON).content(\"$content\")")
            } else if (requestBodyType != null) {
                append(".contentType(MediaType.APPLICATION_JSON).content(\"{}\")")
            }
            append(")")
        }

        // 期望结果：状态码 200，简单断言
        val resultActions = ".andExpect(status().isOk())"

        return """
    @Test
    void test${capitalizedName}() throws Exception {
        // Generated test for endpoint: $httpMethod $path
        $performBlock
            .andDo(print())
            $resultActions;
    }
        """.trimIndent()
    }
}

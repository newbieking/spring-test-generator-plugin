package com.newbieking.springtestgen.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.newbieking.springtestgen.psi.EndpointMetadata
import kotlinx.coroutines.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * OpenAI 兼容 API 客户端，实现 AI 生成功能
 */
class OpenAIApiClient(private val settings: SettingsService) : AIGenerationService {

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val log = Logger.getInstance(OpenAIApiClient::class.java)

    override suspend fun generateMockRequestBody(metadata: EndpointMetadata): String? {
        val bodyType = metadata.requestBodyType ?: return null
        val prompt = """
            You are a test data generator. Given a Java/Kotlin class type: $bodyType
            Generate a realistic JSON object that could be used as a request body for a Spring Boot controller.
            The JSON should have meaningful field names and values. Only output the JSON, no other text, no markdown fences.
            Example: {"id":1, "name":"John Doe", "email":"john@example.com"}
        """.trimIndent()

        return callAI(prompt)
    }

    override suspend fun generateExpectedResponse(metadata: EndpointMetadata): String? {
        val prompt = """
            Generate a JSON object that represents a typical success response for a ${metadata.httpMethod} request to ${metadata.path}.
            Only output the JSON, no other text, no markdown fences.
        """.trimIndent()
        return callAI(prompt)
    }

    private suspend fun callAI(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val config = settings.getConfig()
                log.info("Sending AI generation request (model: ${config.model}, endpoint: ${config.baseUrl.trimEnd('/')}/chat/completions)")

                // 构建 messages JSON 数组
                val messagesArray = JsonArray().apply {
                    val userMessage = JsonObject().apply {
                        addProperty("role", "user")
                        addProperty("content", prompt)
                    }
                    add(userMessage)
                }

                val requestBody = JsonObject().apply {
                    addProperty("model", config.model)
                    add("messages", messagesArray)
                }

                val request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create("${config.baseUrl.trimEnd('/')}/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer ${config.apiKey}")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                    .timeout(Duration.ofSeconds(30))
                    .build()

                val response = client.send(request, HttpResponse.BodyHandlers.ofString())
                if (response.statusCode() == 200) {
                    val json = JsonParser.parseString(response.body()).asJsonObject
                    val choices = json.get("choices")?.asJsonArray
                    if (choices != null && choices.size() > 0) {
                        val content = choices.get(0).asJsonObject
                            .get("message")?.asJsonObject
                            ?.get("content")?.asString
                        log.info("AI generation request completed successfully (response length: ${content?.length ?: 0})")
                        content
                    } else {
                        log.warn("AI API returned empty or missing choices array")
                        null
                    }
                } else {
                    log.warn("AI API request failed with HTTP ${response.statusCode()} (response length: ${response.body().length})")
                    null
                }
            } catch (e: Exception) {
                log.error("AI call failed", e)
                null
            }
        }
    }
}

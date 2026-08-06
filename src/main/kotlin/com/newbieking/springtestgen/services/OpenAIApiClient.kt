package com.newbieking.springtestgen.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.newbieking.springtestgen.prompt.PromptTemplateService
import com.newbieking.springtestgen.psi.EndpointMetadata
import kotlinx.coroutines.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * OpenAI-compatible API client that implements AI generation via prompt templates.
 *
 * All prompt text is resolved through [PromptTemplateService], making prompts
 * configurable and centralised. If a template is missing, the client falls back
 * to a minimal hardcoded prompt and logs a warning.
 */
class OpenAIApiClient(
    private val project: Project,
    private val promptService: PromptTemplateService = PromptTemplateService()
) : AIGenerationService {

    private val settings: SettingsService by lazy { SettingsService.getInstance(project) }

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    private val log = Logger.getInstance(OpenAIApiClient::class.java)

    override suspend fun generateMockRequestBody(metadata: EndpointMetadata): String? {
        val bodyType = metadata.requestBodyType ?: return null
        val resolved = promptService.resolve(
            PromptTemplateService.CONTROLLER_MOCK_REQUEST_BODY,
            mapOf("bodyType" to bodyType)
        )
        if (resolved.unresolvedVariables.isNotEmpty()) {
            log.info("Mock request body template has unresolved variables: ${resolved.unresolvedVariables}")
        }
        return callAI(resolved.text)
    }

    override suspend fun generateExpectedResponse(metadata: EndpointMetadata): String? {
        val resolved = promptService.resolve(
            PromptTemplateService.CONTROLLER_EXPECTED_RESPONSE,
            mapOf(
                "httpMethod" to metadata.httpMethod.name,
                "path" to metadata.path
            )
        )
        if (resolved.unresolvedVariables.isNotEmpty()) {
            log.info("Expected response template has unresolved variables: ${resolved.unresolvedVariables}")
        }
        return callAI(resolved.text)
    }

    private suspend fun callAI(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val config = settings.getConfig()
                log.info("Sending AI generation request (model: ${config.model}, endpoint: ${config.baseUrl.trimEnd('/')}/chat/completions)")

                // Build messages JSON array
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
package com.newbieking.springtestgen.services

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.newbieking.springtestgen.psi.EndpointMetadata
import kotlinx.coroutines.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * OpenAI-compatible API client that implements [AIProvider].
 *
 * Supports any OpenAI-compatible endpoint (OpenAI, Azure OpenAI, local LLMs, etc.)
 * by configuring [AIProviderConfig.baseUrl] and [AIProviderConfig.model].
 *
 * All prompt text is resolved externally by the caller (via PromptTemplateService),
 * making prompts configurable and centralised. This provider receives resolved
 * prompt strings and never accesses PSI.
 */
class OpenAIApiClient(
    private val project: Project,
    private val config: AIProviderConfig
) : AIProvider {

    override val id: String = config.id
    override val displayName: String = config.displayName

    private val settings: SettingsService by lazy { SettingsService.getInstance(project) }

    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
        .build()

    private val log = Logger.getInstance(OpenAIApiClient::class.java)

    override fun isAvailable(): Boolean {
        return config.enabled && config.apiKey.isNotBlank() && config.baseUrl.isNotBlank()
    }

    override suspend fun generateMockRequestBody(metadata: EndpointMetadata, prompt: String): AIGenerationResult {
        return callAI(prompt)
    }

    override suspend fun generateExpectedResponse(metadata: EndpointMetadata, prompt: String): AIGenerationResult {
        return callAI(prompt)
    }

    override suspend fun generateFromPrompt(prompt: String): AIGenerationResult {
        return callAI(prompt)
    }

    /**
     * Execute an AI API call with retry logic.
     *
     * Retries up to [AIProviderConfig.maxRetries] times on failure,
     * with a simple linear backoff (1s, 2s, 3s...).
     */
    private suspend fun callAI(prompt: String): AIGenerationResult {
        var lastError: String? = null
        val maxAttempts = config.maxRetries.coerceAtLeast(1)

        for (attempt in 1..maxAttempts) {
            val startTime = System.currentTimeMillis()
            try {
                val result = performCall(prompt)
                val latencyMs = System.currentTimeMillis() - startTime
                if (result != null) {
                    return AIGenerationResult(
                        content = result,
                        providerId = id,
                        success = true,
                        latencyMs = latencyMs
                    )
                }
                lastError = "AI API returned null content"
                log.warn("Attempt $attempt/$maxAttempts: AI API returned null for provider $id")
            } catch (e: Exception) {
                lastError = e.message ?: "Unknown error"
                val latencyMs = System.currentTimeMillis() - startTime
                log.warn("Attempt $attempt/$maxAttempts: AI call failed for provider $id: ${e.message}")
                if (attempt < maxAttempts) {
                    delay(attempt * 1000L)
                }
            }
        }

        return AIGenerationResult(
            content = null,
            providerId = id,
            success = false,
            errorMessage = lastError ?: "All retry attempts exhausted"
        )
    }

    /**
     * Perform a single HTTP call to the OpenAI-compatible API.
     * Returns the content string on success, or null on failure.
     */
    private suspend fun performCall(prompt: String): String? {
        return withContext(Dispatchers.IO) {
            try {
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
                    .timeout(Duration.ofSeconds(config.timeoutSeconds.toLong()))
                    .build()

                log.info("Sending AI request to $displayName (model: ${config.model}, endpoint: ${config.baseUrl.trimEnd('/')}/chat/completions)")

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
package com.newbieking.springtestgen.prompt

import com.intellij.openapi.diagnostic.Logger

/**
 * Centralized prompt template management service.
 *
 * Loads built-in default templates, supports variable interpolation,
 * and provides graceful fallback when templates are missing or incomplete.
 *
 * This service is PSI-free and safe to call from any thread.
 */
class PromptTemplateService {

    private val log = Logger.getInstance(PromptTemplateService::class.java)

    private val templates = mutableMapOf<TemplateId, PromptTemplate>()

    init {
        loadDefaults()
    }

    /**
     * Resolve a template by ID, substituting provided variable values.
     *
     * Missing variables are left as `{{name}}` placeholders in the output
     * and reported in [ResolvedPrompt.unresolvedVariables].
     *
     * @return resolved prompt, or a fallback prompt if the template ID is not found
     */
    fun resolve(id: TemplateId, variables: Map<String, String> = emptyMap()): ResolvedPrompt {
        val template = templates[id]
        if (template == null) {
            log.warn("Template not found: ${id.id}; returning fallback")
            return ResolvedPrompt(
                templateId = id,
                text = "[Missing template: ${id.id}]",
                unresolvedVariables = emptySet()
            )
        }
        return resolveTemplate(template, variables)
    }

    /**
     * Register or override a template at runtime.
     * Used for project-level custom templates or test overrides.
     */
    fun register(template: PromptTemplate) {
        templates[template.id] = template
        log.info("Registered prompt template: ${template.id.id} (${template.variables.size} variable(s))")
    }

    /**
     * Remove a registered template. Returns true if the template existed.
     */
    fun unregister(id: TemplateId): Boolean = templates.remove(id) != null

    /**
     * List all registered template IDs.
     */
    fun listTemplateIds(): Set<TemplateId> = templates.keys.toSet()

    /**
     * Get a registered template by ID, or null if not found.
     */
    fun getTemplate(id: TemplateId): PromptTemplate? = templates[id]

    /**
     * Check if a template is registered.
     */
    fun hasTemplate(id: TemplateId): Boolean = templates.containsKey(id)

    private fun resolveTemplate(template: PromptTemplate, variables: Map<String, String>): ResolvedPrompt {
        val unresolved = mutableSetOf<String>()
        var resolvedText = template.text
        for (varName in template.variables) {
            val value = variables[varName]
            if (value != null) {
                resolvedText = resolvedText.replace("{{$varName}}", value)
            } else {
                unresolved.add(varName)
            }
        }
        if (unresolved.isNotEmpty()) {
            log.info("Template ${template.id.id} has unresolved variables: $unresolved")
        }
        return ResolvedPrompt(
            templateId = template.id,
            text = resolvedText,
            unresolvedVariables = unresolved
        )
    }

    /**
     * Load built-in default templates.
     * These cover the core use cases: controller mock request/response,
     * class-level test generation prompts, and scenario-specific prompts.
     */
    private fun loadDefaults() {
        // Controller-level AI prompts
        register(PromptTemplate(
            id = TemplateId("controller.mock-request-body"),
            text = """
                You are a test data generator. Given a Java/Kotlin class type: {{bodyType}}
                Generate a realistic JSON object that could be used as a request body for a Spring Boot controller.
                The JSON should have meaningful field names and values. Only output the JSON, no other text, no markdown fences.
                Example: {"id":1, "name":"John Doe", "email":"john@example.com"}
            """.trimIndent(),
            description = "Generate a mock JSON request body for a controller endpoint"
        ))

        register(PromptTemplate(
            id = TemplateId("controller.expected-response"),
            text = """
                Generate a JSON object that represents a typical success response for a {{httpMethod}} request to {{path}}.
                Only output the JSON, no other text, no markdown fences.
            """.trimIndent(),
            description = "Generate an expected success response JSON for a controller endpoint"
        ))

        // Class-level AI prompts
        register(PromptTemplate(
            id = TemplateId("class.test-scenario"),
            text = """
                Generate a JUnit 5 test method for {{className}}.{{methodName}}({{parameterTypes}}).
                The class is a {{targetType}} with dependencies: {{dependencies}}.
                Scenario type: {{scenarioType}}.
                Generate only the test method body, no class wrapper. Use {{testFramework}} style.
            """.trimIndent(),
            description = "Generate a test scenario for a non-Controller class method"
        ))

        // Risk scenario prompts (for future #17)
        register(PromptTemplate(
            id = TemplateId("scenario.security"),
            text = """
                Generate security test scenarios for {{httpMethod}} {{path}} on {{controllerName}}.
                Consider: SQL injection, XSS, path traversal, authentication bypass, and authorization violations.
                For each scenario, provide the test method name, input description, and expected assertion.
                Output format: one scenario per line as "methodName | input | assertion".
            """.trimIndent(),
            description = "Generate security-focused test scenarios for a controller endpoint"
        ))

        register(PromptTemplate(
            id = TemplateId("scenario.idempotency"),
            text = """
                Generate idempotency test scenarios for {{httpMethod}} {{path}} on {{controllerName}}.
                Consider: duplicate identical requests, concurrent identical requests, and retry-after-failure.
                For each scenario, provide the test method name, description, and expected behavior.
                Output format: one scenario per line as "methodName | description | expectedBehavior".
            """.trimIndent(),
            description = "Generate idempotency test scenarios for a controller endpoint"
        ))

        register(PromptTemplate(
            id = TemplateId("scenario.concurrency"),
            text = """
                Generate concurrency test scenario skeletons for {{className}}.{{methodName}}.
                Consider: optimistic lock conflict, concurrent read/write, and race conditions on shared state.
                Generate JUnit 5 test method skeletons with TODO placeholders for actual concurrency setup.
                Use {{testFramework}} style.
            """.trimIndent(),
            description = "Generate concurrency test scenario skeletons for a class method"
        ))

        register(PromptTemplate(
            id = TemplateId("scenario.state-machine"),
            text = """
                Generate state-machine test scenarios for {{className}}.
                Consider: valid state transitions (create -> update -> delete) and invalid transitions (delete before create, update after delete).
                Generate JUnit 5 test method skeletons with TODO placeholders for business assertions.
                Use {{testFramework}} style.
            """.trimIndent(),
            description = "Generate state-machine test scenarios for a class"
        ))

        log.info("Loaded ${templates.size} default prompt templates")
    }

    companion object {
        /** Well-known template IDs for type-safe access. */
        val CONTROLLER_MOCK_REQUEST_BODY = TemplateId("controller.mock-request-body")
        val CONTROLLER_EXPECTED_RESPONSE = TemplateId("controller.expected-response")
        val CLASS_TEST_SCENARIO = TemplateId("class.test-scenario")
        val SCENARIO_SECURITY = TemplateId("scenario.security")
        val SCENARIO_IDEMPOTENCY = TemplateId("scenario.idempotency")
        val SCENARIO_CONCURRENCY = TemplateId("scenario.concurrency")
        val SCENARIO_STATE_MACHINE = TemplateId("scenario.state-machine")
    }
}
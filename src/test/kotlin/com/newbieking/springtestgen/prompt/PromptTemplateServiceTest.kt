package com.newbieking.springtestgen.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptTemplateServiceTest {

    private val service = PromptTemplateService()

    // --- resolve() with built-in templates ---

    @Test
    fun `resolves controller mock-request-body template with bodyType variable`() {
        val resolved = service.resolve(
            PromptTemplateService.CONTROLLER_MOCK_REQUEST_BODY,
            mapOf("bodyType" to "com.example.UserDto")
        )
        assertTrue(resolved.text.contains("com.example.UserDto"))
        assertTrue(resolved.unresolvedVariables.isEmpty())
    }

    @Test
    fun `resolves controller expected-response template with httpMethod and path`() {
        val resolved = service.resolve(
            PromptTemplateService.CONTROLLER_EXPECTED_RESPONSE,
            mapOf("httpMethod" to "POST", "path" to "/api/users")
        )
        assertTrue(resolved.text.contains("POST"))
        assertTrue(resolved.text.contains("/api/users"))
        assertTrue(resolved.unresolvedVariables.isEmpty())
    }

    @Test
    fun `reports unresolved variables when not all are provided`() {
        val resolved = service.resolve(
            PromptTemplateService.CLASS_TEST_SCENARIO,
            mapOf("className" to "UserService")
        )
        assertTrue(resolved.unresolvedVariables.isNotEmpty())
        assertTrue(resolved.unresolvedVariables.contains("methodName"))
        // Provided variable is substituted
        assertTrue(resolved.text.contains("UserService"))
        // Unresolved variables remain as {{name}} placeholders
        assertTrue(resolved.text.contains("{{methodName}}"))
    }

    @Test
    fun `returns fallback for missing template ID`() {
        val missingId = TemplateId("nonexistent.template")
        val resolved = service.resolve(missingId)
        assertTrue(resolved.text.contains("[Missing template: nonexistent.template]"))
        assertTrue(resolved.unresolvedVariables.isEmpty())
    }

    // --- register / unregister ---

    @Test
    fun `registers and resolves a custom template`() {
        val customId = TemplateId("custom.test")
        val template = PromptTemplate(
            id = customId,
            text = "Hello {{name}}, welcome to {{place}}.",
            description = "A custom test template"
        )
        service.register(template)

        val resolved = service.resolve(customId, mapOf("name" to "Alice", "place" to "Wonderland"))
        assertEquals("Hello Alice, welcome to Wonderland.", resolved.text)
        assertTrue(resolved.unresolvedVariables.isEmpty())
    }

    @Test
    fun `unregister removes a previously registered template`() {
        val customId = TemplateId("custom.removable")
        service.register(PromptTemplate(id = customId, text = "Remove me {{var}}"))
        assertTrue(service.hasTemplate(customId))

        val removed = service.unregister(customId)
        assertTrue(removed)
        assertFalse(service.hasTemplate(customId))

        val resolved = service.resolve(customId)
        assertTrue(resolved.text.contains("[Missing template: custom.removable]"))
    }

    @Test
    fun `unregister returns false for non-existent template`() {
        assertFalse(service.unregister(TemplateId("does.not.exist")))
    }

    // --- listTemplateIds / hasTemplate / getTemplate ---

    @Test
    fun `lists all built-in template IDs`() {
        val ids = service.listTemplateIds()
        assertTrue(ids.contains(PromptTemplateService.CONTROLLER_MOCK_REQUEST_BODY))
        assertTrue(ids.contains(PromptTemplateService.CONTROLLER_EXPECTED_RESPONSE))
        assertTrue(ids.contains(PromptTemplateService.CLASS_TEST_SCENARIO))
        assertTrue(ids.contains(PromptTemplateService.SCENARIO_SECURITY))
        assertTrue(ids.contains(PromptTemplateService.SCENARIO_IDEMPOTENCY))
        assertTrue(ids.contains(PromptTemplateService.SCENARIO_CONCURRENCY))
        assertTrue(ids.contains(PromptTemplateService.SCENARIO_STATE_MACHINE))
        assertEquals(7, ids.size)
    }

    @Test
    fun `hasTemplate returns true for built-in and false for unknown`() {
        assertTrue(service.hasTemplate(PromptTemplateService.CONTROLLER_MOCK_REQUEST_BODY))
        assertFalse(service.hasTemplate(TemplateId("unknown")))
    }

    @Test
    fun `getTemplate returns template for known ID and null for unknown`() {
        assertNotNull(service.getTemplate(PromptTemplateService.CONTROLLER_MOCK_REQUEST_BODY))
        assertNull(service.getTemplate(TemplateId("unknown")))
    }

    // --- Template variable extraction ---

    @Test
    fun `extracts variables from template text`() {
        val template = PromptTemplate(
            id = TemplateId("test.vars"),
            text = "{{one}} and {{two}} and {{one_again}}"
        )
        assertEquals(setOf("one", "two", "one_again"), template.variables)
    }

    @Test
    fun `template with no variables has empty variable set`() {
        val template = PromptTemplate(
            id = TemplateId("test.no-vars"),
            text = "No variables here."
        )
        assertTrue(template.variables.isEmpty())
    }

    // --- Scenario templates ---

    @Test
    fun `resolves security scenario template`() {
        val resolved = service.resolve(
            PromptTemplateService.SCENARIO_SECURITY,
            mapOf("httpMethod" to "POST", "path" to "/api/admin", "controllerName" to "AdminController")
        )
        assertTrue(resolved.text.contains("POST"))
        assertTrue(resolved.text.contains("/api/admin"))
        assertTrue(resolved.text.contains("AdminController"))
        assertTrue(resolved.unresolvedVariables.isEmpty())
    }

    @Test
    fun `resolves concurrency scenario template`() {
        val resolved = service.resolve(
            PromptTemplateService.SCENARIO_CONCURRENCY,
            mapOf("className" to "OrderService", "methodName" to "update", "testFramework" to "MockMvc")
        )
        assertTrue(resolved.text.contains("OrderService"))
        assertTrue(resolved.text.contains("update"))
        assertTrue(resolved.unresolvedVariables.isEmpty())
    }
}
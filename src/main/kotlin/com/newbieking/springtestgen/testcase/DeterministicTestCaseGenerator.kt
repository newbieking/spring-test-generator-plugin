package com.newbieking.springtestgen.testcase

import com.google.gson.JsonArray
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.psi.RequestBodySchema
import com.newbieking.springtestgen.psi.RequestFieldSchema
import com.newbieking.springtestgen.psi.RequestParam
import java.math.BigDecimal

/** Produces deterministic baseline and validation scenarios without an AI provider. */
class DeterministicTestCaseGenerator {

    fun generate(endpoints: List<EndpointMetadata>): List<TestCaseModel> = endpoints.flatMap(::generate)

    fun generate(endpoint: EndpointMetadata): List<TestCaseModel> =
        plan(endpoint.requestParams, endpoint.requestBodySchema, endpoint.requestBodyValidated).map { plan ->
            TestCaseModel(
                id = "${endpoint.methodName}:${plan.idSuffix}",
                displayName = plan.displayName,
                endpoint = endpoint,
                scenarioType = plan.scenarioType,
                omittedRequestParameters = plan.omittedRequestParameters,
                requestBodyJson = plan.requestBodyJson,
                expectedStatus = plan.expectedStatus
            )
        }

    fun plan(
        requestParams: List<RequestParam>,
        bodySchema: RequestBodySchema? = null,
        bodyValidated: Boolean = false
    ): List<TestScenarioPlan> {
        val plans = mutableListOf(
            TestScenarioPlan(
                idSuffix = "happy-path",
                displayName = "Happy path",
                scenarioType = TestScenarioType.HAPPY_PATH,
                requestBodyJson = buildValidBody(bodySchema)
            )
        )
        requestParams.filter { it.required }.forEach { parameter ->
            plans += TestScenarioPlan(
                idSuffix = "missing-${parameter.name}",
                displayName = "Missing required parameter '${parameter.name}'",
                scenarioType = TestScenarioType.MISSING_REQUIRED_PARAMETER,
                omittedRequestParameters = setOf(parameter.name),
                expectedStatus = ExpectedHttpStatus.BAD_REQUEST
            )
        }
        if (bodyValidated && bodySchema != null) {
            bodySchema.fields.forEach { field -> addFieldPlans(plans, bodySchema, field) }
        }
        return plans
    }

    private fun addFieldPlans(plans: MutableList<TestScenarioPlan>, schema: RequestBodySchema, field: RequestFieldSchema) {
        val constraints = field.constraints
        if (constraints.required) {
            plans += bodyMutation(
                schema, "missing-${field.name}", "Missing required body field '${field.name}'",
                TestScenarioType.MISSING_REQUIRED_BODY_FIELD
            ) { it.remove(field.name) }
            plans += bodyMutation(
                schema, "null-${field.name}", "Null required body field '${field.name}'",
                TestScenarioType.NULL_REQUIRED_BODY_FIELD
            ) { it.add(field.name, JsonNull.INSTANCE) }
        }
        if (constraints.notBlank) {
            plans += bodyMutation(
                schema, "blank-${field.name}", "Blank body field '${field.name}'",
                TestScenarioType.BLANK_BODY_FIELD
            ) { it.addProperty(field.name, "") }
        }
        constraints.minLength?.takeIf { it > 0 }?.let { minLength ->
            plans += bodyMutation(
                schema, "short-${field.name}", "Body field '${field.name}' below minimum length",
                TestScenarioType.BODY_FIELD_TOO_SHORT
            ) { it.addProperty(field.name, "a".repeat((minLength - 1).coerceAtLeast(0))) }
        }
        constraints.maxLength?.let { maxLength ->
            plans += bodyMutation(
                schema, "long-${field.name}", "Body field '${field.name}' above maximum length",
                TestScenarioType.BODY_FIELD_TOO_LONG
            ) { it.addProperty(field.name, "a".repeat((maxLength + 1).coerceAtMost(1024))) }
        }
        constraints.minimum?.toBigDecimalOrNull()?.let { minimum ->
            plans += bodyMutation(
                schema, "below-min-${field.name}", "Body field '${field.name}' below minimum",
                TestScenarioType.BODY_FIELD_BELOW_MINIMUM
            ) { it.add(field.name, JsonPrimitive(minimum - BigDecimal.ONE)) }
        }
        constraints.maximum?.toBigDecimalOrNull()?.let { maximum ->
            plans += bodyMutation(
                schema, "above-max-${field.name}", "Body field '${field.name}' above maximum",
                TestScenarioType.BODY_FIELD_ABOVE_MAXIMUM
            ) { it.add(field.name, JsonPrimitive(maximum + BigDecimal.ONE)) }
        }
    }

    private fun bodyMutation(
        schema: RequestBodySchema,
        idSuffix: String,
        displayName: String,
        scenarioType: TestScenarioType,
        mutation: (JsonObject) -> Unit
    ): TestScenarioPlan {
        val body = buildValidBodyObject(schema)
        mutation(body)
        return TestScenarioPlan(
            idSuffix = idSuffix,
            displayName = displayName,
            scenarioType = scenarioType,
            requestBodyJson = body.toString(),
            expectedStatus = ExpectedHttpStatus.BAD_REQUEST
        )
    }

    private fun buildValidBody(schema: RequestBodySchema?): String? = schema?.let(::buildValidBodyObject)?.toString()

    private fun buildValidBodyObject(schema: RequestBodySchema): JsonObject = JsonObject().apply {
        schema.fields.forEach { field -> add(field.name, validValue(field)) }
    }

    private fun validValue(field: RequestFieldSchema) = when {
        field.defaultValue != null -> JsonPrimitive(field.defaultValue)
        field.type in setOf("boolean", "java.lang.Boolean") -> JsonPrimitive(true)
        field.type in setOf("byte", "short", "int", "long", "float", "double", "java.lang.Byte", "java.lang.Short", "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.math.BigDecimal") ->
            JsonPrimitive(field.constraints.minimum?.toBigDecimalOrNull() ?: BigDecimal.ONE)
        field.type.endsWith("[]") || field.type.startsWith("java.util.List") || field.type.startsWith("java.util.Set") -> JsonArray()
        field.type.startsWith("java.util.Map") -> JsonObject()
        else -> JsonPrimitive(stringValue(field))
    }

    private fun stringValue(field: RequestFieldSchema): String {
        val length = field.constraints.minLength?.coerceAtLeast(1) ?: 5
        return "value".padEnd(length, 'x').take(field.constraints.maxLength ?: Int.MAX_VALUE)
    }
}

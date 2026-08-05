package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.psi.RequestParam
import com.newbieking.springtestgen.psi.RequestBodySchema
import com.newbieking.springtestgen.psi.RequestFieldSchema
import com.newbieking.springtestgen.psi.ValidationConstraints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeterministicTestCaseGeneratorTest {

    private val generator = DeterministicTestCaseGenerator()

    @Test
    fun `creates a happy path and one missing case per required parameter`() {
        val plans = generator.plan(
            requestParams = listOf(
                RequestParam("keyword", "java.lang.String", true),
                RequestParam("page", "int", false),
                RequestParam("size", "int", true)
            )
        )

        assertEquals(3, plans.size)
        assertEquals(TestScenarioType.HAPPY_PATH, plans.first().scenarioType)
        assertEquals(setOf("keyword"), plans[1].omittedRequestParameters)
        assertEquals(setOf("size"), plans[2].omittedRequestParameters)
        assertTrue(plans.drop(1).all { it.expectedStatus == ExpectedHttpStatus.BAD_REQUEST })
    }

    @Test
    fun `only creates a happy path when no parameters are required`() {
        val plans = generator.plan(
            requestParams = listOf(RequestParam("page", "int", false))
        )

        assertEquals(1, plans.size)
        assertEquals("happy-path", plans.single().idSuffix)
    }

    @Test
    fun `creates null blank length and numeric boundary body cases for validated dto`() {
        val schema = RequestBodySchema(
            typeName = "CreateOrderRequest",
            qualifiedName = "example.CreateOrderRequest",
            fields = listOf(
                RequestFieldSchema(
                    name = "username",
                    type = "java.lang.String",
                    constraints = ValidationConstraints(
                        required = true,
                        notBlank = true,
                        minLength = 3,
                        maxLength = 5
                    )
                ),
                RequestFieldSchema(
                    name = "amount",
                    type = "int",
                    constraints = ValidationConstraints(minimum = "18", maximum = "60")
                )
            )
        )

        val plans = generator.plan(emptyList(), schema, bodyValidated = true)

        assertTrue(plans.any { it.scenarioType == TestScenarioType.MISSING_REQUIRED_BODY_FIELD && !it.requestBodyJson.orEmpty().contains("username") })
        assertTrue(plans.any { it.scenarioType == TestScenarioType.NULL_REQUIRED_BODY_FIELD && it.requestBodyJson.orEmpty().contains("\"username\":null") })
        assertTrue(plans.any { it.scenarioType == TestScenarioType.BLANK_BODY_FIELD && it.requestBodyJson.orEmpty().contains("\"username\":\"\"") })
        assertTrue(plans.any { it.scenarioType == TestScenarioType.BODY_FIELD_TOO_SHORT && it.requestBodyJson.orEmpty().contains("\"aa\"") })
        assertTrue(plans.any { it.scenarioType == TestScenarioType.BODY_FIELD_TOO_LONG && it.requestBodyJson.orEmpty().contains("\"aaaaaa\"") })
        assertTrue(plans.any { it.scenarioType == TestScenarioType.BODY_FIELD_BELOW_MINIMUM && it.requestBodyJson.orEmpty().contains("\"amount\":17") })
        assertTrue(plans.any { it.scenarioType == TestScenarioType.BODY_FIELD_ABOVE_MAXIMUM && it.requestBodyJson.orEmpty().contains("\"amount\":61") })
    }

    @Test
    fun `recurses into nested dto fields while keeping the complete request body`() {
        val schema = RequestBodySchema(
            typeName = "CreateOrderRequest",
            qualifiedName = "example.CreateOrderRequest",
            fields = listOf(
                RequestFieldSchema(
                    name = "customer",
                    type = "example.Customer",
                    constraints = ValidationConstraints(required = true),
                    nestedSchema = RequestBodySchema(
                        typeName = "Customer",
                        qualifiedName = "example.Customer",
                        fields = listOf(
                            RequestFieldSchema(
                                name = "name",
                                type = "java.lang.String",
                                constraints = ValidationConstraints(
                                    required = true,
                                    notBlank = true,
                                    minLength = 2
                                )
                            )
                        )
                    )
                ),
                RequestFieldSchema(name = "orderNo", type = "java.lang.String")
            )
        )

        val plans = generator.plan(emptyList(), schema, bodyValidated = true)

        assertTrue(plans.first().requestBodyJson.orEmpty().contains("\"customer\":{\"name\":\"value\"}"))
        assertTrue(plans.any {
            it.idSuffix == "missing-customer" &&
                it.requestBodyJson.orEmpty().contains("\"orderNo\":\"value\"") &&
                !it.requestBodyJson.orEmpty().contains("\"customer\"")
        })
        assertTrue(plans.any {
            it.idSuffix == "null-customer" &&
                it.requestBodyJson.orEmpty().contains("\"customer\":null")
        })
        assertTrue(plans.any {
            it.idSuffix == "missing-customer-name" &&
                it.requestBodyJson.orEmpty().contains("\"customer\":{}") &&
                it.requestBodyJson.orEmpty().contains("\"orderNo\":\"value\"")
        })
        assertTrue(plans.any {
            it.idSuffix == "short-customer-name" &&
                it.requestBodyJson.orEmpty().contains("\"customer\":{\"name\":\"a\"}")
        })
    }
}

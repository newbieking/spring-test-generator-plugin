package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.psi.RequestParam
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
}

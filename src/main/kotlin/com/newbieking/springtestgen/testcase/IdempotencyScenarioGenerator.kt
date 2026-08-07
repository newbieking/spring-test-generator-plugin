package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.psi.HttpMethod

/**
 * Generates idempotency risk scenarios for Controller endpoints.
 *
 * Produces two categories of deterministic skeletons:
 * - **IDEMPOTENCY_DUPLICATE_REQUEST**: Sends the same request twice sequentially,
 *   expecting the second to return CONFLICT (or be idempotently handled).
 * - **IDEMPOTENCY_CONCURRENT_DUPLICATE**: Sends the same request concurrently,
 *   expecting one of the concurrent requests to return CONFLICT.
 *
 * Only applicable to mutating HTTP methods (POST, PUT, PATCH) that are not
 * naturally idempotent by HTTP specification.
 */
class IdempotencyScenarioGenerator : EndpointRiskScenarioGenerator {

    override val label: RiskScenarioLabel = RiskScenarioLabel.IDEMPOTENCY

    override fun generate(endpoint: EndpointMetadata): List<TestCaseModel> {
        // Only generate idempotency scenarios for mutating methods
        if (endpoint.httpMethod !in MUTATING_METHODS) return emptyList()

        val method = endpoint.methodName
        return listOf(
            TestCaseModel(
                id = "$method:idempotency-duplicate-request",
                displayName = "Duplicate request should return CONFLICT or be idempotent",
                endpoint = endpoint,
                scenarioType = TestScenarioType.IDEMPOTENCY_DUPLICATE_REQUEST,
                requestBodyJson = null,
                expectedStatus = ExpectedHttpStatus.CONFLICT,
                riskLabel = RiskScenarioLabel.IDEMPOTENCY
            ),
            TestCaseModel(
                id = "$method:idempotency-concurrent-duplicate",
                displayName = "Concurrent duplicate request should return CONFLICT",
                endpoint = endpoint,
                scenarioType = TestScenarioType.IDEMPOTENCY_CONCURRENT_DUPLICATE,
                requestBodyJson = null,
                expectedStatus = ExpectedHttpStatus.CONFLICT,
                riskLabel = RiskScenarioLabel.IDEMPOTENCY
            )
        )
    }

    companion object {
        private val MUTATING_METHODS = setOf(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH)
    }
}
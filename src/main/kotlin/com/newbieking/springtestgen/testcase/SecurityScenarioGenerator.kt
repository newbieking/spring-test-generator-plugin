package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.psi.HttpMethod

/**
 * Generates security risk scenarios for Controller endpoints.
 *
 * Produces three categories of deterministic skeletons:
 * - **SECURITY_INJECTION**: Sends common injection payloads (SQL injection, XSS, path traversal)
 *   in request parameters and body fields, expecting BAD_REQUEST or a sanitised response.
 * - **SECURITY_AUTH_BYPASS**: Sends requests without authentication context,
 *   expecting UNAUTHORIZED or FORBIDDEN.
 * - **SECURITY_SENSITIVE_DATA**: Verifies that sensitive fields (passwords, tokens, secrets)
 *   are not leaked in the response body, expecting OK but with a TODO assertion placeholder.
 */
class SecurityScenarioGenerator : EndpointRiskScenarioGenerator {

    override val label: RiskScenarioLabel = RiskScenarioLabel.SECURITY

    override fun generate(endpoint: EndpointMetadata): List<TestCaseModel> = buildList {
        addAll(generateInjectionScenarios(endpoint))
        addAll(generateAuthBypassScenarios(endpoint))
        addAll(generateSensitiveDataScenarios(endpoint))
    }

    // --- Injection scenarios ---

    private fun generateInjectionScenarios(endpoint: EndpointMetadata): List<TestCaseModel> {
        val scenarios = mutableListOf<TestCaseModel>()
        val method = endpoint.methodName

        // SQL injection payloads in request params
        endpoint.requestParams.forEach { param ->
            scenarios += TestCaseModel(
                id = "$method:security-injection-sql-${param.name}",
                displayName = "SQL injection in parameter '${param.name}'",
                endpoint = endpoint,
                scenarioType = TestScenarioType.SECURITY_INJECTION,
                requestBodyJson = null,
                expectedStatus = ExpectedHttpStatus.BAD_REQUEST,
                riskLabel = RiskScenarioLabel.SECURITY
            )
        }

        // XSS payload in body
        if (endpoint.requestBodyType != null) {
            scenarios += TestCaseModel(
                id = "$method:security-injection-xss",
                displayName = "XSS injection in request body",
                endpoint = endpoint,
                scenarioType = TestScenarioType.SECURITY_INJECTION,
                requestBodyJson = """{"<script>alert(1)</script>":"<script>alert(1)</script>"}""",
                expectedStatus = ExpectedHttpStatus.BAD_REQUEST,
                riskLabel = RiskScenarioLabel.SECURITY
            )
        }

        // Path traversal in path variables
        endpoint.pathVariables.forEach { pv ->
            scenarios += TestCaseModel(
                id = "$method:security-injection-path-traversal-${pv.name}",
                displayName = "Path traversal in path variable '${pv.name}'",
                endpoint = endpoint,
                scenarioType = TestScenarioType.SECURITY_INJECTION,
                requestBodyJson = null,
                expectedStatus = ExpectedHttpStatus.BAD_REQUEST,
                riskLabel = RiskScenarioLabel.SECURITY
            )
        }

        return scenarios
    }

    // --- Auth bypass scenarios ---

    private fun generateAuthBypassScenarios(endpoint: EndpointMetadata): List<TestCaseModel> {
        val method = endpoint.methodName
        // Generate one UNAUTHORIZED and one FORBIDDEN scenario per endpoint
        return listOf(
            TestCaseModel(
                id = "$method:security-auth-bypass-unauthenticated",
                displayName = "Unauthenticated request should be rejected",
                endpoint = endpoint,
                scenarioType = TestScenarioType.SECURITY_AUTH_BYPASS,
                requestBodyJson = null,
                expectedStatus = ExpectedHttpStatus.UNAUTHORIZED,
                riskLabel = RiskScenarioLabel.SECURITY
            ),
            TestCaseModel(
                id = "$method:security-auth-bypass-unauthorized-role",
                displayName = "Insufficient permissions should be forbidden",
                endpoint = endpoint,
                scenarioType = TestScenarioType.SECURITY_AUTH_BYPASS,
                requestBodyJson = null,
                expectedStatus = ExpectedHttpStatus.FORBIDDEN,
                riskLabel = RiskScenarioLabel.SECURITY
            )
        )
    }

    // --- Sensitive data exposure scenarios ---

    private fun generateSensitiveDataScenarios(endpoint: EndpointMetadata): List<TestCaseModel> {
        val method = endpoint.methodName
        // Only relevant for GET endpoints that return data
        if (endpoint.httpMethod != HttpMethod.GET) return emptyList()

        return listOf(
            TestCaseModel(
                id = "$method:security-sensitive-data-exposure",
                displayName = "Sensitive fields should not be exposed in response",
                endpoint = endpoint,
                scenarioType = TestScenarioType.SECURITY_SENSITIVE_DATA,
                requestBodyJson = null,
                expectedStatus = ExpectedHttpStatus.OK,
                riskLabel = RiskScenarioLabel.SECURITY
            )
        )
    }
}
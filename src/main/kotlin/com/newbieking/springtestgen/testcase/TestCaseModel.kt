package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.psi.EndpointMetadata

/** Framework-neutral representation of one executable endpoint test scenario. */
data class TestCaseModel(
    val id: String,
    val displayName: String,
    val endpoint: EndpointMetadata,
    val scenarioType: TestScenarioType,
    val omittedRequestParameters: Set<String> = emptySet(),
    val requestBodyJson: String? = null,
    val expectedStatus: ExpectedHttpStatus = ExpectedHttpStatus.OK,
    val riskLabel: RiskScenarioLabel? = null
)

enum class TestScenarioType {
    // --- Deterministic scenarios ---
    HAPPY_PATH,
    MISSING_REQUIRED_PARAMETER,
    MISSING_REQUIRED_BODY_FIELD,
    NULL_REQUIRED_BODY_FIELD,
    BLANK_BODY_FIELD,
    BODY_FIELD_TOO_SHORT,
    BODY_FIELD_TOO_LONG,
    BODY_FIELD_BELOW_MINIMUM,
    BODY_FIELD_ABOVE_MAXIMUM,

    // --- Risk scenarios: Security ---
    SECURITY_INJECTION,
    SECURITY_AUTH_BYPASS,
    SECURITY_SENSITIVE_DATA,

    // --- Risk scenarios: Idempotency ---
    IDEMPOTENCY_DUPLICATE_REQUEST,
    IDEMPOTENCY_CONCURRENT_DUPLICATE
}

enum class ExpectedHttpStatus {
    OK,
    BAD_REQUEST,
    UNAUTHORIZED,
    FORBIDDEN,
    CONFLICT
}

/** Risk scenario category labels for policy-based enable/disable. */
enum class RiskScenarioLabel {
    SECURITY,
    IDEMPOTENCY,
    CONCURRENCY,
    STATE_MACHINE
}

/** Pure scenario definition used before it is bound to PSI-derived endpoint metadata. */
data class TestScenarioPlan(
    val idSuffix: String,
    val displayName: String,
    val scenarioType: TestScenarioType,
    val omittedRequestParameters: Set<String> = emptySet(),
    val requestBodyJson: String? = null,
    val expectedStatus: ExpectedHttpStatus = ExpectedHttpStatus.OK,
    val riskLabel: RiskScenarioLabel? = null
)

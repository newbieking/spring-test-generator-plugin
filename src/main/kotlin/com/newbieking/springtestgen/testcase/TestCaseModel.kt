package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.psi.EndpointMetadata

/** Framework-neutral representation of one executable endpoint test scenario. */
data class TestCaseModel(
    val id: String,
    val displayName: String,
    val endpoint: EndpointMetadata,
    val scenarioType: TestScenarioType,
    val omittedRequestParameters: Set<String> = emptySet(),
    val expectedStatus: ExpectedHttpStatus = ExpectedHttpStatus.OK
)

enum class TestScenarioType {
    HAPPY_PATH,
    MISSING_REQUIRED_PARAMETER
}

enum class ExpectedHttpStatus {
    OK,
    BAD_REQUEST
}

/** Pure scenario definition used before it is bound to PSI-derived endpoint metadata. */
data class TestScenarioPlan(
    val idSuffix: String,
    val displayName: String,
    val scenarioType: TestScenarioType,
    val omittedRequestParameters: Set<String> = emptySet(),
    val expectedStatus: ExpectedHttpStatus = ExpectedHttpStatus.OK
)

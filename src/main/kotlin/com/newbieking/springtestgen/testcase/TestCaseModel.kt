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

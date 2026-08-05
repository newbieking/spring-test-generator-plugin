package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.psi.EndpointMetadata

/** Produces deterministic baseline cases without depending on an AI provider. */
class DeterministicTestCaseGenerator {

    fun generate(endpoints: List<EndpointMetadata>): List<TestCaseModel> =
        endpoints.flatMap(::generate)

    fun generate(endpoint: EndpointMetadata): List<TestCaseModel> {
        val happyPath = TestCaseModel(
            id = "${endpoint.methodName}:happy-path",
            displayName = "Happy path",
            endpoint = endpoint,
            scenarioType = TestScenarioType.HAPPY_PATH
        )
        val missingRequiredParameters = endpoint.requestParams
            .filter { it.required }
            .map { parameter ->
                TestCaseModel(
                    id = "${endpoint.methodName}:missing-${parameter.name}",
                    displayName = "Missing required parameter '${parameter.name}'",
                    endpoint = endpoint,
                    scenarioType = TestScenarioType.MISSING_REQUIRED_PARAMETER,
                    omittedRequestParameters = setOf(parameter.name),
                    expectedStatus = ExpectedHttpStatus.BAD_REQUEST
                )
            }
        return listOf(happyPath) + missingRequiredParameters
    }
}

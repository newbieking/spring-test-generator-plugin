package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.psi.EndpointMetadata
import com.newbieking.springtestgen.psi.RequestParam

/** Produces deterministic baseline cases without depending on an AI provider. */
class DeterministicTestCaseGenerator {

    fun generate(endpoints: List<EndpointMetadata>): List<TestCaseModel> =
        endpoints.flatMap(::generate)

    fun generate(endpoint: EndpointMetadata): List<TestCaseModel> {
        return plan(endpoint.requestParams).map { plan ->
            TestCaseModel(
                id = "${endpoint.methodName}:${plan.idSuffix}",
                displayName = plan.displayName,
                endpoint = endpoint,
                scenarioType = plan.scenarioType,
                omittedRequestParameters = plan.omittedRequestParameters,
                expectedStatus = plan.expectedStatus
            )
        }
    }

    fun plan(requestParams: List<RequestParam>): List<TestScenarioPlan> {
        val happyPath = TestScenarioPlan(
            idSuffix = "happy-path",
            displayName = "Happy path",
            scenarioType = TestScenarioType.HAPPY_PATH
        )
        val missingRequiredParameters = requestParams
            .filter { it.required }
            .map { parameter ->
                TestScenarioPlan(
                    idSuffix = "missing-${parameter.name}",
                    displayName = "Missing required parameter '${parameter.name}'",
                    scenarioType = TestScenarioType.MISSING_REQUIRED_PARAMETER,
                    omittedRequestParameters = setOf(parameter.name),
                    expectedStatus = ExpectedHttpStatus.BAD_REQUEST
                )
            }
        return listOf(happyPath) + missingRequiredParameters
    }
}

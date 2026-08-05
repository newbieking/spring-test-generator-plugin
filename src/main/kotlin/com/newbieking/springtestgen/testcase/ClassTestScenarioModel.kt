package com.newbieking.springtestgen.testcase

import com.newbieking.springtestgen.model.MethodMetadata

/** A deterministic, PSI-free scenario for a non-Controller class method. */
data class ClassTestScenario(
    val id: String,
    val displayName: String,
    val method: MethodMetadata,
    val methodOrdinal: Int,
    val scenarioType: ClassTestScenarioType,
    val arguments: List<ArgumentValue>,
    val disabledReason: String? = null
)

enum class ClassTestScenarioType {
    HAPPY_PATH,
    NULL_INPUT,
    BOUNDARY_INPUT,
    DEPENDENCY_EXCEPTION,
    EMPTY_RESULT,
    DUPLICATE_RESULT,
    PERSISTENCE_EXCEPTION,
    TRANSACTION_FAILURE
}

data class ArgumentValue(
    val source: String,
    val semantic: ArgumentSemantic
)

enum class ArgumentSemantic {
    NORMAL,
    NULL,
    BOUNDARY
}

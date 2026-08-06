package com.newbieking.springtestgen.prompt

/**
 * Identifies a prompt template by its purpose and target.
 *
 * @param id stable identifier used for lookup and persistence (e.g. "controller.mock-request-body")
 */
data class TemplateId(val id: String) : Comparable<TemplateId> {
    override fun compareTo(other: TemplateId): Int = id.compareTo(other.id)
    override fun toString(): String = id
}

/**
 * A single prompt template with variable placeholders.
 *
 * Variables are expressed as `{{name}}` in the template text.
 * Example: "Generate a JSON body for {{bodyType}} with realistic values."
 */
data class PromptTemplate(
    val id: TemplateId,
    val text: String,
    val description: String = "",
    val variables: Set<String> = extractVariables(text)
) {
    companion object {
        private val VARIABLE_PATTERN = Regex("\\{\\{(\\w+)}}")

        /** Extracts `{{variableName}}` placeholders from template text. */
        fun extractVariables(text: String): Set<String> =
            VARIABLE_PATTERN.findAll(text).map { it.groupValues[1] }.toSet()
    }
}

/**
 * A resolved template where all variables have been substituted.
 * If a variable is missing from the provided values, it is left as-is.
 */
data class ResolvedPrompt(
    val templateId: TemplateId,
    val text: String,
    val unresolvedVariables: Set<String>
)

/**
 * Variable metadata for documentation and UI rendering.
 */
data class PromptVariable(
    val name: String,
    val description: String,
    val required: Boolean = true,
    val exampleValue: String = ""
)

/**
 * Registry of known template variables with documentation.
 */
object PromptVariableDocs {
    private val docs = mutableMapOf<String, PromptVariable>()

    fun register(variable: PromptVariable) {
        docs[variable.name] = variable
    }

    fun get(name: String): PromptVariable? = docs[name]

    fun all(): Collection<PromptVariable> = docs.values

    init {
        register(PromptVariable("bodyType", "Fully qualified Java/Kotlin request body class name", exampleValue = "com.example.UserDto"))
        register(PromptVariable("httpMethod", "HTTP method (GET, POST, PUT, DELETE, PATCH)", exampleValue = "POST"))
        register(PromptVariable("path", "Endpoint URL path", exampleValue = "/api/users"))
        register(PromptVariable("controllerName", "Controller simple class name", exampleValue = "UserController"))
        register(PromptVariable("scenarioType", "Test scenario type", exampleValue = "HAPPY_PATH"))
        register(PromptVariable("className", "Class under test simple name", exampleValue = "UserService"))
        register(PromptVariable("methodName", "Method under test name", exampleValue = "findById"))
        register(PromptVariable("targetType", "Class under test target type", exampleValue = "SERVICE"))
    }
}
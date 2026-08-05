package com.newbieking.springtestgen.psi

/** A PSI-free snapshot of a request DTO and its validation metadata. */
data class RequestBodySchema(
    val typeName: String,
    val qualifiedName: String?,
    val fields: List<RequestFieldSchema>
)

data class RequestFieldSchema(
    val name: String,
    val type: String,
    val constraints: ValidationConstraints = ValidationConstraints(),
    val defaultValue: String? = null
)

data class ValidationConstraints(
    val required: Boolean = false,
    val notBlank: Boolean = false,
    val minLength: Int? = null,
    val maxLength: Int? = null,
    val minimum: String? = null,
    val maximum: String? = null,
    val pattern: String? = null
)

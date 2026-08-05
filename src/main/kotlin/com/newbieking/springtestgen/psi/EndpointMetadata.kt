package com.newbieking.springtestgen.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod

/**
 * Endpoint data extracted from PSI. The string fields are snapshots that remain
 * safe to consume after the read action which produced this metadata has ended.
 */
data class EndpointMetadata(
    val controllerClass: PsiClass,
    val method: PsiMethod,
    val controllerName: String,
    val controllerQualifiedName: String?,
    val methodName: String,
    val httpMethod: HttpMethod,
    val path: String,
    val requestBodyType: String?,
    val requestBodySchema: RequestBodySchema?,
    val requestBodyValidated: Boolean,
    val requestParams: List<RequestParam>,
    val pathVariables: List<PathVariable>
)

enum class HttpMethod { GET, POST, PUT, DELETE, PATCH }

data class RequestParam(val name: String, val type: String, val required: Boolean)
data class PathVariable(val name: String, val type: String)

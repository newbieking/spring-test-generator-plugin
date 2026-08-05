package com.newbieking.springtestgen.psi

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.*

/** Extracts Spring MVC endpoint metadata from Java PSI. */
class SpringEndpointParser(private val psiManager: PsiManager) {

    private val log = Logger.getInstance(SpringEndpointParser::class.java)
    private val dtoSchemaResolver = DtoSchemaResolver(psiManager)

    private companion object {
        val MAPPING_ANNOTATIONS = listOf(
            "org.springframework.web.bind.annotation.RequestMapping",
            "org.springframework.web.bind.annotation.GetMapping",
            "org.springframework.web.bind.annotation.PostMapping",
            "org.springframework.web.bind.annotation.PutMapping",
            "org.springframework.web.bind.annotation.DeleteMapping",
            "org.springframework.web.bind.annotation.PatchMapping"
        )
    }

    fun parseController(psiClass: PsiClass): List<EndpointMetadata> =
        ReadAction.compute<List<EndpointMetadata>, RuntimeException> {
            parseControllerInReadAction(psiClass)
        }

    private fun parseControllerInReadAction(psiClass: PsiClass): List<EndpointMetadata> {
        val controllerName = psiClass.qualifiedName ?: psiClass.name ?: "<anonymous>"
        if (!isController(psiClass)) {
            log.debug("Skipping non-controller class: $controllerName")
            return emptyList()
        }

        val classPaths = getClassRequestMappings(psiClass)
        val endpoints = psiClass.allMethods
            .filter(::isHandlerMethodInReadAction)
            .flatMap { method ->
                val methodPaths = getMethodRequestMappings(method) ?: return@flatMap emptyList()
                val httpMethods = resolveHttpMethods(method)
                if (methodPaths.isEmpty() || httpMethods.isEmpty()) {
                    log.warn("Skipping unsupported mapping on method: $controllerName#${method.name}")
                    return@flatMap emptyList()
                }

                val requestBody = extractRequestBody(method)
                val requestParams = extractRequestParams(method)
                val pathVariables = extractPathVariables(method)
                classPaths.flatMap { classPath ->
                    methodPaths.flatMap { methodPath ->
                        httpMethods.map { httpMethod ->
                            EndpointMetadata(
                                controllerClass = psiClass,
                                method = method,
                                controllerName = psiClass.name ?: "Controller",
                                controllerQualifiedName = psiClass.qualifiedName,
                                methodName = method.name,
                                httpMethod = httpMethod,
                                path = normalizePath("$classPath/$methodPath"),
                                requestBodyType = requestBody?.type?.canonicalText,
                                requestBodySchema = requestBody?.let { dtoSchemaResolver.resolve(it.type) },
                                requestBodyValidated = requestBody?.hasAnnotation("jakarta.validation.Valid") == true ||
                                    requestBody?.hasAnnotation("javax.validation.Valid") == true ||
                                    requestBody?.hasAnnotation("org.springframework.validation.annotation.Validated") == true,
                                requestParams = requestParams,
                                pathVariables = pathVariables
                            )
                        }
                    }
                }
            }

        log.debug("Parsed ${endpoints.size} endpoint(s) from controller: $controllerName")
        return endpoints
    }

    fun isHandlerMethod(method: PsiMethod): Boolean = ReadAction.compute<Boolean, RuntimeException> {
        isHandlerMethodInReadAction(method)
    }

    private fun isHandlerMethodInReadAction(method: PsiMethod): Boolean =
        MAPPING_ANNOTATIONS.any { method.getAnnotation(it) != null }

    private fun isController(clazz: PsiClass): Boolean =
        clazz.getAnnotation("org.springframework.web.bind.annotation.RestController") != null ||
            clazz.getAnnotation("org.springframework.stereotype.Controller") != null

    private fun getClassRequestMappings(clazz: PsiClass): List<String> =
        clazz.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
            ?.let(::extractPaths)
            ?: listOf("")

    /**
     * A mapping annotation without `path` or `value` is valid Spring MVC and maps to
     * the controller base path, so it returns a single empty path rather than null.
     */
    private fun getMethodRequestMappings(method: PsiMethod): List<String>? {
        val annotation = MAPPING_ANNOTATIONS.firstNotNullOfOrNull { method.getAnnotation(it) }
            ?: return null
        return extractPaths(annotation)
    }

    private fun resolveHttpMethods(method: PsiMethod): List<HttpMethod> = when {
        method.hasAnnotation("org.springframework.web.bind.annotation.GetMapping") -> listOf(HttpMethod.GET)
        method.hasAnnotation("org.springframework.web.bind.annotation.PostMapping") -> listOf(HttpMethod.POST)
        method.hasAnnotation("org.springframework.web.bind.annotation.PutMapping") -> listOf(HttpMethod.PUT)
        method.hasAnnotation("org.springframework.web.bind.annotation.DeleteMapping") -> listOf(HttpMethod.DELETE)
        method.hasAnnotation("org.springframework.web.bind.annotation.PatchMapping") -> listOf(HttpMethod.PATCH)
        method.hasAnnotation("org.springframework.web.bind.annotation.RequestMapping") -> {
            val annotation = method.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
            val methodAttribute = annotation?.findDeclaredAttributeValue("method")
            if (methodAttribute == null) {
                log.debug("@RequestMapping without method on ${method.name}; using GET as the generated-test default")
                listOf(HttpMethod.GET)
            } else {
                annotationValues(methodAttribute).mapNotNull(::toHttpMethod)
            }
        }
        else -> emptyList()
    }

    private fun extractPaths(annotation: PsiAnnotation): List<String> {
        // findAttributeValue() returns annotation defaults, which are an empty array ({})
        // for Spring mapping paths. Only source-declared values distinguish an omitted path.
        val pathValue = annotation.findDeclaredAttributeValue("path")
            ?: annotation.findDeclaredAttributeValue("value")
            ?: return listOf("")
        val paths = annotationValues(pathValue).mapNotNull(::resolveStringValue)
        if (paths.isEmpty() && pathValue is PsiArrayInitializerMemberValue && pathValue.initializers.isEmpty()) {
            return listOf("")
        }
        if (paths.isEmpty()) {
            log.warn("Unable to resolve path expression '${pathValue.text}' on ${annotation.qualifiedName}")
        }
        return paths
    }

    private fun annotationValues(value: PsiAnnotationMemberValue): List<PsiAnnotationMemberValue> =
        if (value is PsiArrayInitializerMemberValue) value.initializers.toList() else listOf(value)

    private fun resolveStringValue(value: PsiAnnotationMemberValue?): String? {
        val expression = value as? PsiExpression ?: return null
        return JavaPsiFacade.getInstance(psiManager.project)
            .constantEvaluationHelper
            .computeConstantExpression(expression) as? String
    }

    private fun resolveBooleanValue(value: PsiAnnotationMemberValue?): Boolean? {
        val expression = value as? PsiExpression ?: return null
        return JavaPsiFacade.getInstance(psiManager.project)
            .constantEvaluationHelper
            .computeConstantExpression(expression) as? Boolean
    }

    private fun toHttpMethod(value: PsiAnnotationMemberValue): HttpMethod? = when (
        value.text.substringAfterLast('.').trim()
    ) {
        "GET" -> HttpMethod.GET
        "POST" -> HttpMethod.POST
        "PUT" -> HttpMethod.PUT
        "DELETE" -> HttpMethod.DELETE
        "PATCH" -> HttpMethod.PATCH
        else -> null
    }

    private fun normalizePath(path: String): String = "/" + path.trim('/').replace(Regex("/{2,}"), "/")

    private fun extractRequestBody(method: PsiMethod) =
        method.parameterList.parameters.firstOrNull {
            it.hasAnnotation("org.springframework.web.bind.annotation.RequestBody")
        }

    private fun extractRequestParams(method: PsiMethod): List<RequestParam> =
        method.parameterList.parameters.mapNotNull { parameter ->
            val annotation = parameter.getAnnotation("org.springframework.web.bind.annotation.RequestParam")
                ?: return@mapNotNull null
            val name = annotationName(annotation) ?: parameter.name
            val required = resolveBooleanValue(annotation.findAttributeValue("required")) ?: true
            RequestParam(name, parameter.type.canonicalText, required)
        }

    private fun extractPathVariables(method: PsiMethod): List<PathVariable> =
        method.parameterList.parameters.mapNotNull { parameter ->
            val annotation = parameter.getAnnotation("org.springframework.web.bind.annotation.PathVariable")
                ?: return@mapNotNull null
            val name = annotationName(annotation) ?: parameter.name
            PathVariable(name, parameter.type.canonicalText)
        }

    /**
     * Spring's name/value attributes default to an empty string. Read only values
     * declared in source, then use the Java parameter name when the annotation is blank.
     */
    private fun annotationName(annotation: PsiAnnotation): String? =
        resolveStringValue(
            annotation.findDeclaredAttributeValue("name")
                ?: annotation.findDeclaredAttributeValue("value")
        )?.takeIf { it.isNotBlank() }
}

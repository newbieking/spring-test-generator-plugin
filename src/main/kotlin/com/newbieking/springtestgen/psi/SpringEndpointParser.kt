package com.newbieking.springtestgen.psi

import com.intellij.psi.*

/**
 * PSI 解析器，提取 Spring Web 端点信息
 */
class SpringEndpointParser(private val project: PsiManager) {

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

    /**
     * 扫描当前类中所有符合端点条件的方法
     */
    fun parseController(psiClass: PsiClass): List<EndpointMetadata> {
        if (!isController(psiClass)) return emptyList()
        val classPath = getClassRequestMapping(psiClass)
        return psiClass.allMethods
            .filter { isHandlerMethod(it) }
            .mapNotNull { method ->
                val methodPath = getMethodRequestMapping(method) ?: return@mapNotNull null
                val httpMethod = resolveHttpMethod(method) ?: return@mapNotNull null
                val fullPath = normalizePath(classPath + methodPath)
                val bodyParam = extractRequestBodyType(method)
                val params = extractRequestParams(method)
                val pathVars = extractPathVariables(method)
                EndpointMetadata(
                    controllerClass = psiClass,
                    method = method,
                    httpMethod = httpMethod,
                    path = fullPath,
                    requestBodyType = bodyParam,
                    requestParams = params,
                    pathVariables = pathVars
                )
            }
    }

    /**
     * 判断指定方法是否为请求处理方法（拥有 @RequestMapping 或其快捷注解）
     */
    fun isHandlerMethod(method: PsiMethod): Boolean {
        return MAPPING_ANNOTATIONS.any { method.getAnnotation(it) != null }
    }

    // ---------- 私有辅助方法 ----------

    private fun isController(clazz: PsiClass): Boolean {
        return clazz.hasAnnotation("org.springframework.web.bind.annotation.RestController") ||
                clazz.hasAnnotation("org.springframework.stereotype.Controller")
    }

    private fun getClassRequestMapping(clazz: PsiClass): String {
        val anno = clazz.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
        val value = anno?.findAttributeValue("value") ?: anno?.findAttributeValue("path")
        return extractPathFromAnnotationValue(value) ?: ""
    }

    private fun getMethodRequestMapping(method: PsiMethod): String? {
        for (annoName in MAPPING_ANNOTATIONS) {
            val anno = method.getAnnotation(annoName)
            if (anno != null) {
                val value = anno.findAttributeValue("value") ?: anno.findAttributeValue("path")
                return extractPathFromAnnotationValue(value)
            }
        }
        return null
    }

    private fun resolveHttpMethod(method: PsiMethod): HttpMethod? {
        return when {
            method.hasAnnotation("org.springframework.web.bind.annotation.GetMapping") -> HttpMethod.GET
            method.hasAnnotation("org.springframework.web.bind.annotation.PostMapping") -> HttpMethod.POST
            method.hasAnnotation("org.springframework.web.bind.annotation.PutMapping") -> HttpMethod.PUT
            method.hasAnnotation("org.springframework.web.bind.annotation.DeleteMapping") -> HttpMethod.DELETE
            method.hasAnnotation("org.springframework.web.bind.annotation.PatchMapping") -> HttpMethod.PATCH
            method.hasAnnotation("org.springframework.web.bind.annotation.RequestMapping") -> {
                val anno = method.getAnnotation("org.springframework.web.bind.annotation.RequestMapping")
                val methodAttr = anno?.findAttributeValue("method")
                when {
                    methodAttr is PsiArrayInitializerMemberValue -> {
                        methodAttr.initializers?.firstOrNull()?.text?.let {
                            when (it.trim('"')) {
                                "RequestMethod.GET" -> HttpMethod.GET
                                "RequestMethod.POST" -> HttpMethod.POST
                                "RequestMethod.PUT" -> HttpMethod.PUT
                                "RequestMethod.DELETE" -> HttpMethod.DELETE
                                "RequestMethod.PATCH" -> HttpMethod.PATCH
                                else -> null
                            }
                        }
                    }
                    // method 未指定则默认 GET
                    methodAttr == null -> HttpMethod.GET
                    else -> null
                }
            }
            else -> null
        }
    }

    private fun extractPathFromAnnotationValue(value: PsiAnnotationMemberValue?): String? {
        if (value is PsiLiteralExpression) {
            return value.text?.trim('"')
        } else if (value is PsiArrayInitializerMemberValue) {
            return value.initializers?.firstOrNull()?.text?.trim('"')
        }
        return null
    }

    private fun normalizePath(path: String): String {
        return "/" + path.trim('/')
    }

    private fun extractRequestBodyType(method: PsiMethod): String? {
        for (param in method.parameterList.parameters) {
            if (param.hasAnnotation("org.springframework.web.bind.annotation.RequestBody")) {
                return param.type.canonicalText
            }
        }
        return null
    }

    private fun extractRequestParams(method: PsiMethod): List<RequestParam> {
        val result = mutableListOf<RequestParam>()
        for (param in method.parameterList.parameters) {
            val anno = param.getAnnotation("org.springframework.web.bind.annotation.RequestParam")
            if (anno != null) {
                val name = (anno.findAttributeValue("value") as? PsiLiteralExpression)?.text?.trim('"')
                    ?: param.name ?: "unnamed"
                val required = (anno.findAttributeValue("required") as? PsiLiteralExpression)?.text?.toBoolean() ?: true
                result.add(RequestParam(name, param.type.canonicalText, required))
            }
        }
        return result
    }

    private fun extractPathVariables(method: PsiMethod): List<PathVariable> {
        val result = mutableListOf<PathVariable>()
        for (param in method.parameterList.parameters) {
            val anno = param.getAnnotation("org.springframework.web.bind.annotation.PathVariable")
            if (anno != null) {
                val name = (anno.findAttributeValue("value") as? PsiLiteralExpression)?.text?.trim('"')
                    ?: param.name ?: "unnamed"
                result.add(PathVariable(name, param.type.canonicalText))
            }
        }
        return result
    }

    // 扩展函数：检查方法是否有某个注解
    private fun PsiMethod.hasAnnotation(fqn: String): Boolean {
        return getAnnotation(fqn) != null
    }

    private fun PsiClass.hasAnnotation(fqn: String): Boolean {
        return getAnnotation(fqn) != null
    }

    private fun PsiParameter.hasAnnotation(fqn: String): Boolean {
        return getAnnotation(fqn) != null
    }
}
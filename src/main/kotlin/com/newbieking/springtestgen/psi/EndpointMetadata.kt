package com.newbieking.springtestgen.psi

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiClass

/**
 * 端点元数据，包含生成测试所需全部信息
 */
data class EndpointMetadata(
    val controllerClass: PsiClass,          // 所属 Controller 类
    val method: PsiMethod,                  // 处理方法
    val httpMethod: HttpMethod,            // GET/POST/PUT/DELETE
    val path: String,                       // 完整路径（类级 + 方法级）
    val requestBodyType: String?,           // @RequestBody 的类型全限定名（可能为 null）
    val requestParams: List<RequestParam>,  // @RequestParam 列表
    val pathVariables: List<PathVariable>   // @PathVariable 列表
)

enum class HttpMethod { GET, POST, PUT, DELETE, PATCH }

data class RequestParam(val name: String, val type: String, val required: Boolean)
data class PathVariable(val name: String, val type: String)
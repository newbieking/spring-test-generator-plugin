package com.newbieking.springtestgen.services

import com.newbieking.springtestgen.psi.EndpointMetadata

/**
 * AI 生成服务接口
 */
interface AIGenerationService {
    /**
     * 根据端点元数据生成 Mock 请求体 JSON 样本
     * @param metadata 端点元数据（包含 requestBodyType）
     * @return JSON 字符串
     */
    suspend fun generateMockRequestBody(metadata: EndpointMetadata): String?

    /**
     * 生成断言期望值（可扩展）
     */
    suspend fun generateExpectedResponse(metadata: EndpointMetadata): String?
}
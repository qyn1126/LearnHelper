package com.zhuanjie.learnhelper.data

import java.util.UUID

data class LlmConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val apiBaseUrl: String = "https://dashscope.aliyuncs.com/compatible-mode/v1",
    val apiKey: String = "",
    val apiModel: String = "qwen-plus",
    val maxTokens: Int? = null,
    val temperature: Float? = null,
    val topP: Float? = null
)

data class ChatParams(
    val maxTokens: Int? = null,
    val temperature: Float? = null,
    val topP: Float? = null
) {
    fun mergeOver(base: LlmConfig): ChatParams = ChatParams(
        maxTokens = maxTokens ?: base.maxTokens,
        temperature = temperature ?: base.temperature,
        topP = topP ?: base.topP
    )
}

data class TokenUsage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheTokens: Long = 0
) {
    operator fun plus(other: TokenUsage) = TokenUsage(
        inputTokens + other.inputTokens,
        outputTokens + other.outputTokens,
        cacheTokens + other.cacheTokens
    )
}

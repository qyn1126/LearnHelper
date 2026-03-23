package com.zhuanjie.learnhelper.network

import com.zhuanjie.learnhelper.data.ChatMessage
import com.zhuanjie.learnhelper.data.ChatParams
import com.zhuanjie.learnhelper.data.LlmConfig
import com.zhuanjie.learnhelper.data.TokenUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class QwenApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    @Volatile
    var lastUsage: TokenUsage? = null
        private set

    fun chatStream(
        messages: List<ChatMessage>,
        config: LlmConfig,
        paramOverrides: ChatParams? = null
    ): Flow<String> = flow {
        if (config.apiKey.isBlank()) throw Exception("请先在设置中配置 API Key")

        lastUsage = null
        val params = paramOverrides?.mergeOver(config) ?: ChatParams(
            maxTokens = config.maxTokens,
            temperature = config.temperature,
            topP = config.topP
        )

        val messagesJson = JSONArray()
        messages.forEach { msg ->
            messagesJson.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }

        val body = JSONObject().apply {
            put("model", config.apiModel)
            put("messages", messagesJson)
            put("stream", true)
            put("stream_options", JSONObject().put("include_usage", true))
            put("enable_thinking", false)
            params.maxTokens?.let { put("max_tokens", it) }
            params.temperature?.let { put("temperature", it.toDouble()) }
            params.topP?.let { put("top_p", it.toDouble()) }
        }

        val request = Request.Builder()
            .url("${config.apiBaseUrl.trimEnd('/')}/chat/completions")
            .addHeader("Authorization", "Bearer ${config.apiKey}")
            .addHeader("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val responseBody = response.body?.string() ?: ""
            val errorMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message") ?: responseBody
            } catch (_: Exception) { responseBody }
            throw Exception("API 错误 (${response.code}): $errorMsg")
        }

        val reader = response.body?.byteStream()?.bufferedReader()
            ?: throw Exception("响应为空")

        reader.use { r ->
            var line = r.readLine()
            while (line != null) {
                if (line.startsWith("data: ")) {
                    val data = line.removePrefix("data: ").trim()
                    if (data == "[DONE]") break
                    try {
                        val json = JSONObject(data)
                        // Parse content delta
                        val content = json.optJSONArray("choices")
                            ?.optJSONObject(0)
                            ?.optJSONObject("delta")
                            ?.optString("content", "") ?: ""
                        if (content.isNotEmpty()) {
                            emit(content)
                        }
                        // Parse usage from any chunk that contains it
                        parseUsage(json)?.let { lastUsage = it }
                    } catch (_: Exception) { }
                }
                line = r.readLine()
            }
        }

        // If stream didn't include usage, try a synchronous call to get it
        // (some providers only return usage in non-stream mode)
        if (lastUsage == null) {
            try {
                val usageBody = JSONObject().apply {
                    put("model", config.apiModel)
                    put("messages", messagesJson)
                    put("max_tokens", 1)
                    put("stream", false)
                }
                // Skip the fallback for now — not all providers charge for this
                // Just leave lastUsage as null
            } catch (_: Exception) { }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun chat(
        messages: List<ChatMessage>,
        config: LlmConfig,
        paramOverrides: ChatParams? = null
    ): String {
        val sb = StringBuilder()
        chatStream(messages, config, paramOverrides).collect { sb.append(it) }
        return sb.toString()
    }

    /**
     * Parse usage from a SSE JSON chunk.
     * Handles multiple formats:
     * - OpenAI: usage.prompt_tokens, usage.completion_tokens, usage.prompt_tokens_details.cached_tokens
     * - DashScope: usage.prompt_tokens, usage.completion_tokens, usage.prompt_cache_hit_tokens
     * - Other: usage.input_tokens, usage.output_tokens
     */
    private fun parseUsage(json: JSONObject): TokenUsage? {
        val usage = json.optJSONObject("usage") ?: return null

        val inputTokens = usage.optLong("prompt_tokens", 0)
            .takeIf { it > 0 }
            ?: usage.optLong("input_tokens", 0)

        val outputTokens = usage.optLong("completion_tokens", 0)
            .takeIf { it > 0 }
            ?: usage.optLong("output_tokens", 0)

        val cacheTokens = usage.optJSONObject("prompt_tokens_details")
            ?.optLong("cached_tokens", 0)
            ?: usage.optLong("prompt_cache_hit_tokens", 0)

        // Only return if we got meaningful data
        if (inputTokens == 0L && outputTokens == 0L) return null

        return TokenUsage(
            inputTokens = inputTokens,
            outputTokens = outputTokens,
            cacheTokens = cacheTokens
        )
    }
}

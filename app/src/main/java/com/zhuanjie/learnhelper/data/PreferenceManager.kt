package com.zhuanjie.learnhelper.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class PreferenceManager(context: Context) {
    private val prefs = context.getSharedPreferences("learn_helper", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        const val DEFAULT_CHAT_PROMPT = "你是一位考试辅导老师。请简洁明了地解答学生关于以下题目的疑惑，回答要精炼，避免冗余。"
        const val DEFAULT_ANALYSIS_PROMPT = "你是一位考试辅导老师。请简洁地分析刷题结果，每个要点用1-2句话概括，不要展开过多。"
    }

    // ==================== LLM Configs ====================

    fun getLlmConfigs(): List<LlmConfig> {
        val json = prefs.getString("llm_configs", null) ?: return migrateOldConfig()
        return try {
            val type = object : TypeToken<List<LlmConfig>>() {}.type
            gson.fromJson<List<LlmConfig>>(json, type) ?: emptyList()
        } catch (_: Exception) { emptyList() }
    }

    fun saveLlmConfigs(configs: List<LlmConfig>) {
        prefs.edit().putString("llm_configs", gson.toJson(configs)).apply()
    }

    var activeLlmConfigId: String
        get() = prefs.getString("active_llm_config_id", "") ?: ""
        set(value) = prefs.edit().putString("active_llm_config_id", value).apply()

    fun getActiveLlmConfig(): LlmConfig? {
        val configs = getLlmConfigs()
        return configs.find { it.id == activeLlmConfigId } ?: configs.firstOrNull()
    }

    fun addLlmConfig(config: LlmConfig) {
        val configs = getLlmConfigs() + config
        saveLlmConfigs(configs)
        if (configs.size == 1) activeLlmConfigId = config.id
    }

    fun updateLlmConfig(config: LlmConfig) {
        saveLlmConfigs(getLlmConfigs().map { if (it.id == config.id) config else it })
    }

    fun deleteLlmConfig(id: String) {
        val configs = getLlmConfigs().filter { it.id != id }
        saveLlmConfigs(configs)
        if (activeLlmConfigId == id) {
            activeLlmConfigId = configs.firstOrNull()?.id ?: ""
        }
    }

    /** Migrate old single-config fields to new multi-config */
    private fun migrateOldConfig(): List<LlmConfig> {
        val oldKey = prefs.getString("api_key", "") ?: ""
        val oldModel = prefs.getString("api_model", "") ?: ""
        val oldUrl = prefs.getString("api_base_url", "") ?: ""
        if (oldKey.isBlank() && oldModel.isBlank()) return emptyList()
        val config = LlmConfig(
            name = oldModel.ifBlank { "默认配置" },
            apiBaseUrl = oldUrl.ifBlank { "https://dashscope.aliyuncs.com/compatible-mode/v1" },
            apiKey = oldKey,
            apiModel = oldModel.ifBlank { "qwen-plus" }
        )
        saveLlmConfigs(listOf(config))
        activeLlmConfigId = config.id
        return listOf(config)
    }

    // Keep old accessors for backward compat (delegate to active config)
    var apiKey: String
        get() = getActiveLlmConfig()?.apiKey ?: ""
        set(value) { /* no-op, use LlmConfig */ }
    var apiModel: String
        get() = getActiveLlmConfig()?.apiModel ?: "qwen-plus"
        set(value) { /* no-op */ }
    var apiBaseUrl: String
        get() = getActiveLlmConfig()?.apiBaseUrl ?: ""
        set(value) { /* no-op */ }

    // ==================== Prompts with params ====================

    var chatSystemPrompt: String
        get() = prefs.getString("chat_system_prompt", null) ?: DEFAULT_CHAT_PROMPT
        set(value) = prefs.edit().putString("chat_system_prompt", value).apply()

    var analysisSystemPrompt: String
        get() = prefs.getString("analysis_system_prompt", null) ?: DEFAULT_ANALYSIS_PROMPT
        set(value) = prefs.edit().putString("analysis_system_prompt", value).apply()

    var chatPromptParams: ChatParams
        get() {
            val json = prefs.getString("chat_prompt_params", null) ?: return ChatParams()
            return try { gson.fromJson(json, ChatParams::class.java) } catch (_: Exception) { ChatParams() }
        }
        set(value) = prefs.edit().putString("chat_prompt_params", gson.toJson(value)).apply()

    var analysisPromptParams: ChatParams
        get() {
            val json = prefs.getString("analysis_prompt_params", null) ?: return ChatParams()
            return try { gson.fromJson(json, ChatParams::class.java) } catch (_: Exception) { ChatParams() }
        }
        set(value) = prefs.edit().putString("analysis_prompt_params", gson.toJson(value)).apply()

    fun resetPrompts() {
        prefs.edit()
            .remove("chat_system_prompt")
            .remove("analysis_system_prompt")
            .remove("chat_prompt_params")
            .remove("analysis_prompt_params")
            .apply()
    }

    // ==================== Token Usage ====================

    fun getTokenUsageMap(): Map<String, TokenUsage> {
        val json = prefs.getString("token_usage", null) ?: return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, TokenUsage>>() {}.type
            gson.fromJson(json, type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }
    }

    fun addTokenUsage(config: LlmConfig, usage: TokenUsage) {
        val key = "${config.apiBaseUrl}|${config.apiModel}"
        val map = getTokenUsageMap().toMutableMap()
        map[key] = (map[key] ?: TokenUsage()) + usage
        prefs.edit().putString("token_usage", gson.toJson(map)).apply()
    }

    fun clearTokenUsage() {
        prefs.edit().remove("token_usage").apply()
    }

    // ==================== Quiz / Wrong answers ====================

    var quizProgress: Int
        get() = prefs.getInt("quiz_progress", 0)
        set(value) = prefs.edit().putInt("quiz_progress", value).apply()

    var quizRandomSeed: Long
        get() = prefs.getLong("quiz_random_seed", System.nanoTime())
        set(value) = prefs.edit().putLong("quiz_random_seed", value).apply()

    var quizIsRandomMode: Boolean
        get() = prefs.getBoolean("quiz_is_random", true)
        set(value) = prefs.edit().putBoolean("quiz_is_random", value).apply()

    var quizIsReciteMode: Boolean
        get() = prefs.getBoolean("quiz_is_recite", false)
        set(value) = prefs.edit().putBoolean("quiz_is_recite", value).apply()

    fun getWrongAnswerIds(): Set<String> =
        prefs.getStringSet("wrong_answers", emptySet()) ?: emptySet()

    fun addWrongAnswer(id: String) {
        val set = getWrongAnswerIds().toMutableSet()
        set.add(id)
        prefs.edit().putStringSet("wrong_answers", set).apply()
    }

    fun removeWrongAnswer(id: String) {
        val set = getWrongAnswerIds().toMutableSet()
        set.remove(id)
        prefs.edit().putStringSet("wrong_answers", set).apply()
    }

    fun clearWrongAnswers() {
        prefs.edit().remove("wrong_answers").apply()
    }

    fun getCustomExplanation(questionId: String): String? =
        prefs.getString("explanation_$questionId", null)

    fun setCustomExplanation(questionId: String, explanation: String) {
        prefs.edit().putString("explanation_$questionId", explanation).apply()
    }
}

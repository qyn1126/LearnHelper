package com.zhuanjie.learnhelper.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhuanjie.learnhelper.data.ChatParams
import com.zhuanjie.learnhelper.data.LlmConfig
import com.zhuanjie.learnhelper.data.PreferenceManager
import com.zhuanjie.learnhelper.ui.theme.LearnHelperTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefManager: PreferenceManager,
    totalQuestions: Int,
    onOpenBankManager: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var showResetDialog by remember { mutableStateOf(false) }
    var showClearWrongDialog by remember { mutableStateOf(false) }
    var showClearTokenDialog by remember { mutableStateOf(false) }

    // LLM config state
    var showConfigDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<LlmConfig?>(null) }
    var templatePrefill by remember { mutableStateOf<ProviderTemplate?>(null) }
    var showDeleteConfigDialog by remember { mutableStateOf<LlmConfig?>(null) }

    val configs = remember(refreshTrigger) { prefManager.getLlmConfigs() }
    val activeConfigId = remember(refreshTrigger) { prefManager.activeLlmConfigId }
    val tokenUsage = remember(refreshTrigger) { prefManager.getTokenUsageMap() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ==================== LLM Configs ====================
            SectionTitle("大模型配置")

            OutlinedButton(
                onClick = { editingConfig = null; templatePrefill = null; showConfigDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("手动添加配置")
            }

            // First-launch: show templates when no configs exist
            if (configs.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "或选择一个模板快速开始",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                providerTemplates.forEach { template ->
                    Card(
                        onClick = {
                            editingConfig = null
                            templatePrefill = template
                            showConfigDialog = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(template.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                template.models.joinToString(", "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            configs.forEach { config ->
                val isActive = config.id == activeConfigId
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isActive,
                            onClick = {
                                prefManager.activeLlmConfigId = config.id
                                refreshTrigger++
                            }
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(config.name.ifBlank { config.apiModel }, fontWeight = FontWeight.Bold)
                            Text(
                                "${config.apiModel} | ${config.apiBaseUrl.take(30)}...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                            // Show params if set
                            val paramParts = listOfNotNull(
                                config.maxTokens?.let { "max=$it" },
                                config.temperature?.let { "temp=$it" },
                                config.topP?.let { "topP=$it" }
                            )
                            if (paramParts.isNotEmpty()) {
                                Text(
                                    paramParts.joinToString(" "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                        }
                        IconButton(onClick = { editingConfig = config; showConfigDialog = true }) {
                            Icon(Icons.Default.Edit, "编辑", tint = MaterialTheme.colorScheme.primary)
                        }
                        if (configs.size > 1) {
                            IconButton(onClick = { showDeleteConfigDialog = config }) {
                                Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            SectionDivider()

            // ==================== Prompts ====================
            SectionTitle("提示词管理")

            var chatPrompt by rememberSaveable { mutableStateOf(prefManager.chatSystemPrompt) }
            var analysisPrompt by rememberSaveable { mutableStateOf(prefManager.analysisSystemPrompt) }
            var chatParams by remember { mutableStateOf(prefManager.chatPromptParams) }
            var analysisParams by remember { mutableStateOf(prefManager.analysisPromptParams) }
            var showChatParams by remember { mutableStateOf(false) }
            var showAnalysisParams by remember { mutableStateOf(false) }

            // Chat prompt
            OutlinedTextField(
                value = chatPrompt, onValueChange = { chatPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("答疑提示词") }, minLines = 2, maxLines = 5
            )
            Text("题目信息会自动附加在提示词之后", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            CollapsibleParamsEditor(
                label = "答疑参数覆盖",
                expanded = showChatParams,
                onToggle = { showChatParams = !showChatParams },
                params = chatParams,
                onParamsChange = { chatParams = it }
            )

            Spacer(Modifier.height(12.dp))

            // Analysis prompt
            OutlinedTextField(
                value = analysisPrompt, onValueChange = { analysisPrompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("刷题分析提示词") }, minLines = 2, maxLines = 5
            )
            Text("刷题结果数据会自动附加在提示词之后", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            CollapsibleParamsEditor(
                label = "分析参数覆盖",
                expanded = showAnalysisParams,
                onToggle = { showAnalysisParams = !showAnalysisParams },
                params = analysisParams,
                onParamsChange = { analysisParams = it }
            )

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        prefManager.chatSystemPrompt = chatPrompt.trim()
                        prefManager.analysisSystemPrompt = analysisPrompt.trim()
                        prefManager.chatPromptParams = chatParams
                        prefManager.analysisPromptParams = analysisParams
                        scope.launch { snackbarHostState.showSnackbar("提示词已保存") }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("保存提示词") }
                OutlinedButton(
                    onClick = {
                        prefManager.resetPrompts()
                        chatPrompt = PreferenceManager.DEFAULT_CHAT_PROMPT
                        analysisPrompt = PreferenceManager.DEFAULT_ANALYSIS_PROMPT
                        chatParams = ChatParams()
                        analysisParams = ChatParams()
                        showChatParams = false
                        showAnalysisParams = false
                        scope.launch { snackbarHostState.showSnackbar("已恢复默认") }
                    },
                    modifier = Modifier.weight(1f)
                ) { Text("恢复默认") }
            }

            SectionDivider()

            // ==================== Token Usage ====================
            SectionTitle("Token 消耗统计")

            if (tokenUsage.isEmpty()) {
                Text("暂无消耗记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                tokenUsage.forEach { (key, usage) ->
                    val parts = key.split("|", limit = 2)
                    val url = parts.getOrElse(0) { "" }.takeLast(25)
                    val model = parts.getOrElse(1) { "" }
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("$model", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("...$url", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("输入: ${usage.inputTokens}", style = MaterialTheme.typography.bodySmall)
                                Text("输出: ${usage.outputTokens}", style = MaterialTheme.typography.bodySmall)
                                Text("缓存: ${usage.cacheTokens}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearTokenDialog = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("清空统计") }
            }

            SectionDivider()

            // ==================== Bank Management ====================
            SectionTitle("题库管理")
            Button(onClick = onOpenBankManager, modifier = Modifier.fillMaxWidth()) {
                Text("管理题库 (导入 / 切换 / 导出示例)")
            }

            SectionDivider()

            // ==================== Data Management ====================
            SectionTitle("数据管理")

            Text("刷题进度: ${(prefManager.quizProgress + 1).coerceAtMost(totalQuestions)} / $totalQuestions", style = MaterialTheme.typography.bodyMedium)
            Text("错题数量: ${prefManager.getWrongAnswerIds().size}", style = MaterialTheme.typography.bodyMedium)

            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { showResetDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("重置刷题进度") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showClearWrongDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("清空错题本") }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ==================== Dialogs ====================

    // LLM Config editor dialog
    if (showConfigDialog) {
        LlmConfigDialog(
            config = editingConfig,
            templatePrefill = if (editingConfig == null) templatePrefill else null,
            onSave = { config ->
                if (editingConfig != null) prefManager.updateLlmConfig(config)
                else prefManager.addLlmConfig(config)
                refreshTrigger++
                showConfigDialog = false
                scope.launch { snackbarHostState.showSnackbar("配置已保存") }
            },
            onDismiss = { showConfigDialog = false }
        )
    }

    showDeleteConfigDialog?.let { config ->
        AlertDialog(
            onDismissRequest = { showDeleteConfigDialog = null },
            title = { Text("删除配置") },
            text = { Text("确定删除\"${config.name.ifBlank { config.apiModel }}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    prefManager.deleteLlmConfig(config.id)
                    refreshTrigger++
                    showDeleteConfigDialog = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfigDialog = null }) { Text("取消") } }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("确认重置") },
            text = { Text("确定要重置刷题进度吗?") },
            confirmButton = { TextButton(onClick = { prefManager.quizProgress = 0; showResetDialog = false }) { Text("确定") } },
            dismissButton = { TextButton(onClick = { showResetDialog = false }) { Text("取消") } }
        )
    }

    if (showClearWrongDialog) {
        AlertDialog(
            onDismissRequest = { showClearWrongDialog = false },
            title = { Text("确认清空") },
            text = { Text("确定要清空错题本吗?") },
            confirmButton = { TextButton(onClick = { prefManager.clearWrongAnswers(); showClearWrongDialog = false }) { Text("确定", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showClearWrongDialog = false }) { Text("取消") } }
        )
    }

    if (showClearTokenDialog) {
        AlertDialog(
            onDismissRequest = { showClearTokenDialog = false },
            title = { Text("清空统计") },
            text = { Text("确定清空所有 Token 消耗记录?") },
            confirmButton = {
                TextButton(onClick = {
                    prefManager.clearTokenUsage()
                    refreshTrigger++
                    showClearTokenDialog = false
                    scope.launch { snackbarHostState.showSnackbar("统计已清空") }
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showClearTokenDialog = false }) { Text("取消") } }
        )
    }
}

// ==================== Reusable components ====================

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(24.dp))
    HorizontalDivider()
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun CollapsibleParamsEditor(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    params: ChatParams,
    onParamsChange: (ChatParams) -> Unit
) {
    Text(
        if (expanded) "$label (收起)" else "$label (展开)",
        modifier = Modifier.clickable { onToggle() }.padding(vertical = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
    if (expanded) {
        Text("留空则使用大模型配置的默认值，此处设置优先级更高", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = params.maxTokens?.toString() ?: "",
                onValueChange = { onParamsChange(params.copy(maxTokens = it.toIntOrNull())) },
                label = { Text("max_tokens") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = params.temperature?.toString() ?: "",
                onValueChange = { onParamsChange(params.copy(temperature = it.toFloatOrNull())) },
                label = { Text("temperature") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = params.topP?.toString() ?: "",
                onValueChange = { onParamsChange(params.copy(topP = it.toFloatOrNull())) },
                label = { Text("top_p") }, singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private data class ProviderTemplate(
    val name: String,
    val apiBaseUrl: String,
    val models: List<String>
)

private val providerTemplates = listOf(
    ProviderTemplate("OpenAI", "https://api.openai.com/v1", listOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo", "o1", "o1-mini", "o3-mini")),
    ProviderTemplate("Claude", "https://api.anthropic.com/v1", listOf("claude-sonnet-4-5-20250514", "claude-haiku-4-5-20251001", "claude-opus-4-0-20250514")),
    ProviderTemplate("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai", listOf("gemini-2.5-pro-preview-06-05", "gemini-2.5-flash-preview-05-20", "gemini-2.0-flash")),
    ProviderTemplate("阿里云百炼", "https://dashscope.aliyuncs.com/compatible-mode/v1", listOf("qwen3.5-plus", "qwen-plus", "qwen-turbo", "qwen-max", "qwen-long", "qwen3-235b-a22b")),
    ProviderTemplate("腾讯云混元", "https://api.hunyuan.cloud.tencent.com/v1", listOf("hunyuan-pro", "hunyuan-standard", "hunyuan-lite", "hunyuan-turbo")),
    ProviderTemplate("DeepSeek", "https://api.deepseek.com/v1", listOf("deepseek-chat", "deepseek-reasoner")),
    ProviderTemplate("xAI Grok", "https://api.x.ai/v1", listOf("grok-3", "grok-3-mini", "grok-2")),
    ProviderTemplate("硅基流动", "https://api.siliconflow.cn/v1", listOf("Qwen/Qwen3-235B-A22B", "deepseek-ai/DeepSeek-V3", "Pro/deepseek-ai/DeepSeek-R1")),
    ProviderTemplate("零一万物", "https://api.lingyiwanwu.com/v1", listOf("yi-large", "yi-medium", "yi-spark")),
    ProviderTemplate("Moonshot", "https://api.moonshot.cn/v1", listOf("moonshot-v1-auto", "moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")),
    ProviderTemplate("智谱 AI", "https://open.bigmodel.cn/api/paas/v4", listOf("glm-4-plus", "glm-4-flash", "glm-4-air")),
)

@Composable
private fun LlmConfigDialog(
    config: LlmConfig?,
    templatePrefill: ProviderTemplate? = null,
    onSave: (LlmConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val isNew = config == null
    var name by rememberSaveable { mutableStateOf(config?.name ?: templatePrefill?.name ?: "") }
    var url by rememberSaveable { mutableStateOf(config?.apiBaseUrl ?: templatePrefill?.apiBaseUrl ?: "https://dashscope.aliyuncs.com/compatible-mode/v1") }
    var key by rememberSaveable { mutableStateOf(config?.apiKey ?: "") }
    var model by rememberSaveable { mutableStateOf(config?.apiModel ?: templatePrefill?.models?.firstOrNull() ?: "qwen-plus") }
    var showKey by remember { mutableStateOf(false) }
    var showParams by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var maxTokens by rememberSaveable { mutableStateOf(config?.maxTokens?.toString() ?: "") }
    var temperature by rememberSaveable { mutableStateOf(config?.temperature?.toString() ?: "") }
    var topP by rememberSaveable { mutableStateOf(config?.topP?.toString() ?: "") }

    // Track selected provider for model suggestions
    val matchedProvider = providerTemplates.find { url.trimEnd('/').startsWith(it.apiBaseUrl.trimEnd('/')) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "添加配置" else "编辑配置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Provider templates
                Text(
                    if (showTemplates) "选择模板 (收起)" else "选择模板 (展开)",
                    modifier = Modifier.clickable { showTemplates = !showTemplates }.padding(vertical = 2.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (showTemplates) {
                    providerTemplates.forEach { template ->
                        Card(
                            onClick = {
                                name = template.name
                                url = template.apiBaseUrl
                                model = template.models.first()
                                showTemplates = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (matchedProvider == template) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(template.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    template.models.joinToString(", "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("配置名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("API 地址") }, minLines = 1, maxLines = 3, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    value = key, onValueChange = { key = it },
                    label = { Text("API Key") }, singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "隐藏" else "显示") } },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("模型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                // Model suggestions from matched provider
                if (matchedProvider != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        matchedProvider.models.take(4).forEach { m ->
                            TextButton(
                                onClick = { model = m },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                            ) {
                                Text(
                                    m.split("/").last().take(16),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    fontWeight = if (m == model) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    if (matchedProvider.models.size > 4) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            matchedProvider.models.drop(4).forEach { m ->
                                TextButton(
                                    onClick = { model = m },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) {
                                    Text(
                                        m.split("/").last().take(16),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        fontWeight = if (m == model) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    if (showParams) "默认参数 (收起)" else "默认参数 (展开)",
                    modifier = Modifier.clickable { showParams = !showParams }.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (showParams) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(value = maxTokens, onValueChange = { maxTokens = it }, label = { Text("max") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = temperature, onValueChange = { temperature = it }, label = { Text("temp") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = topP, onValueChange = { topP = it }, label = { Text("top_p") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        LlmConfig(
                            id = config?.id ?: LlmConfig().id,
                            name = name.trim().ifBlank { model.trim() },
                            apiBaseUrl = url.trim(),
                            apiKey = key.trim(),
                            apiModel = model.trim(),
                            maxTokens = maxTokens.toIntOrNull(),
                            temperature = temperature.toFloatOrNull(),
                            topP = topP.toFloatOrNull()
                        )
                    )
                },
                enabled = key.isNotBlank() && model.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    LearnHelperTheme {
        SettingsScreen(prefManager = PreferenceManager(LocalContext.current), totalQuestions = 520)
    }
}

package com.zhuanjie.learnhelper.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhuanjie.learnhelper.data.ChatMessage
import com.zhuanjie.learnhelper.data.ChatStorage
import com.zhuanjie.learnhelper.data.PreferenceManager
import com.zhuanjie.learnhelper.data.Question
import com.zhuanjie.learnhelper.network.QwenApi
import com.zhuanjie.learnhelper.ui.theme.LearnHelperTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    question: Question,
    qwenApi: QwenApi,
    chatStorage: ChatStorage,
    prefManager: PreferenceManager,
    onBack: () -> Unit
) {
    var messages by remember { mutableStateOf(chatStorage.getMessages(question.id)) }
    var inputText by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var streamingContent by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Track if user is near the bottom for auto-scroll
    val isNearBottom by remember {
        derivedStateOf {
            val max = scrollState.maxValue
            max == 0 || scrollState.value >= max - 200
        }
    }

    val systemMessage = ChatMessage(
        role = "system",
        content = buildString {
            append(prefManager.chatSystemPrompt)
            append("\n\n")
            append("题目: ${question.question}\n")
            append("选项:\n")
            question.options.entries.sortedBy { it.key }.forEach { (k, v) ->
                append("$k. $v\n")
            }
            append("正确答案: ${question.answer}\n")
            if (!question.explanationText.isNullOrBlank()) {
                append("参考解析: ${question.explanationText}")
            }
        }
    )

    BackHandler { onBack() }

    fun sendMessage() {
        val text = inputText.trim()
        if (text.isBlank() || isLoading) return

        val userMsg = ChatMessage("user", text)
        val newMessages = messages + userMsg
        messages = newMessages
        chatStorage.saveMessages(question.id, newMessages)
        inputText = ""
        isLoading = true
        streamingContent = ""
        errorMessage = null

        scope.launch {
            try {
                val config = prefManager.getActiveLlmConfig() ?: throw Exception("请先配置大模型")
                val params = prefManager.chatPromptParams
                val apiMessages = listOf(systemMessage) + newMessages
                val sb = StringBuilder()
                qwenApi.chatStream(apiMessages, config, params).collect { delta ->
                    sb.append(delta)
                    streamingContent = sb.toString()
                }
                qwenApi.lastUsage?.let { prefManager.addTokenUsage(config, it) }
                val assistantMsg = ChatMessage("assistant", sb.toString())
                messages = messages + assistantMsg
                chatStorage.saveMessages(question.id, messages)
                streamingContent = ""
            } catch (e: Exception) {
                errorMessage = e.message ?: "未知错误"
                streamingContent = ""
            } finally {
                isLoading = false
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("AI 答疑", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 3.dp,
                modifier = Modifier.imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("输入你的问题...") },
                        maxLines = 3,
                        shape = MaterialTheme.shapes.large
                    )
                    Spacer(Modifier.width(8.dp))
                    IconButton(
                        onClick = { sendMessage() },
                        enabled = inputText.isNotBlank() && !isLoading
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "发送",
                            tint = if (inputText.isNotBlank() && !isLoading)
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Question info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(question.title, style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        question.question,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "正确答案: ${question.answer}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            // Quick actions
            if (!isLoading) {
                val quickActions = if (messages.isEmpty()) listOf(
                    "这道题为什么选${question.answer}?",
                    "请通俗易懂地分析这道题",
                    "请简单明了地解释每个选项",
                    "请简单易懂地复述原始解析",
                    "这个知识点还有哪些考法?"
                ) else listOf(
                    "请通俗易懂地重新解释",
                    "请简单明了地总结要点",
                    "能再详细一些吗?",
                    "请简单易懂地复述原始解析"
                )
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    quickActions.forEach { action ->
                        AssistChip(
                            onClick = { inputText = action },
                            label = { Text(action, style = MaterialTheme.typography.labelMedium) }
                        )
                    }
                }
            }

            // Committed messages (rendered as Markdown)
            messages.forEach { message ->
                ChatBubble(
                    message = message,
                    isStreaming = false,
                    onSetAsExplanation = if (message.role == "assistant") {
                        {
                            prefManager.setCustomExplanation(question.id, message.content)
                            scope.launch {
                                snackbarHostState.showSnackbar("已设为本题 AI 解析")
                            }
                        }
                    } else null
                )
            }

            // Streaming response (plain text for performance)
            if (isLoading && streamingContent.isNotEmpty()) {
                ChatBubble(
                    message = ChatMessage("assistant", streamingContent),
                    isStreaming = true,
                    onSetAsExplanation = null
                )
            }

            // Loading indicator (before first token)
            if (isLoading && streamingContent.isEmpty()) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("AI 正在思考...", style = MaterialTheme.typography.bodySmall)
                }
            }

            // Error
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        errorMessage!!,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }

    // Auto-scroll to bottom
    LaunchedEffect(messages.size) {
        scrollState.scrollTo(scrollState.maxValue)
    }

    LaunchedEffect(streamingContent) {
        if (isLoading && isNearBottom) {
            scrollState.scrollTo(scrollState.maxValue)
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    isStreaming: Boolean,
    onSetAsExplanation: (() -> Unit)?
) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Text(
            if (isUser) "你" else "AI",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (isUser) {
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else if (isStreaming) {
                    StreamingMarkdownText(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    MarkdownText(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (onSetAsExplanation != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = onSetAsExplanation,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text("设为本题解析", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun AiChatScreenPreview() {
    val context = LocalContext.current
    LearnHelperTheme {
        AiChatScreen(
            question = sampleQuestions[0],
            qwenApi = QwenApi(),
            chatStorage = ChatStorage(context),
            prefManager = PreferenceManager(context),
            onBack = {}
        )
    }
}

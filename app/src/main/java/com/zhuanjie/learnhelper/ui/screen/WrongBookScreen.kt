package com.zhuanjie.learnhelper.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhuanjie.learnhelper.data.ChatMessage
import com.zhuanjie.learnhelper.data.ChatStorage
import com.zhuanjie.learnhelper.data.PreferenceManager
import com.zhuanjie.learnhelper.data.Question
import com.zhuanjie.learnhelper.data.QuizSummaryItem
import com.zhuanjie.learnhelper.data.SummaryManager
import com.zhuanjie.learnhelper.data.WrongDetail
import com.zhuanjie.learnhelper.network.QwenApi
import com.zhuanjie.learnhelper.ui.theme.LearnHelperTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    allQuestions: List<Question>,
    prefManager: PreferenceManager,
    chatStorage: ChatStorage,
    summaryManager: SummaryManager,
    qwenApi: QwenApi,
    isActive: Boolean = true,
    onOpenAiChat: (Question) -> Unit,
    onStartReview: (List<Question>) -> Unit,
    onEditQuestion: ((Question) -> Unit)? = null,
    onDeleteQuestion: ((Question) -> Unit)? = null
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Auto-refresh when tab becomes active (picks up changes from other screens)
    LaunchedEffect(isActive) {
        if (isActive) refreshTrigger++
    }

    val wrongIds = remember(refreshTrigger) { prefManager.getWrongAnswerIds() }
    val wrongQuestions = remember(refreshTrigger, allQuestions) {
        allQuestions.filter { it.id in wrongIds }
    }
    val chatQuestionIds = remember(refreshTrigger) { chatStorage.getAllChatQuestionIds() }
    val chatQuestions = remember(chatQuestionIds, allQuestions) {
        chatQuestionIds.mapNotNull { qId -> allQuestions.find { it.id == qId } }
    }
    val summaries = remember(refreshTrigger) { summaryManager.getAllSummaries() }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(title = { Text("总结") })
                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        text = { Text("错题 (${wrongQuestions.size})") })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        text = { Text("AI 问答 (${chatQuestions.size})") })
                    Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                        text = { Text("刷题记录 (${summaries.size})") })
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 && wrongQuestions.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = { onStartReview(wrongQuestions) },
                    icon = { Icon(Icons.Default.Refresh, null) },
                    text = { Text("复习错题") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> WrongQuestionsTab(wrongQuestions, prefManager, onOpenAiChat, onEditQuestion, onDeleteQuestion,
                onRemoveWrong = { prefManager.removeWrongAnswer(it); refreshTrigger++ },
                modifier = Modifier.padding(padding))
            1 -> AiChatHistoryTab(chatQuestions, chatStorage, onOpenAiChat,
                onDeleteChat = { chatStorage.deleteMessages(it); refreshTrigger++ },
                modifier = Modifier.padding(padding))
            2 -> QuizSummariesTab(summaries, summaryManager, qwenApi, prefManager,
                onDelete = { summaryManager.deleteSummary(it); refreshTrigger++ },
                onUpdated = { refreshTrigger++ },
                modifier = Modifier.padding(padding))
        }
    }
}

// ==================== Tab 0: Wrong Questions ====================

@Composable
private fun WrongQuestionsTab(
    wrongQuestions: List<Question>,
    prefManager: PreferenceManager,
    onOpenAiChat: (Question) -> Unit,
    onEditQuestion: ((Question) -> Unit)?,
    onDeleteQuestion: ((Question) -> Unit)?,
    onRemoveWrong: (String) -> Unit,
    modifier: Modifier
) {
    if (wrongQuestions.isEmpty()) {
        EmptyState("暂无错题", "答错的题目会自动收录到这里", modifier)
    } else {
        LazyColumn(modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(wrongQuestions, key = { it.dbId }) { question ->
                var expanded by rememberSaveable(question.dbId) { mutableStateOf(false) }
                Card(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(question.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(question.question, maxLines = if (expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        if (expanded) {
                            Spacer(Modifier.height(12.dp))
                            question.options.entries.sortedBy { it.key }.forEach { (k, v) ->
                                val isCorrect = k in question.correctAnswerSet
                                Text("$k. $v", color = if (isCorrect) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("正确答案: ${question.answer}", fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                            if (!question.explanationText.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                Text("解析: ${question.explanationText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val custom = prefManager.getCustomExplanation(question.id)
                            if (custom != null) {
                                Spacer(Modifier.height(8.dp))
                                Text("AI 解析:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                MarkdownText(text = custom, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                if (onEditQuestion != null) TextButton(onClick = { onEditQuestion(question) }) { Text("编辑") }
                                if (onDeleteQuestion != null) TextButton(onClick = { onDeleteQuestion(question) }) { Text("删除") }
                                TextButton(onClick = { onOpenAiChat(question) }) { Text("问 AI") }
                                TextButton(onClick = { onRemoveWrong(question.id) }) { Text("移除", color = MaterialTheme.colorScheme.error) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== Tab 1: AI Chat History ====================

@Composable
private fun AiChatHistoryTab(
    chatQuestions: List<Question>,
    chatStorage: ChatStorage,
    onOpenAiChat: (Question) -> Unit,
    onDeleteChat: (String) -> Unit,
    modifier: Modifier
) {
    if (chatQuestions.isEmpty()) {
        EmptyState("暂无问答记录", "刷题时点击\"问 AI\"开始答疑", modifier)
    } else {
        LazyColumn(modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chatQuestions, key = { "chat_${it.dbId}" }) { question ->
                val lastMsg = remember(question.id) { chatStorage.getLastMessage(question.id) }
                val msgCount = remember(question.id) { chatStorage.getMessageCount(question.id) }
                Card(onClick = { onOpenAiChat(question) }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(question.title.ifBlank { "题目" }, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(question.question, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (lastMsg != null) {
                                Spacer(Modifier.height(4.dp))
                                Text("${if (lastMsg.role == "user") "你" else "AI"}: ${lastMsg.content.take(60).replace('\n', ' ')}", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Text("$msgCount 条对话", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteChat(question.id) }) {
                            Icon(Icons.Default.Delete, "删除记录", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ==================== Tab 2: Quiz Summaries ====================

@Composable
private fun QuizSummariesTab(
    summaries: List<QuizSummaryItem>,
    summaryManager: SummaryManager,
    qwenApi: QwenApi,
    prefManager: PreferenceManager,
    onDelete: (Long) -> Unit,
    onUpdated: () -> Unit,
    modifier: Modifier
) {
    // Track which summary is open in detail view
    var detailSummaryId by rememberSaveable { mutableStateOf<Long?>(null) }

    if (detailSummaryId != null) {
        val summary = summaries.find { it.id == detailSummaryId }
        if (summary != null) {
            SummaryDetailView(
                summary = summary,
                summaryManager = summaryManager,
                qwenApi = qwenApi,
                prefManager = prefManager,
                onBack = { detailSummaryId = null; onUpdated() },
                onDelete = { onDelete(summary.id); detailSummaryId = null }
            )
            return
        } else {
            detailSummaryId = null
        }
    }

    if (summaries.isEmpty()) {
        EmptyState("暂无刷题记录", "完成一次刷题后记录会自动保存", modifier)
    } else {
        LazyColumn(modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(summaries, key = { it.id }) { summary ->
                val dateStr = remember(summary.timestamp) {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(summary.timestamp))
                }
                val accuracyColor = when {
                    summary.accuracy >= 0.8f -> Color(0xFF4CAF50)
                    summary.accuracy >= 0.6f -> Color(0xFFFF9800)
                    else -> Color(0xFFF44336)
                }

                Card(onClick = { detailSummaryId = summary.id }, modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dateStr, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text("共 ${summary.totalCount} 题  对 ${summary.correctCount}  错 ${summary.wrongCount}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (summary.aiAnalysis != null) {
                                Text("已生成 AI 分析", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Text("${"%.0f".format(summary.accuracy * 100)}%",
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = accuracyColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryDetailView(
    summary: QuizSummaryItem,
    summaryManager: SummaryManager,
    qwenApi: QwenApi,
    prefManager: PreferenceManager,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var aiAnalysis by remember { mutableStateOf(summary.aiAnalysis) }
    var streamingAnalysis by remember { mutableStateOf("") }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzeError by remember { mutableStateOf<String?>(null) }

    val dateStr = remember(summary.timestamp) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(summary.timestamp))
    }
    val accuracyColor = when {
        summary.accuracy >= 0.8f -> Color(0xFF4CAF50)
        summary.accuracy >= 0.6f -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    fun generateAiAnalysis() {
        if (isAnalyzing) return
        isAnalyzing = true
        analyzeError = null
        streamingAnalysis = ""

        val prompt = buildString {
            append("我刚完成了一次刷题练习，请帮我分析结果。\n\n")
            append("总计: ${summary.totalCount} 题\n")
            append("正确: ${summary.correctCount} 题\n")
            append("错误: ${summary.wrongCount} 题\n")
            append("正确率: ${"%.1f".format(summary.accuracy * 100)}%\n\n")
            if (summary.wrongDetails.isNotEmpty()) {
                append("错题:\n")
                summary.wrongDetails.forEachIndexed { i, d ->
                    append("${i + 1}. ${d.question} (选${d.userAnswer} 答案${d.correctAnswer})\n")
                }
            }
            append("\n请从总体评价、薄弱知识点、错误原因、学习建议四个方面分析。")
        }

        scope.launch {
            try {
                val config = prefManager.getActiveLlmConfig() ?: throw Exception("请先配置大模型")
                val params = prefManager.analysisPromptParams
                val messages = listOf(
                    ChatMessage("system", prefManager.analysisSystemPrompt),
                    ChatMessage("user", prompt)
                )
                val sb = StringBuilder()
                qwenApi.chatStream(messages, config, params).collect { delta ->
                    sb.append(delta)
                    streamingAnalysis = sb.toString()
                }
                qwenApi.lastUsage?.let { prefManager.addTokenUsage(config, it) }
                aiAnalysis = sb.toString()
                summaryManager.updateAiAnalysis(summary.id, sb.toString())
                streamingAnalysis = ""
            } catch (e: Exception) {
                analyzeError = e.message ?: "分析失败"
                streamingAnalysis = ""
            } finally {
                isAnalyzing = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Back button
        TextButton(onClick = onBack) { Text("< 返回列表") }

        Spacer(Modifier.height(8.dp))

        // Stats card
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(dateStr, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("${"%.1f".format(summary.accuracy * 100)}%",
                    style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, color = accuracyColor)
                Text("正确率", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${summary.totalCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("总计", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${summary.correctCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                        Text("正确", style = MaterialTheme.typography.bodySmall)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${summary.wrongCount}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                        Text("错误", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        // Wrong details
        if (summary.wrongDetails.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("错题摘要", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            summary.wrongDetails.forEach { detail ->
                Text("  ${detail.question} (选${detail.userAnswer} 答案${detail.correctAnswer})",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(16.dp))

        // AI Analysis section
        val displayAnalysis = aiAnalysis ?: streamingAnalysis.ifEmpty { null }

        if (displayAnalysis != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("AI 分析", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (isAnalyzing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (isAnalyzing) {
                StreamingMarkdownText(text = displayAnalysis, style = MaterialTheme.typography.bodyMedium)
            } else {
                MarkdownText(text = displayAnalysis, style = MaterialTheme.typography.bodyMedium)
            }
            if (!isAnalyzing) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { generateAiAnalysis() }, modifier = Modifier.fillMaxWidth()) {
                    Text("重新生成 AI 分析")
                }
            }
        } else if (isAnalyzing) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("AI 正在分析...", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            Button(onClick = { generateAiAnalysis() }, modifier = Modifier.fillMaxWidth()) {
                Text("生成 AI 分析")
            }
        }

        if (analyzeError != null) {
            Spacer(Modifier.height(8.dp))
            Text(analyzeError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { generateAiAnalysis() }) { Text("重试") }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Text("删除此记录")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String, modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SummaryScreenPreview() {
    val ctx = LocalContext.current
    LearnHelperTheme {
        SummaryScreen(
            allQuestions = sampleQuestions,
            prefManager = PreferenceManager(ctx),
            chatStorage = ChatStorage(ctx),
            summaryManager = SummaryManager(ctx),
            qwenApi = QwenApi(),
            onOpenAiChat = {},
            onStartReview = {}
        )
    }
}

package com.zhuanjie.learnhelper.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.zhuanjie.learnhelper.data.AnswerRecord
import com.zhuanjie.learnhelper.data.ChatMessage
import com.zhuanjie.learnhelper.data.PreferenceManager
import com.zhuanjie.learnhelper.data.QuizResult
import com.zhuanjie.learnhelper.data.SummaryManager
import com.zhuanjie.learnhelper.network.QwenApi
import com.zhuanjie.learnhelper.ui.theme.LearnHelperTheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizResultScreen(
    result: QuizResult,
    qwenApi: QwenApi,
    prefManager: PreferenceManager,
    summaryManager: SummaryManager,
    onBack: () -> Unit,
    onOpenAiChat: (com.zhuanjie.learnhelper.data.Question) -> Unit
) {
    var aiAnalysis by rememberSaveable { mutableStateOf<String?>(null) }
    var streamingAnalysis by remember { mutableStateOf("") }
    var summaryId by remember { mutableStateOf<Long?>(null) }

    // Save summary on first composition
    LaunchedEffect(Unit) {
        if (summaryId == null) {
            summaryId = summaryManager.saveSummary(result)
        }
    }
    var isAnalyzing by remember { mutableStateOf(false) }
    var analyzeError by remember { mutableStateOf<String?>(null) }
    var showCorrectList by rememberSaveable { mutableStateOf(false) }
    var showWrongList by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    fun analyzeWithAi() {
        if (isAnalyzing) return
        isAnalyzing = true
        analyzeError = null
        streamingAnalysis = ""

        val prompt = buildString {
            append("刷题结果: ${result.totalCount}题 对${result.correctCount} 错${result.wrongCount} 正确率${"%.0f".format(result.accuracy * 100)}%\n\n")

            if (result.wrongRecords.isNotEmpty()) {
                append("错题:\n")
                result.wrongRecords.forEachIndexed { index, record ->
                    append("${index + 1}. ${record.question.question}\n")
                    record.question.options.entries.sortedBy { it.key }.forEach { (k, v) ->
                        append("  $k. $v\n")
                    }
                    append("  我选: ${record.userAnswer}  正确: ${record.question.answer}\n")
                    if (!record.question.explanationText.isNullOrBlank()) {
                        append("  解析: ${record.question.explanationText}\n")
                    }
                    append("\n")
                }
            }

            append("请简洁分析:\n1. 一句话总评\n2. 每道错题涉及的知识点(一句话)\n3. 针对性复习建议(2-3条)")
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
                summaryId?.let { summaryManager.updateAiAnalysis(it, sb.toString()) }
                streamingAnalysis = ""
            } catch (e: Exception) {
                analyzeError = e.message ?: "分析失败"
                streamingAnalysis = ""
            } finally {
                isAnalyzing = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("刷题结果") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats card
            item(key = "stats") {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "本次刷题结果",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(16.dp))

                        // Accuracy
                        Text(
                            "${"%.1f".format(result.accuracy * 100)}%",
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                result.accuracy >= 0.8f -> Color(0xFF4CAF50)
                                result.accuracy >= 0.6f -> Color(0xFFFF9800)
                                else -> Color(0xFFF44336)
                            }
                        )
                        Text(
                            "正确率",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            StatItem("总计", result.totalCount, MaterialTheme.colorScheme.onPrimaryContainer)
                            StatItem("正确", result.correctCount, Color(0xFF4CAF50))
                            StatItem("错误", result.wrongCount, Color(0xFFF44336))
                        }
                    }
                }
            }

            // AI Analysis button
            item(key = "ai_button") {
                if (aiAnalysis == null && !isAnalyzing) {
                    Button(
                        onClick = { analyzeWithAi() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("AI 智能分析本次刷题")
                    }
                }
            }

            // AI Analysis: streaming / loading / error / result
            val displayAnalysis = aiAnalysis ?: streamingAnalysis.ifEmpty { null }
            if (displayAnalysis != null) {
                item(key = "ai_result") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "AI 分析报告",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isAnalyzing) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (isAnalyzing) {
                                StreamingMarkdownText(
                                    text = displayAnalysis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                MarkdownText(
                                    text = displayAnalysis,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            } else if (isAnalyzing) {
                item(key = "ai_loading") {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("AI 正在分析...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // AI Analysis error
            if (analyzeError != null) {
                item(key = "ai_error") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(analyzeError!!, color = MaterialTheme.colorScheme.onErrorContainer)
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { analyzeWithAi() }) {
                                Text("重试")
                            }
                        }
                    }
                }
            }

            // Wrong questions section
            if (result.wrongRecords.isNotEmpty()) {
                item(key = "wrong_header") {
                    SectionHeader(
                        title = "错题列表 (${result.wrongCount})",
                        expanded = showWrongList,
                        onToggle = { showWrongList = !showWrongList },
                        color = Color(0xFFF44336)
                    )
                }

                if (showWrongList) {
                    items(result.wrongRecords, key = { "wrong_${it.question.dbId}" }) { record ->
                        AnswerRecordCard(
                            record = record,
                            onOpenAiChat = { onOpenAiChat(record.question) }
                        )
                    }
                }
            }

            // Correct questions section
            if (result.correctRecords.isNotEmpty()) {
                item(key = "correct_header") {
                    SectionHeader(
                        title = "正确题目 (${result.correctCount})",
                        expanded = showCorrectList,
                        onToggle = { showCorrectList = !showCorrectList },
                        color = Color(0xFF4CAF50)
                    )
                }

                if (showCorrectList) {
                    items(result.correctRecords, key = { "correct_${it.question.dbId}" }) { record ->
                        AnswerRecordCard(
                            record = record,
                            onOpenAiChat = { onOpenAiChat(record.question) }
                        )
                    }
                }
            }

            item(key = "bottom_spacer") {
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, count: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "$count",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    color: Color
) {
    Card(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                if (expanded) "收起" else "展开",
                style = MaterialTheme.typography.labelMedium,
                color = color
            )
        }
    }
}

@Composable
private fun AnswerRecordCard(
    record: AnswerRecord,
    onOpenAiChat: () -> Unit
) {
    var expanded by rememberSaveable(record.question.dbId) { mutableStateOf(false) }

    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (record.isCorrect) "O" else "X",
                    fontWeight = FontWeight.Bold,
                    color = if (record.isCorrect) Color(0xFF4CAF50) else Color(0xFFF44336),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(24.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        record.question.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        record.question.question,
                        maxLines = if (expanded) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            if (expanded) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))

                record.question.options.entries.sortedBy { it.key }.forEach { (k, v) ->
                    val isCorrectAnswer = k in record.question.correctAnswerSet
                    val isUserAnswer = k in record.userAnswer.map { it.toString() }.toSet()
                    val textColor = when {
                        isCorrectAnswer -> Color(0xFF4CAF50)
                        isUserAnswer && !record.isCorrect -> Color(0xFFF44336)
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    val suffix = buildString {
                        if (isUserAnswer) append(" [你的答案]")
                        if (isCorrectAnswer) append(" [正确答案]")
                    }
                    Text(
                        "$k. $v$suffix",
                        color = textColor,
                        fontWeight = if (isCorrectAnswer || isUserAnswer) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                if (!record.question.explanationText.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "解析: ${record.question.explanationText}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    OutlinedButton(onClick = onOpenAiChat, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                        Text("问 AI", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun QuizResultScreenPreview() {
    val context = LocalContext.current
    LearnHelperTheme {
        QuizResultScreen(
            result = sampleResult,
            qwenApi = QwenApi(),
            prefManager = PreferenceManager(context),
            summaryManager = SummaryManager(context),
            onBack = {},
            onOpenAiChat = {}
        )
    }
}

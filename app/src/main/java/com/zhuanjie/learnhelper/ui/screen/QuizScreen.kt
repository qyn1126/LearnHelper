package com.zhuanjie.learnhelper.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.zhuanjie.learnhelper.data.AnswerRecord
import com.zhuanjie.learnhelper.data.BankManager
import com.zhuanjie.learnhelper.data.PreferenceManager
import com.zhuanjie.learnhelper.data.Question
import com.zhuanjie.learnhelper.data.QuestionBank
import com.zhuanjie.learnhelper.data.QuizResult
import com.zhuanjie.learnhelper.data.db.AppDatabase
import com.zhuanjie.learnhelper.data.SnowflakeId
import com.zhuanjie.learnhelper.data.db.WrongAnswerEntity
import com.zhuanjie.learnhelper.ui.theme.LearnHelperTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    questions: List<Question>,
    prefManager: PreferenceManager,
    isReviewMode: Boolean = false,
    onOpenAiChat: (Question) -> Unit,
    onBack: (() -> Unit)? = null,
    onFinish: ((QuizResult) -> Unit)? = null,
    onEditQuestion: ((Question) -> Unit)? = null,
    onDeleteQuestion: ((Question) -> Unit)? = null,
    banks: List<QuestionBank> = emptyList(),
    activeBankId: String = "",
    onSwitchBank: ((String) -> Unit)? = null
) {
    if (questions.isEmpty()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (isReviewMode) "错题复习" else "刷题") },
                    navigationIcon = {
                        if (isReviewMode && onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                            }
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    if (isReviewMode) "错题本为空，继续加油!" else "暂无题目",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val context = LocalContext.current
    val db = remember { AppDatabase.getInstance(context) }
    val wrongAnswerDao = remember { db.wrongAnswerDao() }
    val customExplanationDao = remember { db.customExplanationDao() }

    var isRandomMode by rememberSaveable { mutableStateOf(if (isReviewMode) false else prefManager.quizIsRandomMode) }
    var isReciteMode by rememberSaveable { mutableStateOf(if (isReviewMode) false else prefManager.quizIsReciteMode) }
    var randomSeed by rememberSaveable { mutableStateOf(prefManager.quizRandomSeed) }
    var questionOrder by remember(randomSeed, isRandomMode, questions.size) {
        mutableStateOf(
            if (isRandomMode) questions.indices.toList().shuffled(kotlin.random.Random(randomSeed))
            else questions.indices.toList()
        )
    }
    var currentIndex by rememberSaveable {
        mutableIntStateOf(
            if (isReviewMode) 0
            else prefManager.quizProgress.coerceIn(0, questions.size - 1)
        )
    }
    var showFinishDialog by remember { mutableStateOf(false) }
    // Pending mode switch: "random" or "sequential", confirmed after dialog
    var pendingSwitch by remember { mutableStateOf<String?>(null) }

    // Unified answer state: supports both single and multi choice
    var selectedOptions by remember { mutableStateOf<Set<String>>(emptySet()) }
    var hasSubmitted by remember { mutableStateOf(false) }

    // Session answer tracking: questionDbId -> userAnswer string
    var sessionAnswers by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }

    val safeIndex = currentIndex.coerceIn(0, questions.size - 1)
    val actualIndex = questionOrder.getOrElse(safeIndex) { 0 }.coerceIn(0, questions.size - 1)
    val question = questions[actualIndex]

    // Read from Room DB instead of SharedPreferences
    var customExplanation by remember { mutableStateOf<String?>(null) }
    var isInWrongBook by remember { mutableStateOf(false) }

    LaunchedEffect(question.dbId) {
        customExplanation = customExplanationDao.findByQuestionId(question.dbId)?.content
        isInWrongBook = wrongAnswerDao.exists(question.dbId)
    }

    // Restore state when navigating to a previously answered question
    LaunchedEffect(question.dbId) {
        val prev = sessionAnswers[question.dbId]
        if (prev != null) {
            selectedOptions = prev.map { it.toString() }.toSet()
            hasSubmitted = true
        } else {
            selectedOptions = emptySet()
            hasSubmitted = false
        }
    }

    LaunchedEffect(safeIndex) {
        if (!isReviewMode) {
            prefManager.quizProgress = safeIndex
        }
    }

    fun userAnswerStr(): String = selectedOptions.sorted().joinToString("")

    fun isCorrectAnswer(): Boolean = userAnswerStr() == question.correctAnswerSet.joinToString("")

    fun submitAnswer() {
        if (hasSubmitted || selectedOptions.isEmpty()) return
        hasSubmitted = true
        val ansStr = userAnswerStr()
        sessionAnswers = sessionAnswers + (question.dbId to ansStr)
        if (!isCorrectAnswer()) {
            if (!wrongAnswerDao.exists(question.dbId)) {
                wrongAnswerDao.insert(WrongAnswerEntity(id = SnowflakeId.next(), questionId = question.dbId))
            }
            isInWrongBook = true
        } else if (isReviewMode) {
            wrongAnswerDao.deleteByQuestionId(question.dbId)
            isInWrongBook = false
        }
    }

    fun goToQuestion(newIndex: Int) {
        currentIndex = newIndex
        // State will be restored in LaunchedEffect
    }

    fun buildResult(): QuizResult {
        val records = sessionAnswers.mapNotNull { (qDbId, userAnswer) ->
            val q = questions.find { it.dbId == qDbId } ?: return@mapNotNull null
            AnswerRecord(q, userAnswer, userAnswer == q.correctAnswerSet.joinToString(""))
        }
        return QuizResult(records)
    }

    fun doSwitchToRandom() {
        isRandomMode = true
        prefManager.quizIsRandomMode = true
        val newSeed = System.nanoTime()
        randomSeed = newSeed
        prefManager.quizRandomSeed = newSeed
        currentIndex = 0
        prefManager.quizProgress = 0
        sessionAnswers = emptyMap()
    }

    fun doSwitchToSequential() {
        isRandomMode = false
        prefManager.quizIsRandomMode = false
        currentIndex = 0
        prefManager.quizProgress = 0
        sessionAnswers = emptyMap()
    }

    fun trySwitch(target: String) {
        if (sessionAnswers.isNotEmpty()) {
            pendingSwitch = target
        } else {
            if (target == "random") doSwitchToRandom() else doSwitchToSequential()
        }
    }

    var showBankDropdown by remember { mutableStateOf(false) }

    // Resolve display name for active bank
    val activeBankName = when {
        isReviewMode -> "错题复习"
        activeBankId == BankManager.MIXED_ID -> "混合模式"
        else -> banks.find { it.id == activeBankId }?.name ?: "刷题"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!isReviewMode && banks.size > 1 && onSwitchBank != null) {
                        // Clickable dropdown title
                        Row(
                            modifier = Modifier.clickable { showBankDropdown = true },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(activeBankName, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "切换题库")
                            DropdownMenu(
                                expanded = showBankDropdown,
                                onDismissRequest = { showBankDropdown = false }
                            ) {
                                banks.forEach { bank ->
                                    DropdownMenuItem(
                                        text = { Text(bank.name) },
                                        onClick = {
                                            showBankDropdown = false
                                            onSwitchBank(bank.id)
                                        },
                                        trailingIcon = {
                                            if (bank.id == activeBankId) Text("*", fontWeight = FontWeight.Bold)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("混合模式") },
                                    onClick = {
                                        showBankDropdown = false
                                        onSwitchBank(BankManager.MIXED_ID)
                                    },
                                    trailingIcon = {
                                        if (activeBankId == BankManager.MIXED_ID) Text("*", fontWeight = FontWeight.Bold)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(activeBankName)
                    }
                },
                navigationIcon = {
                    if (isReviewMode && onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                        }
                    }
                },
                actions = {
                    if (!isReviewMode) {
                        // Order dropdown: 随机/顺序
                        var showOrderMenu by remember { mutableStateOf(false) }
                        TextButton(onClick = { showOrderMenu = true }) {
                            Text(if (isRandomMode) "随机" else "顺序", style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                            DropdownMenu(expanded = showOrderMenu, onDismissRequest = { showOrderMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("随机") },
                                    onClick = {
                                        showOrderMenu = false
                                        if (!isRandomMode) trySwitch("random")
                                    },
                                    trailingIcon = { if (isRandomMode) Text("*", fontWeight = FontWeight.Bold) }
                                )
                                DropdownMenuItem(
                                    text = { Text("顺序") },
                                    onClick = {
                                        showOrderMenu = false
                                        if (isRandomMode) trySwitch("sequential")
                                    },
                                    trailingIcon = { if (!isRandomMode) Text("*", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }

                        // Mode dropdown: 刷题/背诵
                        var showModeMenu by remember { mutableStateOf(false) }
                        TextButton(onClick = { showModeMenu = true }) {
                            Text(if (isReciteMode) "背诵" else "刷题", style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.size(18.dp))
                            DropdownMenu(expanded = showModeMenu, onDismissRequest = { showModeMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("刷题") },
                                    onClick = { showModeMenu = false; isReciteMode = false; prefManager.quizIsReciteMode = false },
                                    trailingIcon = { if (!isReciteMode) Text("*", fontWeight = FontWeight.Bold) }
                                )
                                DropdownMenuItem(
                                    text = { Text("背诵") },
                                    onClick = { showModeMenu = false; isReciteMode = true; prefManager.quizIsReciteMode = true },
                                    trailingIcon = { if (isReciteMode) Text("*", fontWeight = FontWeight.Bold) }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Progress + session stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "第 ${safeIndex + 1} / ${questions.size} 题",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (sessionAnswers.isNotEmpty()) {
                    val correct = sessionAnswers.count { (qDbId, ans) ->
                        questions.find { it.dbId == qDbId }?.correctAnswerSet?.joinToString("") == ans
                    }
                    Text(
                        "已答 ${sessionAnswers.size} 对 $correct 错 ${sessionAnswers.size - correct}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isInWrongBook) {
                Text("已加入错题本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            LinearProgressIndicator(
                progress = { (safeIndex + 1).toFloat() / questions.size },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )

            // Question title + type badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    question.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (question.isMultiChoice) {
                    Text(
                        "多选",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .then(Modifier)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(question.question, style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(16.dp))

            // Options
            val showAnswer = hasSubmitted || isReciteMode
            val sortedOptions = question.options.entries.sortedBy { it.key }
            sortedOptions.forEach { (key, value) ->
                val isCorrectOption = key in question.correctAnswerSet
                val isSelected = key in selectedOptions

                val containerColor = when {
                    isReciteMode && isCorrectOption -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                    isReciteMode -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    !hasSubmitted && isSelected -> MaterialTheme.colorScheme.primaryContainer
                    !hasSubmitted -> MaterialTheme.colorScheme.surfaceVariant
                    isCorrectOption -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                    isSelected -> Color(0xFFF44336).copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }

                val borderColor = when {
                    isReciteMode && isCorrectOption -> Color(0xFF4CAF50)
                    isReciteMode -> MaterialTheme.colorScheme.outlineVariant
                    !hasSubmitted && isSelected -> MaterialTheme.colorScheme.primary
                    hasSubmitted && isCorrectOption -> Color(0xFF4CAF50)
                    hasSubmitted && isSelected && !isCorrectOption -> Color(0xFFF44336)
                    else -> MaterialTheme.colorScheme.outlineVariant
                }

                OutlinedCard(
                    onClick = {
                        if (!isReciteMode && !hasSubmitted) {
                            if (question.isMultiChoice) {
                                selectedOptions = if (isSelected) selectedOptions - key else selectedOptions + key
                            } else {
                                selectedOptions = setOf(key)
                                submitAnswer()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
                    border = BorderStroke(
                        if ((showAnswer && isCorrectOption) || (!isReciteMode && hasSubmitted && isSelected) || (!hasSubmitted && !isReciteMode && isSelected)) 2.dp else 1.dp,
                        borderColor
                    )
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text("$key.", fontWeight = FontWeight.Bold, modifier = Modifier.width(28.dp))
                        Text(value, modifier = Modifier.weight(1f))
                        if (showAnswer && isCorrectOption) {
                            Text(" OK", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold)
                        } else if (hasSubmitted && isSelected && !isCorrectOption) {
                            Text(" X", color = Color(0xFFF44336), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Multi-choice submit button
            if (!isReciteMode && question.isMultiChoice && !hasSubmitted && selectedOptions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { submitAnswer() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("确认答案 (已选 ${selectedOptions.sorted().joinToString("")})")
                }
            }

            // Navigation
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { goToQuestion(safeIndex - 1) }, enabled = safeIndex > 0) {
                    Text("上一题")
                }
                Button(onClick = { goToQuestion(safeIndex + 1) }, enabled = safeIndex < questions.size - 1) {
                    Text("下一题")
                }
            }

            // Result & Explanation
            if (showAnswer) {
                Spacer(Modifier.height(16.dp))

                if (isReciteMode) {
                    val correctStr = question.correctAnswerSet.joinToString("")
                    Text(
                        "答案: $correctStr",
                        color = Color(0xFF4CAF50),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    val correct = isCorrectAnswer()
                    val correctStr = question.correctAnswerSet.joinToString("")
                    Text(
                        if (correct) "回答正确!" else "回答错误，正确答案是 $correctStr",
                        color = if (correct) Color(0xFF4CAF50) else Color(0xFFF44336),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    if (isReviewMode && correct) {
                        Text("已从错题本移除", style = MaterialTheme.typography.bodySmall, color = Color(0xFF4CAF50))
                    }
                }

                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))

                if (customExplanation != null) {
                    Text("AI 解析", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    MarkdownText(text = customExplanation!!, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                }

                if (!question.explanationText.isNullOrBlank()) {
                    Text(
                        if (customExplanation != null) "原始解析" else "解析",
                        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(question.explanationText, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { onOpenAiChat(question) }, modifier = Modifier.fillMaxWidth()) {
                    Text("问 AI 答疑解惑")
                }

            }

            if (sessionAnswers.isNotEmpty() && onFinish != null) {
                Spacer(Modifier.height(16.dp))
                OutlinedButton(onClick = { showFinishDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("结束刷题，查看结果")
                }
            }

            // Edit / Delete — collapsed at bottom
            if (onEditQuestion != null || onDeleteQuestion != null) {
                var showManage by remember { mutableStateOf(false) }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showManage = !showManage }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        if (showManage) "收起管理" else "管理题目",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (showManage) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onEditQuestion != null) {
                            OutlinedButton(onClick = { onEditQuestion(question) }, modifier = Modifier.weight(1f)) {
                                Text("编辑题目")
                            }
                        }
                        if (onDeleteQuestion != null) {
                            OutlinedButton(onClick = { onDeleteQuestion(question) }, modifier = Modifier.weight(1f)) {
                                Text("删除题目")
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    if (showFinishDialog) {
        val count = sessionAnswers.size
        val correct = sessionAnswers.count { (qDbId, ans) ->
            questions.find { it.dbId == qDbId }?.correctAnswerSet?.joinToString("") == ans
        }
        AlertDialog(
            onDismissRequest = { showFinishDialog = false },
            title = { Text("结束刷题") },
            text = { Text("本次共作答 $count 题，正确 $correct 题，错误 ${count - correct} 题。\n\n确定结束并查看结果?") },
            confirmButton = {
                TextButton(onClick = { showFinishDialog = false; onFinish?.invoke(buildResult()) }) { Text("查看结果") }
            },
            dismissButton = {
                TextButton(onClick = { showFinishDialog = false }) { Text("继续刷题") }
            }
        )
    }

    // Switch mode confirmation when there's progress
    if (pendingSwitch != null) {
        val count = sessionAnswers.size
        val correct = sessionAnswers.count { (qDbId, ans) ->
            questions.find { it.dbId == qDbId }?.correctAnswerSet?.joinToString("") == ans
        }
        val wrong = count - correct
        val targetName = if (pendingSwitch == "random") "随机" else "顺序"
        val accuracy = if (count > 0) "%.0f".format(correct.toFloat() / count * 100) else "0"
        AlertDialog(
            onDismissRequest = { pendingSwitch = null },
            title = { Text("切换为${targetName}模式") },
            text = {
                Column {
                    Text("切换模式将重置进度，如何处理本次刷题?", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("已答", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$correct", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50))
                                Text("正确", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$wrong", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                                Text("错误", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$accuracy%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                Text("正确率", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                    Button(onClick = {
                        val result = buildResult()
                        val switch = pendingSwitch
                        pendingSwitch = null
                        onFinish?.invoke(result)
                        if (switch == "random") doSwitchToRandom() else doSwitchToSequential()
                    }, modifier = Modifier.fillMaxWidth()) { Text("结束并生成总结") }
                    Spacer(Modifier.height(4.dp))
                    OutlinedButton(onClick = {
                        val switch = pendingSwitch
                        pendingSwitch = null
                        if (switch == "random") doSwitchToRandom() else doSwitchToSequential()
                    }, modifier = Modifier.fillMaxWidth()) { Text("放弃本次记录") }
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = { pendingSwitch = null }, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                }
            },
            dismissButton = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun QuizScreenPreview() {
    LearnHelperTheme {
        QuizScreen(
            questions = sampleQuestions,
            prefManager = PreferenceManager(LocalContext.current),
            onOpenAiChat = {},
            onFinish = {}
        )
    }
}

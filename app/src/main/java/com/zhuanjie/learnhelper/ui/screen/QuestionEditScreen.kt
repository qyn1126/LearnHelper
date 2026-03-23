package com.zhuanjie.learnhelper.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.zhuanjie.learnhelper.data.Question
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionEditScreen(
    question: Question?,  // null = new question
    onSave: (Question) -> Unit,
    onBack: () -> Unit
) {
    val isNew = question == null
    var tag by remember { mutableStateOf(question?.displayTag ?: "") }
    var number by remember { mutableStateOf(question?.number?.toString() ?: "") }
    var questionText by remember { mutableStateOf(question?.question ?: "") }
    var type by remember { mutableStateOf(question?.type ?: "single") }
    var answer by remember { mutableStateOf(question?.answer ?: "") }
    var explanation by remember { mutableStateOf(question?.explanationText ?: "") }
    var options by remember {
        mutableStateOf(
            question?.options?.entries?.sortedBy { it.key }?.map { it.key to it.value }
                ?: listOf("A" to "", "B" to "", "C" to "", "D" to "")
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    BackHandler { onBack() }

    fun validate(): String? {
        if (number.isNotBlank() && number.toIntOrNull() == null) return "题号必须为数字或留空"
        if (questionText.isBlank()) return "题目内容不能为空"
        if (options.size < 2) return "至少需要 2 个选项"
        if (options.any { it.second.isBlank() }) return "选项内容不能为空"
        if (answer.isBlank()) return "答案不能为空"
        val validKeys = options.map { it.first }.toSet()
        val answerChars = answer.uppercase().map { it.toString() }
        if (answerChars.any { it !in validKeys }) return "答案包含无效选项: ${answerChars.filter { it !in validKeys }}"
        if (type == "single" && answerChars.size > 1) return "单选题只能有一个答案"
        if (type == "multi" && answerChars.size < 2) return "多选题至少选择 2 个答案"
        return null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建题目" else "编辑题目") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Basic info
            Text("基本信息", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = tag, onValueChange = { tag = it },
                    label = { Text("标签") }, singleLine = true,
                    placeholder = { Text("如: 操作系统、网络安全") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = number, onValueChange = { number = it },
                    label = { Text("题号") }, singleLine = true,
                    modifier = Modifier.weight(0.4f)
                )
            }

            Spacer(Modifier.height(12.dp))

            // Type
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("题型: ", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = type == "single",
                    onClick = { type = "single" },
                    label = { Text("单选") },
                    modifier = Modifier.padding(end = 8.dp)
                )
                FilterChip(
                    selected = type == "multi",
                    onClick = { type = "multi" },
                    label = { Text("多选") }
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Question text
            Text("题目内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = questionText, onValueChange = { questionText = it },
                label = { Text("题目") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3, maxLines = 8
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("选项", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (options.size < 8) {
                    IconButton(onClick = {
                        val nextKey = ('A' + options.size).toString()
                        if (nextKey[0] <= 'H') {
                            options = options + (nextKey to "")
                        }
                    }) {
                        Icon(Icons.Default.Add, "添加选项")
                    }
                }
            }

            options.forEachIndexed { index, (key, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "$key.",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    OutlinedTextField(
                        value = value,
                        onValueChange = { newVal ->
                            options = options.toMutableList().also { it[index] = key to newVal }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        placeholder = { Text("选项 $key 内容") }
                    )
                    if (options.size > 2) {
                        IconButton(onClick = {
                            options = options.toMutableList().also { it.removeAt(index) }
                                .mapIndexed { i, (_, v) -> ('A' + i).toString() to v }
                        }) {
                            Icon(Icons.Default.Delete, "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Answer
            OutlinedTextField(
                value = answer,
                onValueChange = { answer = it.uppercase() },
                label = { Text(if (type == "multi") "答案 (如 BC)" else "答案 (如 B)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))

            // Explanation
            Text("解析", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = explanation, onValueChange = { explanation = it },
                label = { Text("解析 (可选)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2, maxLines = 6
            )

            Spacer(Modifier.height(24.dp))

            // Save button
            Button(
                onClick = {
                    val error = validate()
                    if (error != null) {
                        scope.launch { snackbarHostState.showSnackbar(error) }
                        return@Button
                    }
                    val q = Question(
                        tag = tag.trim(),
                        number = number.trim().toIntOrNull() ?: 0,
                        question = questionText.trim(),
                        options = options.associate { it.first to it.second.trim() },
                        answer = answer.trim().uppercase(),
                        explanation = explanation.trim().ifBlank { null },
                        type = type
                    )
                    onSave(q)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存")
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

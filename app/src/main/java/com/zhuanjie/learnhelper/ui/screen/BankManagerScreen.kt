package com.zhuanjie.learnhelper.ui.screen

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import com.zhuanjie.learnhelper.data.BankManager
import com.zhuanjie.learnhelper.data.QuestionBank
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BankManagerScreen(
    bankManager: BankManager,
    onBack: () -> Unit,
    onBankChanged: (String) -> Unit,
    onOpenBankDetail: (QuestionBank) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var refreshTrigger by remember { mutableIntStateOf(0) }
    val banks = remember(refreshTrigger) { bankManager.getBanks() }
    var activeBankId by remember { mutableStateOf(bankManager.activeBankId) }

    var showImportNameDialog by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var importName by remember { mutableStateOf("") }

    var showDeleteDialog by remember { mutableStateOf<QuestionBank?>(null) }
    var exportingBank by remember { mutableStateOf<QuestionBank?>(null) }

    BackHandler { onBack() }

    // File picker for import
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            importName = ""
            showImportNameDialog = true
        }
    }

    // File creator for sample export
    val sampleExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(bankManager.getSampleJson())
                    }
                    snackbarHostState.showSnackbar("示例题库已导出")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("导出失败: ${e.message}")
                }
            }
        }
    }

    // File creator for bank export
    val bankExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null && exportingBank != null) {
            scope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                        it.write(bankManager.exportBankJson(exportingBank!!.id))
                    }
                    snackbarHostState.showSnackbar("\"${exportingBank!!.name}\" 已导出")
                } catch (e: Exception) {
                    snackbarHostState.showSnackbar("导出失败: ${e.message}")
                } finally {
                    exportingBank = null
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("题库管理") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "header") {
                Text(
                    "选择当前题库",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }

            items(banks, key = { it.id }) { bank ->
                val isActive = bank.id == activeBankId
                Card(
                    onClick = {
                        activeBankId = bank.id
                        bankManager.activeBankId = bank.id
                        onBankChanged(bank.id)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isActive) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isActive,
                            onClick = {
                                activeBankId = bank.id
                                bankManager.activeBankId = bank.id
                                onBankChanged(bank.id)
                            }
                        )
                        Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                            Text(bank.name, fontWeight = FontWeight.Bold)
                            Row {
                                Text(
                                    "${bank.questionCount} 题",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (bank.hasMultiChoice) {
                                    Text(
                                        " | 含多选",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                                if (bank.isBuiltin) {
                                    Text(
                                        " | 内置",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        IconButton(onClick = {
                            exportingBank = bank
                            bankExportLauncher.launch("${bank.name}.json")
                        }) {
                            Icon(Icons.Default.Share, contentDescription = "导出", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onOpenBankDetail(bank) }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "进入",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (!bank.isBuiltin) {
                            IconButton(onClick = { showDeleteDialog = bank }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "删除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            item(key = "actions") {
                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导入题库")
                }

                Spacer(Modifier.height(8.dp))

                OutlinedButton(
                    onClick = { sampleExportLauncher.launch("题库示例.json") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("导出示例题库")
                }

                Spacer(Modifier.height(16.dp))

                // Format description
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("题库 JSON 格式说明", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "JSON 数组，每个元素包含:\n" +
                                    "- tag: 标签/分类 (如\"计算机网络\")\n" +
                                    "- number: 题号\n" +
                                    "- question: 题目内容\n" +
                                    "- options: 选项 {\"A\":\"...\", \"B\":\"...\"}\n" +
                                    "- answer: 答案 (单选\"C\"，多选\"BC\")\n" +
                                    "- type: \"single\" 或 \"multi\" (可选，默认单选)\n" +
                                    "- explanation: 解析 (可选)\n\n" +
                                    "点击\"导出示例题库\"可获取完整示例文件。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Import name dialog
    if (showImportNameDialog && pendingImportUri != null) {
        AlertDialog(
            onDismissRequest = { showImportNameDialog = false },
            title = { Text("导入题库") },
            text = {
                Column {
                    Text("请为题库命名:")
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importName,
                        onValueChange = { importName = it },
                        placeholder = { Text("题库名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = importName.trim()
                        val uri = pendingImportUri!!
                        showImportNameDialog = false
                        scope.launch {
                            try {
                                val bank = bankManager.importBank(
                                    name.ifBlank { "导入题库" },
                                    uri
                                )
                                refreshTrigger++
                                snackbarHostState.showSnackbar("导入成功: ${bank.name} (${bank.questionCount} 题)")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar("导入失败: ${e.message}")
                            }
                        }
                    }
                ) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImportNameDialog = false }) { Text("取消") }
            }
        )
    }

    // Delete confirmation
    showDeleteDialog?.let { bank ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除题库") },
            text = { Text("确定删除\"${bank.name}\"? 此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    bankManager.deleteBank(bank.id)
                    if (activeBankId == bank.id) {
                        activeBankId = BankManager.BUILTIN_ID
                        onBankChanged(BankManager.BUILTIN_ID)
                    }
                    refreshTrigger++
                    showDeleteDialog = null
                    scope.launch { snackbarHostState.showSnackbar("已删除") }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }
}

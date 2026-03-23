package com.zhuanjie.learnhelper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import com.zhuanjie.learnhelper.data.BankManager
import com.zhuanjie.learnhelper.data.ChatStorage
import com.zhuanjie.learnhelper.data.PreferenceManager
import com.zhuanjie.learnhelper.data.Question
import com.zhuanjie.learnhelper.data.QuestionBank
import com.zhuanjie.learnhelper.data.QuizResult
import com.zhuanjie.learnhelper.network.QwenApi
import com.zhuanjie.learnhelper.ui.screen.AiChatScreen
import com.zhuanjie.learnhelper.ui.screen.BankDetailScreen
import com.zhuanjie.learnhelper.ui.screen.BankManagerScreen
import com.zhuanjie.learnhelper.ui.screen.QuestionEditScreen
import com.zhuanjie.learnhelper.ui.screen.QuizResultScreen
import com.zhuanjie.learnhelper.ui.screen.QuizScreen
import com.zhuanjie.learnhelper.ui.screen.SettingsScreen
import com.zhuanjie.learnhelper.data.SummaryManager
import com.zhuanjie.learnhelper.ui.screen.SummaryScreen
import com.zhuanjie.learnhelper.ui.theme.LearnHelperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LearnHelperTheme {
                LearnHelperApp()
            }
        }
    }
}

enum class AppTab(
    val label: String,
    val icon: ImageVector
) {
    QUIZ("刷题", Icons.Default.Edit),
    SUMMARY("总结", Icons.Default.Star),
    SETTINGS("设置", Icons.Default.Settings)
}

@Composable
fun LearnHelperApp() {
    val context = LocalContext.current
    val prefManager = remember { PreferenceManager(context) }
    val chatStorage = remember { ChatStorage(context) }
    val bankManager = remember { BankManager(context) }
    val qwenApi = remember { QwenApi() }
    val summaryManager = remember { SummaryManager(context) }

    var activeBankId by rememberSaveable { mutableStateOf(bankManager.activeBankId) }
    var questions by remember { mutableStateOf(bankManager.loadActiveQuestions()) }
    var banks by remember { mutableStateOf(bankManager.getBanks()) }

    var currentTab by rememberSaveable { mutableStateOf(AppTab.QUIZ) }

    // Overlay screens
    var showAiChat by rememberSaveable { mutableStateOf(false) }
    var aiChatQuestionId by rememberSaveable { mutableStateOf("") }
    var showWrongReview by rememberSaveable { mutableStateOf(false) }
    var wrongReviewQuestions by remember { mutableStateOf<List<Question>>(emptyList()) }
    var showQuizResult by rememberSaveable { mutableStateOf(false) }
    var quizResult by remember { mutableStateOf<QuizResult?>(null) }
    var showBankManager by rememberSaveable { mutableStateOf(false) }

    // Edit / Delete state
    var showEditScreen by rememberSaveable { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<Question?>(null) }
    var editingBankId by rememberSaveable { mutableStateOf("") } // which bank the edit belongs to
    var showDeleteDialog by remember { mutableStateOf<Question?>(null) }

    // Bank detail state
    var showBankDetail by rememberSaveable { mutableStateOf(false) }
    var detailBank by remember { mutableStateOf<QuestionBank?>(null) }

    fun reloadQuestions() {
        questions = bankManager.loadQuestions(activeBankId)
        banks = bankManager.getBanks()
    }

    val openAiChat: (Question) -> Unit = { question ->
        aiChatQuestionId = question.id
        showAiChat = true
    }

    val onBankChanged: (String) -> Unit = { bankId ->
        activeBankId = bankId
        if (bankId != BankManager.MIXED_ID) {
            bankManager.activeBankId = bankId
        }
        questions = bankManager.loadQuestions(bankId)
        banks = bankManager.getBanks()
    }

    val onEditQuestion: (Question) -> Unit = { question ->
        editingQuestion = question
        editingBankId = activeBankId
        showEditScreen = true
    }

    val onDeleteQuestion: (Question) -> Unit = { question ->
        showDeleteDialog = question
    }

    // Edit/add from bank detail (uses specific bankId, not active)
    val onEditQuestionInBank: (String, Question) -> Unit = { bankId, question ->
        editingQuestion = question
        editingBankId = bankId
        showEditScreen = true
    }

    val onAddQuestionToBank: (String) -> Unit = { bankId ->
        editingQuestion = null
        editingBankId = bankId
        showEditScreen = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavigationSuiteScaffold(
            navigationSuiteItems = {
                AppTab.entries.forEach { tab ->
                    item(
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                        selected = tab == currentTab,
                        onClick = { currentTab = tab }
                    )
                }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize().tabVisibility(currentTab == AppTab.QUIZ)) {
                    QuizScreen(
                        questions = questions,
                        prefManager = prefManager,
                        onOpenAiChat = openAiChat,
                        onFinish = { result ->
                            quizResult = result
                            showQuizResult = true
                        },
                        onEditQuestion = onEditQuestion,
                        onDeleteQuestion = onDeleteQuestion,
                        banks = banks,
                        activeBankId = activeBankId,
                        onSwitchBank = onBankChanged
                    )
                }

                Box(modifier = Modifier.fillMaxSize().tabVisibility(currentTab == AppTab.SUMMARY)) {
                    SummaryScreen(
                        allQuestions = questions,
                        prefManager = prefManager,
                        chatStorage = chatStorage,
                        summaryManager = summaryManager,
                        onOpenAiChat = openAiChat,
                        onStartReview = { wrongQuestions ->
                            wrongReviewQuestions = wrongQuestions
                            showWrongReview = true
                        },
                        onEditQuestion = onEditQuestion,
                        onDeleteQuestion = onDeleteQuestion
                    )
                }

                Box(modifier = Modifier.fillMaxSize().tabVisibility(currentTab == AppTab.SETTINGS)) {
                    SettingsScreen(
                        prefManager = prefManager,
                        totalQuestions = questions.size,
                        onOpenBankManager = { showBankManager = true }
                    )
                }
            }
        }

        // Wrong review overlay
        if (showWrongReview) {
            Surface(modifier = Modifier.fillMaxSize()) {
                QuizScreen(
                    questions = wrongReviewQuestions,
                    prefManager = prefManager,
                    isReviewMode = true,
                    onOpenAiChat = openAiChat,
                    onBack = { showWrongReview = false },
                    onFinish = { result ->
                        quizResult = result
                        showQuizResult = true
                        showWrongReview = false
                    },
                    onEditQuestion = onEditQuestion,
                    onDeleteQuestion = onDeleteQuestion
                )
            }
        }

        // Quiz result overlay
        if (showQuizResult && quizResult != null) {
            Surface(modifier = Modifier.fillMaxSize()) {
                QuizResultScreen(
                    result = quizResult!!,
                    qwenApi = qwenApi,
                    prefManager = prefManager,
                    summaryManager = summaryManager,
                    onBack = { showQuizResult = false },
                    onOpenAiChat = openAiChat
                )
            }
        }

        // Bank manager overlay
        if (showBankManager) {
            Surface(modifier = Modifier.fillMaxSize()) {
                BankManagerScreen(
                    bankManager = bankManager,
                    onBack = {
                        showBankManager = false
                        reloadQuestions()
                    },
                    onBankChanged = onBankChanged,
                    onOpenBankDetail = { bank ->
                        detailBank = bank
                        showBankDetail = true
                    }
                )
            }
        }

        // Bank detail overlay
        if (showBankDetail && detailBank != null) {
            Surface(modifier = Modifier.fillMaxSize()) {
                BankDetailScreen(
                    bank = detailBank!!,
                    bankManager = bankManager,
                    onBack = {
                        showBankDetail = false
                        reloadQuestions()
                    },
                    onEditQuestion = { question ->
                        onEditQuestionInBank(detailBank!!.id, question)
                    },
                    onAddQuestion = onAddQuestionToBank
                )
            }
        }

        // Question edit overlay (edit existing or add new)
        if (showEditScreen) {
            Surface(modifier = Modifier.fillMaxSize()) {
                QuestionEditScreen(
                    question = editingQuestion,
                    onSave = { updated ->
                        val targetBankId = editingBankId.ifBlank { activeBankId }
                        if (editingQuestion != null) {
                            bankManager.updateQuestion(targetBankId, editingQuestion!!.id, updated)
                        } else {
                            bankManager.addQuestion(targetBankId, updated)
                        }
                        reloadQuestions()
                        showEditScreen = false
                        editingQuestion = null
                    },
                    onBack = {
                        showEditScreen = false
                        editingQuestion = null
                    }
                )
            }
        }

        // AI chat overlay (on top of everything)
        if (showAiChat && aiChatQuestionId.isNotEmpty()) {
            val question = questions.find { it.id == aiChatQuestionId }
            if (question != null) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AiChatScreen(
                        question = question,
                        qwenApi = qwenApi,
                        chatStorage = chatStorage,
                        prefManager = prefManager,
                        onBack = { showAiChat = false }
                    )
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { question ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除题目") },
            text = { Text("确定删除\"${question.title}\"?\n\n${question.question.take(50)}...") },
            confirmButton = {
                TextButton(onClick = {
                    bankManager.deleteQuestion(activeBankId, question.id)
                    reloadQuestions()
                    showDeleteDialog = null
                }) {
                    Text("删除", color = androidx.compose.material3.MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("取消") }
            }
        )
    }
}

private fun Modifier.tabVisibility(visible: Boolean): Modifier {
    return if (visible) {
        this.zIndex(1f)
    } else {
        this.zIndex(0f).alpha(0f)
    }
}

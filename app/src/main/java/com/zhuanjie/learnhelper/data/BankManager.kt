package com.zhuanjie.learnhelper.data

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zhuanjie.learnhelper.data.db.AppDatabase
import com.zhuanjie.learnhelper.data.db.QuestionBankEntity
import com.zhuanjie.learnhelper.data.db.QuestionEntity
import java.util.UUID

data class QuestionBank(
    val id: String,
    val name: String,
    val questionCount: Int,
    val hasMultiChoice: Boolean = false,
    val isBuiltin: Boolean = false
)

data class RelatedDataCount(
    val chatCount: Int,
    val wrongCount: Int,
    val explanationCount: Int
) {
    val hasAny: Boolean get() = chatCount > 0 || wrongCount > 0 || explanationCount > 0
}

class BankManager(private val context: Context) {
    private val gson = Gson()
    private val db = AppDatabase.getInstance(context)
    private val questionDao = db.questionDao()
    private val bankDao = db.bankDao()
    private val chatMessageDao = db.chatMessageDao()
    private val wrongAnswerDao = db.wrongAnswerDao()
    private val customExplanationDao = db.customExplanationDao()
    private val prefs = context.getSharedPreferences("bank_manager", Context.MODE_PRIVATE)

    companion object {
        const val BUILTIN_ID = "builtin_sysarch"
        const val MIXED_ID = "_mixed_all_"
        private const val BUILTIN_NAME = "系统架构设计师-课后习题"
    }

    init {
        ensureBuiltinImported()
    }

    /** Import builtin bank from assets on first launch */
    private fun ensureBuiltinImported() {
        if (!bankDao.exists(BUILTIN_ID)) {
            bankDao.insert(QuestionBankEntity(BUILTIN_ID, BUILTIN_NAME, isBuiltin = true))
            val questions = QuestionLoader.loadFromAssets(context).map { q ->
                if (q.type == null && q.answer.length > 1) q.copy(type = "multi") else q
            }
            questionDao.insertAll(questions.map { it.toEntity(BUILTIN_ID) })
        }
    }

    fun getBanks(): List<QuestionBank> {
        return bankDao.getAll().map { entity ->
            val count = questionDao.countByBank(entity.id)
            val hasMulti = questionDao.countMultiByBank(entity.id) > 0
            QuestionBank(entity.id, entity.name, count, hasMulti, entity.isBuiltin)
        }
    }

    var activeBankId: String
        get() = prefs.getString("active_bank_id", BUILTIN_ID) ?: BUILTIN_ID
        set(value) = prefs.edit().putString("active_bank_id", value).apply()

    fun loadQuestions(bankId: String): List<Question> {
        if (bankId == MIXED_ID) return loadAllQuestions()
        return questionDao.getByBank(bankId).map { it.toQuestion() }
    }

    fun loadAllQuestions(): List<Question> {
        return questionDao.getAll().map { it.toQuestion() }
    }

    fun loadActiveQuestions(): List<Question> = loadQuestions(activeBankId)

    fun searchQuestions(bankId: String, query: String): List<Question> {
        return questionDao.search(bankId, query).map { it.toQuestion() }
    }

    // --- Mutation ---

    fun addQuestion(bankId: String, question: Question) {
        questionDao.insert(question.toEntity(bankId))
    }

    fun updateQuestion(bankId: String, question: Question) {
        val realBankId = if (bankId == MIXED_ID) questionDao.findBankById(question.dbId) ?: return else bankId
        val entity = questionDao.findById(question.dbId) ?: return
        questionDao.update(question.toEntity(realBankId, entity.id))
    }

    fun deleteQuestion(questionDbId: Long) {
        // Cascade delete related data
        chatMessageDao.deleteByQuestion(questionDbId)
        wrongAnswerDao.deleteByQuestionId(questionDbId)
        customExplanationDao.deleteByQuestionId(questionDbId)
        questionDao.deleteById(questionDbId)
    }

    fun getRelatedDataCount(questionDbId: Long): RelatedDataCount {
        return RelatedDataCount(
            chatCount = chatMessageDao.countByQuestion(questionDbId),
            wrongCount = wrongAnswerDao.countByQuestionId(questionDbId),
            explanationCount = customExplanationDao.countByQuestionId(questionDbId)
        )
    }

    fun findBankIdForQuestion(questionDbId: Long): String? {
        return questionDao.findBankById(questionDbId)
    }

    // --- Import / Export ---

    fun importBank(name: String, uri: Uri): QuestionBank {
        val json = context.contentResolver.openInputStream(uri)
            ?.bufferedReader()?.use { it.readText() }
            ?: throw Exception("无法读取文件")

        val questions = QuestionLoader.parseQuestions(json)
        if (questions.isEmpty()) throw Exception("题库为空或格式不正确")

        questions.forEachIndexed { i, q ->
            if (q.question.isBlank()) throw Exception("第 ${i + 1} 题缺少题目内容")
            if (q.options.isEmpty()) throw Exception("第 ${i + 1} 题缺少选项")
            if (q.answer.isBlank()) throw Exception("第 ${i + 1} 题缺少答案")
        }

        // Auto-detect multi-choice: if answer has more than 1 character and type is not set
        val fixedQuestions = questions.map { q ->
            if (q.type == null && q.answer.length > 1) q.copy(type = "multi") else q
        }

        val id = UUID.randomUUID().toString()
        bankDao.insert(QuestionBankEntity(id, name))
        questionDao.insertAll(fixedQuestions.map { it.toEntity(id) })

        val hasMulti = questions.any { it.isMultiChoice }
        return QuestionBank(id, name, questions.size, hasMulti)
    }

    fun deleteBank(bankId: String) {
        if (bankId == BUILTIN_ID) return
        // Cascade delete all related data for all questions in this bank
        val questions = questionDao.getByBank(bankId)
        questions.forEach { q ->
            chatMessageDao.deleteByQuestion(q.id)
            wrongAnswerDao.deleteByQuestionId(q.id)
            customExplanationDao.deleteByQuestionId(q.id)
        }
        questionDao.deleteAllByBank(bankId)
        bankDao.deleteById(bankId)
        if (activeBankId == bankId) {
            activeBankId = BUILTIN_ID
        }
    }

    fun resetBuiltin() {
        // Cascade delete related data for builtin questions
        val questions = questionDao.getByBank(BUILTIN_ID)
        questions.forEach { q ->
            chatMessageDao.deleteByQuestion(q.id)
            wrongAnswerDao.deleteByQuestionId(q.id)
            customExplanationDao.deleteByQuestionId(q.id)
        }
        questionDao.deleteAllByBank(BUILTIN_ID)
        val newQuestions = QuestionLoader.loadFromAssets(context)
        questionDao.insertAll(newQuestions.map { it.toEntity(BUILTIN_ID) })
    }

    fun exportBankJson(bankId: String): String {
        val questions = loadQuestions(bankId)
        return gson.toJson(questions)
    }

    fun getSampleJson(): String {
        val sample = listOf(
            Question(tag = "计算机网络", number = 1,
                question = "在 OSI 参考模型中，负责数据加密/解密的是（ ）。",
                options = mapOf("A" to "应用层", "B" to "表示层", "C" to "会话层", "D" to "传输层"),
                answer = "B", type = "single",
                explanation = "表示层负责数据格式转换、加密解密、压缩解压缩等功能。"),
            Question(tag = "计算机组成", number = 2,
                question = "以下关于 RISC 的说法，正确的是（ ）。",
                options = mapOf("A" to "指令种类多", "B" to "寻址方式复杂", "C" to "适合流水线", "D" to "指令长度不固定"),
                answer = "C", type = "single",
                explanation = "RISC 指令格式统一、长度固定，更适合流水线执行。"),
            Question(tag = "数据库", number = 3,
                question = "以下属于非关系型数据库的有（ ）。",
                options = mapOf("A" to "MySQL", "B" to "MongoDB", "C" to "Redis", "D" to "Oracle"),
                answer = "BC", type = "multi",
                explanation = "MongoDB 是文档型数据库，Redis 是键值对数据库，都属于 NoSQL。"),
            Question(tag = "软件工程", number = 4,
                question = "面向对象的基本特征包括（ ）。",
                options = mapOf("A" to "封装", "B" to "继承", "C" to "多态", "D" to "重载"),
                answer = "ABC", type = "multi",
                explanation = "面向对象的三大基本特征是封装、继承和多态。重载是多态的一种实现方式，但不是基本特征。")
        )
        return gson.toJson(sample)
    }

    // --- Conversion helpers ---

    private fun Question.toEntity(bankId: String, existingId: Long = 0): QuestionEntity {
        return QuestionEntity(
            id = if (existingId != 0L) existingId else SnowflakeId.next(),
            bankId = bankId,
            stringId = this.id,
            tag = tag,
            number = number,
            questionText = question,
            optionsJson = gson.toJson(options),
            answer = answer,
            explanation = explanation,
            type = type,
            year = year,
            month = month
        )
    }

    private fun QuestionEntity.toQuestion(): Question {
        val optionsType = object : TypeToken<Map<String, String>>() {}.type
        val opts: Map<String, String> = try {
            gson.fromJson(optionsJson, optionsType) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }

        return Question(
            tag = tag,
            number = number,
            question = questionText,
            options = opts,
            answer = answer,
            explanation = explanation,
            type = type,
            year = year,
            month = month,
            dbId = id
        )
    }
}

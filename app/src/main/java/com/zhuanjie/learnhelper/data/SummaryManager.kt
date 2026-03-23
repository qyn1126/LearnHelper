package com.zhuanjie.learnhelper.data

import android.content.Context
import com.google.gson.Gson
import com.zhuanjie.learnhelper.data.db.AppDatabase
import com.zhuanjie.learnhelper.data.db.QuizSummaryEntity

data class QuizSummaryItem(
    val id: Long,
    val timestamp: Long,
    val totalCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val accuracy: Float,
    val aiAnalysis: String?,
    val wrongDetails: List<WrongDetail>
)

data class WrongDetail(
    val question: String,
    val options: Map<String, String> = emptyMap(),
    val userAnswer: String,
    val correctAnswer: String,
    val explanation: String? = null
)

class SummaryManager(context: Context) {
    private val dao = AppDatabase.getInstance(context).quizSummaryDao()
    private val gson = Gson()

    fun saveSummary(result: QuizResult, aiAnalysis: String? = null): Long {
        val wrongDetails = result.wrongRecords.map {
            WrongDetail(
                question = it.question.question,
                options = it.question.options,
                userAnswer = it.userAnswer,
                correctAnswer = it.question.answer,
                explanation = it.question.explanationText.ifBlank { null }
            )
        }
        val entity = QuizSummaryEntity(
            totalCount = result.totalCount,
            correctCount = result.correctCount,
            wrongCount = result.wrongCount,
            accuracy = result.accuracy,
            aiAnalysis = aiAnalysis,
            detailJson = gson.toJson(wrongDetails)
        )
        return dao.insert(entity)
    }

    fun updateAiAnalysis(id: Long, aiAnalysis: String) {
        val all = dao.getAll()
        val entity = all.find { it.id == id } ?: return
        dao.update(entity.copy(aiAnalysis = aiAnalysis))
    }

    fun getAllSummaries(): List<QuizSummaryItem> {
        return dao.getAll().map { entity ->
            val details: List<WrongDetail> = try {
                gson.fromJson(entity.detailJson ?: "[]", Array<WrongDetail>::class.java).toList()
            } catch (_: Exception) { emptyList() }
            QuizSummaryItem(
                id = entity.id,
                timestamp = entity.timestamp,
                totalCount = entity.totalCount,
                correctCount = entity.correctCount,
                wrongCount = entity.wrongCount,
                accuracy = entity.accuracy,
                aiAnalysis = entity.aiAnalysis,
                wrongDetails = details
            )
        }
    }

    fun deleteSummary(id: Long) {
        dao.deleteById(id)
    }

    fun deleteAll() {
        dao.deleteAll()
    }
}

package com.zhuanjie.learnhelper.data

data class AnswerRecord(
    val question: Question,
    val userAnswer: String,
    val isCorrect: Boolean
)

data class QuizResult(
    val records: List<AnswerRecord>
) {
    val totalCount: Int get() = records.size
    val correctCount: Int get() = records.count { it.isCorrect }
    val wrongCount: Int get() = records.count { !it.isCorrect }
    val accuracy: Float get() = if (totalCount > 0) correctCount.toFloat() / totalCount else 0f
    val correctRecords: List<AnswerRecord> get() = records.filter { it.isCorrect }
    val wrongRecords: List<AnswerRecord> get() = records.filter { !it.isCorrect }
}

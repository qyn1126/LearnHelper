package com.zhuanjie.learnhelper.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class Question(
    val tag: String? = null,
    val number: Int = 0,
    val question: String = "",
    val options: Map<String, String> = emptyMap(),
    val answer: String = "",
    val explanation: String? = null,
    val type: String? = null, // "single", "multi", or null (treated as single)
    // backward compat: old JSON uses year+month instead of tag
    val year: String? = null,
    val month: String? = null,
    @Transient val dbId: Long = 0 // Room auto-increment ID, unique per question
) {
    /** Resolved tag: prefers explicit tag, falls back to year+month */
    val displayTag: String
        get() = tag ?: if (!year.isNullOrBlank() || !month.isNullOrBlank())
            "${year ?: ""}年${month ?: ""}月" else ""

    /** Stable ID: uses same components so old wrong-answer / chat data is preserved */
    val id: String
        get() = if (tag != null) "${tag}_$number"
        else "${year ?: ""}_${month ?: ""}_$number"

    val title: String
        get() {
            val t = displayTag
            return if (t.isNotBlank()) "$t 第${number}题" else "第${number}题"
        }

    val isMultiChoice: Boolean get() = type == "multi"
    val explanationText: String get() = explanation ?: ""
    val correctAnswerSet: Set<String>
        get() = answer.map { it.toString() }.toSortedSet()
}

object QuestionLoader {
    private val gson = Gson()

    fun loadFromAssets(context: Context): List<Question> {
        val json = context.assets.open("questions.json").bufferedReader().use { it.readText() }
        return parseQuestions(json)
    }

    fun parseQuestions(json: String): List<Question> {
        val type = object : TypeToken<List<Question>>() {}.type
        val questions: List<Question> = gson.fromJson(json, type) ?: emptyList()
        return questions.sortedWith(compareBy({ it.displayTag }, { it.number }))
    }
}

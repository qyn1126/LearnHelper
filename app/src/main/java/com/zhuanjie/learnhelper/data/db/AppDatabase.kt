package com.zhuanjie.learnhelper.data.db

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update

// ==================== Question Entities ====================

@Entity(tableName = "questions", indices = [Index("bankId"), Index("bankId", "stringId")])
data class QuestionEntity(
    @PrimaryKey val id: Long, // snowflake ID, NOT autoGenerate
    val bankId: String,
    val stringId: String,
    val tag: String?,
    val number: Int,
    @ColumnInfo(name = "question_text") val questionText: String,
    val optionsJson: String,
    val answer: String,
    val explanation: String?,
    val type: String?,
    val year: String?,
    val month: String?
)

@Entity(tableName = "question_banks")
data class QuestionBankEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isBuiltin: Boolean = false
)

// ==================== Wrong Answer Entity ====================

@Entity(tableName = "wrong_answers", indices = [Index("questionId")])
data class WrongAnswerEntity(
    @PrimaryKey val id: Long, // snowflake ID
    val questionId: Long, // FK → questions.id
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== Custom Explanation Entity ====================

@Entity(tableName = "custom_explanations", indices = [Index("questionId", unique = true)])
data class CustomExplanationEntity(
    @PrimaryKey val id: Long, // snowflake ID
    val questionId: Long, // FK → questions.id
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== Chat Entities ====================

@Entity(tableName = "chat_messages", indices = [Index("questionId")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long, // FK → questions.id (snowflake ID)
    val role: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

// ==================== Quiz Summary Entities ====================

@Entity(tableName = "quiz_summaries")
data class QuizSummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val totalCount: Int,
    val correctCount: Int,
    val wrongCount: Int,
    val accuracy: Float,
    val aiAnalysis: String? = null,
    val detailJson: String? = null
)

// ==================== DAOs ====================

@Dao
interface QuestionDao {
    @Query("SELECT * FROM questions WHERE bankId = :bankId ORDER BY COALESCE(tag, year || '年' || month || '月'), number")
    fun getByBank(bankId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY COALESCE(tag, year || '年' || month || '月'), number")
    fun getAll(): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE bankId = :bankId")
    fun countByBank(bankId: String): Int

    @Query("SELECT COUNT(*) FROM questions WHERE bankId = :bankId AND type = 'multi'")
    fun countMultiByBank(bankId: String): Int

    @Insert
    fun insert(question: QuestionEntity): Long

    @Insert
    fun insertAll(questions: List<QuestionEntity>)

    @Update
    fun update(question: QuestionEntity)

    @Query("DELETE FROM questions WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM questions WHERE bankId = :bankId AND stringId = :stringId")
    fun deleteByStringId(bankId: String, stringId: String)

    @Query("SELECT * FROM questions WHERE bankId = :bankId AND stringId = :stringId LIMIT 1")
    fun findByStringId(bankId: String, stringId: String): QuestionEntity?

    @Query("SELECT bankId FROM questions WHERE stringId = :stringId LIMIT 1")
    fun findBankByStringId(stringId: String): String?

    @Query("SELECT * FROM questions WHERE id = :id LIMIT 1")
    fun findById(id: Long): QuestionEntity?

    @Query("SELECT bankId FROM questions WHERE id = :id LIMIT 1")
    fun findBankById(id: Long): String?

    @Query("DELETE FROM questions WHERE bankId = :bankId")
    fun deleteAllByBank(bankId: String)

    @Query("SELECT * FROM questions WHERE bankId = :bankId AND (question_text LIKE '%' || :query || '%' OR tag LIKE '%' || :query || '%' OR explanation LIKE '%' || :query || '%') ORDER BY COALESCE(tag, year || '年' || month || '月'), number")
    fun search(bankId: String, query: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    fun findByIds(ids: List<Long>): List<QuestionEntity>
}

@Dao
interface QuestionBankDao {
    @Query("SELECT * FROM question_banks ORDER BY isBuiltin DESC, name")
    fun getAll(): List<QuestionBankEntity>

    @Insert
    fun insert(bank: QuestionBankEntity)

    @Query("DELETE FROM question_banks WHERE id = :id")
    fun deleteById(id: String)

    @Query("SELECT EXISTS(SELECT 1 FROM question_banks WHERE id = :id)")
    fun exists(id: String): Boolean
}

@Dao
interface WrongAnswerDao {
    @Query("SELECT * FROM wrong_answers ORDER BY timestamp DESC")
    fun getAll(): List<WrongAnswerEntity>

    @Query("SELECT questionId FROM wrong_answers")
    fun getAllQuestionIds(): List<Long>

    @Query("SELECT EXISTS(SELECT 1 FROM wrong_answers WHERE questionId = :questionId)")
    fun exists(questionId: Long): Boolean

    @Insert
    fun insert(entity: WrongAnswerEntity)

    @Query("DELETE FROM wrong_answers WHERE questionId = :questionId")
    fun deleteByQuestionId(questionId: Long)

    @Query("DELETE FROM wrong_answers")
    fun deleteAll()

    @Query("SELECT COUNT(*) FROM wrong_answers WHERE questionId = :questionId")
    fun countByQuestionId(questionId: Long): Int

    @Query("SELECT * FROM wrong_answers WHERE questionId = :questionId LIMIT 1")
    fun findByQuestionId(questionId: Long): WrongAnswerEntity?

    @Update
    fun update(entity: WrongAnswerEntity)
}

@Dao
interface CustomExplanationDao {
    @Query("SELECT * FROM custom_explanations WHERE questionId = :questionId LIMIT 1")
    fun findByQuestionId(questionId: Long): CustomExplanationEntity?

    @Insert
    fun insert(entity: CustomExplanationEntity)

    @Update
    fun update(entity: CustomExplanationEntity)

    @Query("DELETE FROM custom_explanations WHERE questionId = :questionId")
    fun deleteByQuestionId(questionId: Long)

    @Query("SELECT COUNT(*) FROM custom_explanations WHERE questionId = :questionId")
    fun countByQuestionId(questionId: Long): Int
}

@Dao
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE questionId = :questionId ORDER BY timestamp")
    fun getByQuestion(questionId: Long): List<ChatMessageEntity>

    @Insert
    fun insert(message: ChatMessageEntity): Long

    @Insert
    fun insertAll(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE questionId = :questionId")
    fun deleteByQuestion(questionId: Long)

    @Query("SELECT DISTINCT questionId FROM chat_messages ORDER BY questionId")
    fun getAllQuestionIds(): List<Long>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE questionId = :questionId")
    fun countByQuestion(questionId: Long): Int

    @Query("SELECT * FROM chat_messages WHERE questionId = :questionId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessage(questionId: Long): ChatMessageEntity?
}

@Dao
interface QuizSummaryDao {
    @Query("SELECT * FROM quiz_summaries ORDER BY timestamp DESC")
    fun getAll(): List<QuizSummaryEntity>

    @Insert
    fun insert(summary: QuizSummaryEntity): Long

    @Update
    fun update(summary: QuizSummaryEntity)

    @Query("DELETE FROM quiz_summaries WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM quiz_summaries")
    fun deleteAll()
}

// ==================== Database ====================

@Database(
    entities = [
        QuestionEntity::class,
        QuestionBankEntity::class,
        WrongAnswerEntity::class,
        CustomExplanationEntity::class,
        ChatMessageEntity::class,
        QuizSummaryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun bankDao(): QuestionBankDao
    abstract fun wrongAnswerDao(): WrongAnswerDao
    abstract fun customExplanationDao(): CustomExplanationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun quizSummaryDao(): QuizSummaryDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "learn_helper.db"
                )
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}

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
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
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

// ==================== Chat Entities ====================

@Entity(tableName = "chat_messages", indices = [Index("questionId")])
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: String,
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
    val aiAnalysis: String? = null, // AI analysis text if generated
    val detailJson: String? = null  // JSON of wrong question summaries for display
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

    @Query("DELETE FROM questions WHERE bankId = :bankId")
    fun deleteAllByBank(bankId: String)

    @Query("SELECT * FROM questions WHERE bankId = :bankId AND (question_text LIKE '%' || :query || '%' OR tag LIKE '%' || :query || '%' OR explanation LIKE '%' || :query || '%') ORDER BY COALESCE(tag, year || '年' || month || '月'), number")
    fun search(bankId: String, query: String): List<QuestionEntity>
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
interface ChatMessageDao {
    @Query("SELECT * FROM chat_messages WHERE questionId = :questionId ORDER BY timestamp")
    fun getByQuestion(questionId: String): List<ChatMessageEntity>

    @Insert
    fun insert(message: ChatMessageEntity): Long

    @Insert
    fun insertAll(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_messages WHERE questionId = :questionId")
    fun deleteByQuestion(questionId: String)

    @Query("SELECT DISTINCT questionId FROM chat_messages ORDER BY questionId")
    fun getAllQuestionIds(): List<String>

    @Query("SELECT COUNT(*) FROM chat_messages WHERE questionId = :questionId")
    fun countByQuestion(questionId: String): Int

    @Query("SELECT * FROM chat_messages WHERE questionId = :questionId ORDER BY timestamp DESC LIMIT 1")
    fun getLastMessage(questionId: String): ChatMessageEntity?
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
        ChatMessageEntity::class,
        QuizSummaryEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun questionDao(): QuestionDao
    abstract fun bankDao(): QuestionBankDao
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

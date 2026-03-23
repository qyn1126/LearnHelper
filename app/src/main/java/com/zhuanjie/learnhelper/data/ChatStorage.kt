package com.zhuanjie.learnhelper.data

import android.content.Context
import com.zhuanjie.learnhelper.data.db.AppDatabase
import com.zhuanjie.learnhelper.data.db.ChatMessageEntity

class ChatStorage(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).chatMessageDao()

    fun getMessages(questionId: String): List<ChatMessage> {
        return dao.getByQuestion(questionId).map {
            ChatMessage(role = it.role, content = it.content, timestamp = it.timestamp)
        }
    }

    fun saveMessages(questionId: String, messages: List<ChatMessage>) {
        dao.deleteByQuestion(questionId)
        dao.insertAll(messages.map {
            ChatMessageEntity(
                questionId = questionId,
                role = it.role,
                content = it.content,
                timestamp = it.timestamp
            )
        })
    }

    fun getAllChatQuestionIds(): List<String> {
        return dao.getAllQuestionIds()
    }

    fun getMessageCount(questionId: String): Int {
        return dao.countByQuestion(questionId)
    }

    fun getLastMessage(questionId: String): ChatMessage? {
        return dao.getLastMessage(questionId)?.let {
            ChatMessage(role = it.role, content = it.content, timestamp = it.timestamp)
        }
    }

    fun deleteMessages(questionId: String) {
        dao.deleteByQuestion(questionId)
    }
}

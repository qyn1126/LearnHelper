package com.zhuanjie.learnhelper.data

import android.content.Context
import com.zhuanjie.learnhelper.data.db.AppDatabase
import com.zhuanjie.learnhelper.data.db.ChatMessageEntity

class ChatStorage(private val context: Context) {
    private val dao = AppDatabase.getInstance(context).chatMessageDao()

    fun getMessages(questionId: Long): List<ChatMessage> {
        return dao.getByQuestion(questionId).map {
            ChatMessage(role = it.role, content = it.content, timestamp = it.timestamp)
        }
    }

    fun saveMessages(questionId: Long, messages: List<ChatMessage>) {
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

    fun getAllChatQuestionIds(): List<Long> {
        return dao.getAllQuestionIds()
    }

    fun getMessageCount(questionId: Long): Int {
        return dao.countByQuestion(questionId)
    }

    fun getLastMessage(questionId: Long): ChatMessage? {
        return dao.getLastMessage(questionId)?.let {
            ChatMessage(role = it.role, content = it.content, timestamp = it.timestamp)
        }
    }

    fun deleteMessages(questionId: Long) {
        dao.deleteByQuestion(questionId)
    }
}

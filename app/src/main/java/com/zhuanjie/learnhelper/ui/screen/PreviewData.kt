package com.zhuanjie.learnhelper.ui.screen

import com.zhuanjie.learnhelper.data.AnswerRecord
import com.zhuanjie.learnhelper.data.Question
import com.zhuanjie.learnhelper.data.QuizResult

internal val sampleQuestions = listOf(
    Question(
        tag = "计算机网络", number = 1,
        question = "在 OSI 参考模型中，负责对应用层消息进行加密/解密、压缩/解压缩的是（ ）。",
        options = mapOf("A" to "应用层", "B" to "表示层", "C" to "会话层", "D" to "传输层"),
        answer = "B", type = "single",
        explanation = "表示层负责数据格式转换、加密解密、压缩解压缩等功能。"
    ),
    Question(
        tag = "计算机组成", number = 2,
        question = "以下关于 RISC 和 CISC 的叙述中，正确的是（ ）。",
        options = mapOf(
            "A" to "RISC 采用复杂指令集",
            "B" to "CISC 指令种类少，指令功能简单",
            "C" to "RISC 更适合流水线技术",
            "D" to "CISC 大多数指令在一个时钟周期内完成"
        ),
        answer = "C", type = "single",
        explanation = "RISC 指令格式统一，便于流水线执行。"
    ),
    Question(
        tag = "数据库", number = 3,
        question = "以下属于非关系型数据库的有（ ）。",
        options = mapOf("A" to "MySQL", "B" to "MongoDB", "C" to "Redis", "D" to "Oracle"),
        answer = "BC", type = "multi",
        explanation = "MongoDB 是文档型数据库，Redis 是键值对数据库，都属于 NoSQL。"
    )
)

internal val sampleResult = QuizResult(
    records = listOf(
        AnswerRecord(sampleQuestions[0], "B", true),
        AnswerRecord(sampleQuestions[1], "A", false),
        AnswerRecord(sampleQuestions[2], "BC", true)
    )
)

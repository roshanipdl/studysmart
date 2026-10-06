package com.studysmart.app.core.model

data class FormattedAnswer(val nodes: List<AnswerNode>)

sealed interface AnswerNode {
    data class Text(val value: String) : AnswerNode
    data class Element(val tag: String, val children: List<AnswerNode>) : AnswerNode
}

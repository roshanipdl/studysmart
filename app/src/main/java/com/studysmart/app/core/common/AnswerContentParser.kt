package com.studysmart.app.core.common

import com.studysmart.app.core.model.AnswerFormat
import com.studysmart.app.core.model.AnswerNode
import com.studysmart.app.core.model.FormattedAnswer

/** Parses only the published answer grammar; never repairs HTML or guesses a format. */
object AnswerContentParser {
    const val MAX_INPUT_LENGTH = 262_144
    const val MAX_DEPTH = 32

    private val allowed = mapOf(
        "root" to setOf("p", "ul", "ol", "pre"),
        "p" to setOf("strong", "em", "code"),
        "strong" to setOf("strong", "em", "code"),
        "em" to setOf("strong", "em", "code"),
        "li" to setOf("strong", "em", "code", "p", "ul", "ol", "pre"),
        "ul" to setOf("li"), "ol" to setOf("li"),
        "pre" to setOf("code"), "code" to emptySet(),
    )

    private class Frame(val tag: String) {
        val children = mutableListOf<AnswerNode>()
        var visible = false
    }

    fun parse(content: String, format: AnswerFormat): FormattedAnswer {
        require(content.length <= MAX_INPUT_LENGTH) { "Answer exceeds input limit" }
        if (format == AnswerFormat.PLAIN_TEXT) return FormattedAnswer(listOf(AnswerNode.Text(content)))
        require(content.isNotEmpty()) { "Empty HTML answer" }
        require(content.none { it.code < 32 && it !in "\t\r\n" }) { "Invalid control character" }
        val stack = mutableListOf(Frame("root"))
        var index = 0
        fun addText(text: String) {
            val frame = stack.last()
            if (frame.tag in setOf("root", "ul", "ol", "pre")) {
                require(text.isBlank() && frame.tag != "pre") { "Text outside a text container" }
                return
            }
            frame.children.add(AnswerNode.Text(text))
            if (text.isNotBlank()) stack.forEach { it.visible = true }
        }
        while (index < content.length) {
            when (content[index]) {
                '<' -> {
                    val end = content.indexOf('>', index + 1)
                    require(end >= 0) { "Unterminated tag" }
                    val token = content.substring(index + 1, end)
                    if (token.startsWith('/')) {
                        val tag = token.substring(1)
                        require(stack.size > 1 && stack.last().tag == tag) { "Mismatched closing tag" }
                        val frame = stack.removeAt(stack.lastIndex)
                        require(frame.visible) { "Empty element" }
                        if (tag == "pre") require(frame.children.size == 1) { "pre requires exactly one code child" }
                        stack.last().children.add(AnswerNode.Element(tag, frame.children.toList()))
                    } else {
                        val parent = stack.last()
                        require(token in allowed.getValue(parent.tag)) { "Invalid tag or nesting" }
                        require(stack.size <= MAX_DEPTH) { "Answer exceeds nesting limit" }
                        if (parent.tag == "pre") require(parent.children.isEmpty()) { "Multiple code children" }
                        stack.add(Frame(token))
                    }
                    index = end + 1
                }
                '&' -> {
                    val entity = when {
                        content.startsWith("&amp;", index) -> "&amp;" to "&"
                        content.startsWith("&lt;", index) -> "&lt;" to "<"
                        content.startsWith("&gt;", index) -> "&gt;" to ">"
                        else -> throw IllegalArgumentException("Invalid entity")
                    }
                    addText(entity.second)
                    index += entity.first.length
                }
                '>' -> throw IllegalArgumentException("Unescaped reserved character")
                else -> {
                    val start = index
                    while (index < content.length && content[index] !in "<&>") index++
                    addText(content.substring(start, index))
                }
            }
        }
        require(stack.size == 1 && stack.single().visible) { "Unclosed or empty answer" }
        return FormattedAnswer(stack.single().children.toList())
    }

    /** Block boundaries remain explicit; code whitespace is never normalized. */
    fun plainText(answer: FormattedAnswer): String = buildString {
        fun appendNodes(nodes: List<AnswerNode>) {
            nodes.forEach { node ->
                when (node) {
                    is AnswerNode.Text -> append(node.value)
                    is AnswerNode.Element -> {
                        val block = node.tag in setOf("p", "li", "ul", "ol", "pre")
                        if (block && isNotEmpty() && last() != '\n') append('\n')
                        appendNodes(node.children)
                        if (block && isNotEmpty() && last() != '\n') append('\n')
                    }
                }
            }
        }
        appendNodes(answer.nodes)
    }
}

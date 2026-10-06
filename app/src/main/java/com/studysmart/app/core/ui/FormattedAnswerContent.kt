package com.studysmart.app.core.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.studysmart.app.R
import com.studysmart.app.core.common.AnswerContentParser
import com.studysmart.app.core.model.AnswerFormat
import com.studysmart.app.core.model.AnswerNode

@Composable
fun FormattedAnswerContent(content: String, format: AnswerFormat, modifier: Modifier = Modifier) {
    val parsed = remember(content, format) {
        try {
            AnswerContentParser.parse(content, format)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
    if (parsed == null) {
        Text(stringResource(R.string.answer_content_unavailable), modifier,
            color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
    } else {
        key(content, format) {
            SelectionContainer(modifier) { AnswerBlocks(parsed.nodes) }
        }
    }
}

private fun inlineText(nodes: List<AnswerNode>): AnnotatedString = buildAnnotatedString {
    fun appendNodes(children: List<AnswerNode>) {
        children.forEach { node ->
            when (node) {
                is AnswerNode.Text -> append(node.value)
                is AnswerNode.Element -> withStyle(when (node.tag) {
                    "strong" -> SpanStyle(fontWeight = FontWeight.Bold)
                    "em" -> SpanStyle(fontStyle = FontStyle.Italic)
                    "code" -> SpanStyle(fontFamily = FontFamily.Monospace)
                    else -> SpanStyle()
                }) { appendNodes(node.children) }
            }
        }
    }
    appendNodes(nodes)
}

@Composable
private fun AnswerBlocks(nodes: List<AnswerNode>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Group adjacent inline nodes in list items instead of inserting artificial lines.
        var position = 0
        while (position < nodes.size) {
            val node = nodes[position]
            if (node is AnswerNode.Element && node.tag in setOf("p", "ul", "ol", "pre")) {
                when (node.tag) {
                    "p" -> Text(inlineText(node.children), style = MaterialTheme.typography.bodyLarge)
                    "pre" -> Text(
                        AnswerContentParser.plainText(com.studysmart.app.core.model.FormattedAnswer(
                            (node.children.single() as AnswerNode.Element).children)),
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(8.dp),
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        node.children.forEachIndexed { index, child ->
                            val item = child as AnswerNode.Element
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(if (node.tag == "ol") "${index + 1}." else "•", style = MaterialTheme.typography.bodyLarge)
                                Column(Modifier.weight(1f)) { AnswerBlocks(item.children) }
                            }
                        }
                    }
                }
                position++
            } else {
                val start = position
                while (position < nodes.size) {
                    val next = nodes[position]
                    if (next is AnswerNode.Element && next.tag in setOf("p", "ul", "ol", "pre")) break
                    position++
                }
                Text(inlineText(nodes.subList(start, position)), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

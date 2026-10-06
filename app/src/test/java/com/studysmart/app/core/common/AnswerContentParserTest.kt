package com.studysmart.app.core.common

import com.studysmart.app.core.model.AnswerFormat
import com.studysmart.app.core.model.AnswerNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AnswerContentParserTest {
    private fun html(value: String) = AnswerContentParser.parse(value, AnswerFormat.HTML)

    @Test fun preservesCodeAndDecodesEntitiesOnce() {
        val answer = html("<p>A <strong>B <em>C</em></strong> <code>&amp;lt;</code></p><pre><code>  x &lt; y\n\n z\t</code></pre>")
        val pre = answer.nodes.last() as AnswerNode.Element
        val code = pre.children.single() as AnswerNode.Element
        assertEquals("  x < y\n\n z\t", AnswerContentParser.plainText(com.studysmart.app.core.model.FormattedAnswer(code.children)))
        assertEquals("A B C &lt;\n  x < y\n\n z\t\n", AnswerContentParser.plainText(answer))
    }

    @Test fun acceptsNestedListsAndBoundaries() {
        assertEquals("A\nDetail\nB\nC\n", AnswerContentParser.plainText(html("<ol><li>A<p>Detail</p><ul><li>B</li></ul></li><li>C</li></ol>")))
    }

    @Test fun rejectsNoncanonicalAndHostileMarkup() {
        listOf("<script>x</script>", "<p onclick=\"x\">x</p>", "<P>x</P>", "<p>x</P>",
            "<p>x</p >", "< p>x</p>", "<p >x</p>", "<p/>", "<!--x--><p>x</p>",
            "<!DOCTYPE html><p>x</p>", "<?xml?><p>x</p>", "<p><a>x</a></p>",
            "<p><img></img></p>", "<p><svg>x</svg></p>", "<p>x<br/>y</p>").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { html(it) }
        }
    }

    @Test fun rejectsInvalidStackGrammar() {
        listOf("<p><strong>x</p>", "<p>x", "</p><p>x</p>", "<p><ul><li>x</li></ul></p>",
            "<li>x</li>", "<ul>x<li>y</li></ul>", "<ul><p>x</p></ul>", "<code>x</code>",
            "<strong>x</strong>", "<pre>x</pre>", "<pre><code>x</code><code>y</code></pre>",
            "<pre> <code>x</code></pre>", "<pre><code>x</code> </pre>",
            "<pre><code><em>x</em></code></pre>", "top<p>x</p>", "<p>x</p>tail").forEach {
            assertThrows(it, IllegalArgumentException::class.java) { html(it) }
        }
    }

    @Test fun rejectsUnsupportedEntitiesAndEmptyContent() {
        listOf("&nbsp;", "&quot;", "&#60;", "&#x3c;", "&amp", "&lt", "&AMP;", "A & B", "x > y", "x < y").forEach {
            assertThrows(IllegalArgumentException::class.java) { html("<p>$it</p>") }
        }
        listOf("", " ", "<p> </p>", "<ul></ul>", "<ol><li> </li></ol>",
            "<pre><code>\n  </code></pre>", "<p><em> </em>x</p>", "<p>\u0000x</p>").forEach {
            assertThrows(IllegalArgumentException::class.java) { html(it) }
        }
    }

    @Test fun enforcesBoundsAndExplicitFormats() {
        assertThrows(IllegalArgumentException::class.java) { html("<p>" + "<em>".repeat(32) + "x" + "</em>".repeat(32) + "</p>") }
        assertThrows(IllegalArgumentException::class.java) { html("x".repeat(AnswerContentParser.MAX_INPUT_LENGTH + 1)) }
        assertEquals("<p>x</p>", AnswerContentParser.plainText(AnswerContentParser.parse("<p>x</p>", AnswerFormat.PLAIN_TEXT)))
        assertEquals(AnswerFormat.HTML, AnswerFormat.fromStoredValue("html"))
        assertEquals(AnswerFormat.PLAIN_TEXT, AnswerFormat.fromStoredValue("plain_text"))
        assertThrows(IllegalArgumentException::class.java) { AnswerFormat.fromStoredValue("HTML") }
    }
}

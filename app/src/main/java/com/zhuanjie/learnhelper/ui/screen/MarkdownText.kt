package com.zhuanjie.learnhelper.ui.screen

import android.graphics.Typeface
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.linkify.LinkifyPlugin

/**
 * Markdown renderer using Markwon.
 *
 * Streaming buffer strategy:
 * - [bufferedText] is the stable prefix to render as Markdown (up to last complete block boundary)
 * - [tailText] is the unstable tail appended as plain text (avoids re-parsing incomplete Markdown)
 *
 * Use [splitForStreaming] to compute the split from raw streaming content.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSizeSp = style.fontSize.value

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    val spanned = remember(text) { markwon.toMarkdown(text) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(true)
                textSize = textSizeSp
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                typeface = Typeface.DEFAULT
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            markwon.setParsedMarkdown(tv, spanned)
        }
    )
}

/**
 * Streaming-aware Markdown: renders the stable prefix as Markdown and appends the tail as plain text.
 * This avoids re-parsing the entire content on every token.
 */
@Composable
fun StreamingMarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val context = LocalContext.current
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSizeSp = style.fontSize.value

    val markwon = remember(context) {
        Markwon.builder(context)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(context))
            .usePlugin(LinkifyPlugin.create())
            .build()
    }

    // Split into stable markdown prefix + unstable plain text tail
    val (stableText, tailText) = remember(text) { splitForStreaming(text) }
    val spanned = remember(stableText) { markwon.toMarkdown(stableText) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                setTextIsSelectable(false) // disable during streaming for performance
                textSize = textSizeSp
                setTextColor(textColor)
                setLinkTextColor(linkColor)
                typeface = Typeface.DEFAULT
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            if (tailText.isEmpty()) {
                markwon.setParsedMarkdown(tv, spanned)
            } else {
                // Append tail as plain text after the rendered markdown
                val combined = android.text.SpannableStringBuilder(spanned)
                combined.append(tailText)
                tv.text = combined
            }
        }
    )
}

/**
 * Split streaming text into (stablePrefix, unstableTail).
 *
 * Strategy: find the last complete "block boundary" — a blank line (\n\n),
 * the end of a code block (```), or the end of a complete line that isn't
 * in the middle of an incomplete code fence.
 *
 * The stable prefix is rendered as Markdown (re-parsed only when it changes).
 * The tail is appended as plain text (cheap, updates every token).
 */
private fun splitForStreaming(text: String): Pair<String, String> {
    if (text.length < 80) return "" to text // too short, just show as plain text

    // Check if we're inside an unclosed code block
    val fenceCount = text.indices.count {
        it + 2 < text.length && text[it] == '`' && text[it + 1] == '`' && text[it + 2] == '`' &&
                (it == 0 || text[it - 1] == '\n')
    }
    val inCodeBlock = fenceCount % 2 != 0

    if (inCodeBlock) {
        // Inside an unclosed code block: find the last line before the code block started
        val lastFence = text.lastIndexOf("\n```")
        if (lastFence > 0) {
            // Find a paragraph boundary before the code fence
            val boundary = text.lastIndexOf("\n\n", lastFence)
            if (boundary > 0) {
                return text.substring(0, boundary) to text.substring(boundary)
            }
        }
        return "" to text
    }

    // Not in code block: find the last paragraph boundary (double newline)
    val lastDoubleNewline = text.lastIndexOf("\n\n")
    if (lastDoubleNewline > text.length / 3) {
        return text.substring(0, lastDoubleNewline) to text.substring(lastDoubleNewline)
    }

    // Fallback: find the last complete line
    val lastNewline = text.lastIndexOf('\n')
    if (lastNewline > text.length / 3) {
        return text.substring(0, lastNewline) to text.substring(lastNewline)
    }

    return "" to text
}

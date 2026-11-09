package com.mailtabs.app.data.mail

import android.text.Html
import android.util.Base64
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.internet.ContentType
import javax.mail.internet.MimePart
import javax.mail.internet.MimeUtility
import java.io.InputStream
import java.nio.charset.Charset

object MailParser {

    data class Parsed(val html: String?, val text: String?, val attachments: List<ParsedAttachment>)
    data class ParsedAttachment(val path: String, val fileName: String, val mimeType: String, val size: Long)

    private class State {
        var html: String? = null
        var text: String? = null
        val attachments = mutableListOf<ParsedAttachment>()
        val cid = mutableMapOf<String, String>()
    }

    fun parse(message: Part): Parsed {
        val st = State()
        walk(message, "", st, 0)
        var html = st.html
        if (html != null) {
            for ((cid, dataUri) in st.cid) html = html!!.replace("cid:$cid", dataUri)
        }
        return Parsed(html, st.text, st.attachments)
    }

    private fun walk(p: Part, path: String, st: State, depth: Int) {
        if (depth > 10) return
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as? Multipart ?: return
            for (i in 0 until mp.count) {
                walk(mp.getBodyPart(i), if (path.isEmpty()) "$i" else "$path.$i", st, depth + 1)
            }
            return
        }
        val disposition = p.disposition?.lowercase()
        val fileName = fileName(p)
        val mime = baseType(p)
        val cid = (p as? MimePart)?.contentID?.trim('<', '>', ' ')

        // Встроенные картинки (cid:) подставляем в HTML как data: URI
        if (cid != null && mime.startsWith("image/") && disposition != "attachment") {
            val bytes = runCatching { p.inputStream.use { it.readBytes() } }.getOrNull()
            if (bytes != null && bytes.size < 2_000_000) {
                st.cid[cid] = "data:$mime;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
                return
            }
        }

        val isAttachment = disposition == "attachment" ||
            mime == "message/rfc822" ||
            (fileName != null && mime != "text/plain" && mime != "text/html")

        if (!isAttachment && mime == "text/html" && st.html == null) {
            st.html = contentString(p); return
        }
        if (!isAttachment && mime == "text/plain" && st.text == null) {
            st.text = contentString(p); return
        }
        if (isAttachment || fileName != null) {
            st.attachments += ParsedAttachment(path, fileName ?: "attachment", mime, approxSize(p))
        }
    }

    /** Находит часть по пути вида "1.0". Пустой путь — само письмо. */
    fun findPart(message: Part, path: String): Part {
        if (path.isEmpty()) return message
        var cur: Part = message
        for (idx in path.split('.')) {
            cur = (cur.content as Multipart).getBodyPart(idx.toInt())
        }
        return cur
    }

    fun preview(message: Part): String =
        (findText(message, 0) ?: "").replace(Regex("\\s+"), " ").trim().take(200)

    fun hasAttachments(message: Part): Boolean = runCatching { containsAttachment(message, 0) }.getOrDefault(false)

    private fun containsAttachment(p: Part, depth: Int): Boolean {
        if (depth > 6) return false
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as? Multipart ?: return false
            for (i in 0 until mp.count) if (containsAttachment(mp.getBodyPart(i), depth + 1)) return true
            return false
        }
        return p.disposition.equals(Part.ATTACHMENT, ignoreCase = true)
    }

    private fun findText(p: Part, depth: Int): String? {
        if (depth > 6) return null
        if (p.isMimeType("multipart/alternative")) {
            val mp = p.content as? Multipart ?: return null
            for (i in 0 until mp.count) {
                val bp = mp.getBodyPart(i)
                if (bp.isMimeType("text/plain")) return contentString(bp)
            }
            for (i in 0 until mp.count) findText(mp.getBodyPart(i), depth + 1)?.let { return it }
            return null
        }
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as? Multipart ?: return null
            for (i in 0 until mp.count) findText(mp.getBodyPart(i), depth + 1)?.let { return it }
            return null
        }
        if (p.disposition.equals(Part.ATTACHMENT, ignoreCase = true)) return null
        if (p.isMimeType("text/plain")) return contentString(p)
        if (p.isMimeType("text/html")) return htmlToText(contentString(p))
        return null
    }

    fun htmlToText(html: String): String {
        val cleaned = html
            .replace(Regex("(?is)<head.*?</head>"), "")
            .replace(Regex("(?is)<style.*?</style>"), "")
            .replace(Regex("(?is)<script.*?</script>"), "")
        @Suppress("DEPRECATION")
        val text = if (android.os.Build.VERSION.SDK_INT >= 24) {
            Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
        } else {
            Html.fromHtml(cleaned).toString()
        }
        return text.replace('￼', ' ').trim()
    }

    private fun contentString(p: Part): String {
        return try {
            when (val c = p.content) {
                is String -> c
                is InputStream -> c.use { String(it.readBytes(), charsetOf(p)) }
                else -> c.toString()
            }
        } catch (e: Exception) {
            // Неизвестная кодировка и т.п. — читаем как UTF-8
            runCatching { p.inputStream.use { String(it.readBytes(), Charsets.UTF_8) } }.getOrDefault("")
        }
    }

    private fun charsetOf(p: Part): Charset = runCatching {
        val cs = ContentType(p.contentType).getParameter("charset")
        Charset.forName(MimeUtility.javaCharset(cs))
    }.getOrDefault(Charsets.UTF_8)

    private fun baseType(p: Part): String =
        runCatching { ContentType(p.contentType).baseType.lowercase() }.getOrDefault("application/octet-stream")

    private fun fileName(p: Part): String? =
        runCatching { p.fileName?.let { MimeUtility.decodeText(it) } }.getOrNull()

    private fun approxSize(p: Part): Long {
        val size = p.size.toLong()
        if (size <= 0) return 0
        val enc = (p as? MimePart)?.encoding?.lowercase()
        return if (enc == "base64") size * 3 / 4 else size
    }
}

package com.mailtabs.app.data.mail

import com.mailtabs.app.data.ComposeAttachment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Properties
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class MimeComposerTest {

    private val session = Session.getInstance(Properties())

    private fun attachment(name: String, mime: String, bytes: ByteArray): ComposeAttachment {
        val f = File.createTempFile("att", ".bin").apply { writeBytes(bytes); deleteOnExit() }
        return ComposeAttachment(id = name, name = name, mimeType = mime, size = f.length(), path = f.absolutePath)
    }

    private fun roundTrip(m: MimeMessage): Pair<String, MimeMessage> {
        val raw = ByteArrayOutputStream().also { m.writeTo(it) }.toByteArray()
        return String(raw, Charsets.US_ASCII) to MimeMessage(session, ByteArrayInputStream(raw))
    }

    @Test
    fun twoAttachments_multipartMixed_withRfc2231CyrillicName() {
        val pdf = ByteArray(3000) { (it % 251).toByte() }
        val txt = "Привет, мир".toByteArray(Charsets.UTF_8)
        val msg = MimeComposer.build(
            session = session,
            from = InternetAddress("me@example.org", "Я", "UTF-8"),
            to = arrayOf(InternetAddress("me@example.org")),
            cc = emptyArray(),
            subject = "Тест вложений",
            body = "Текст письма",
            attachments = listOf(
                attachment("report.pdf", "application/pdf", pdf),
                attachment("Отчёт за ноябрь.txt", "text/plain", txt),
            ),
        )
        val (raw, parsed) = roundTrip(msg)

        assertTrue(parsed.isMimeType("multipart/mixed"))
        val mp = parsed.content as MimeMultipart
        assertEquals(3, mp.count)
        assertTrue(mp.getBodyPart(0).isMimeType("text/plain"))
        assertEquals("Текст письма", mp.getBodyPart(0).content)

        val a1 = mp.getBodyPart(1)
        assertEquals(Part.ATTACHMENT, a1.disposition)
        assertEquals("report.pdf", a1.fileName)
        assertTrue(a1.isMimeType("application/pdf"))
        assertTrue(pdf.contentEquals(a1.inputStream.readBytes()))

        val a2 = mp.getBodyPart(2)
        assertEquals(Part.ATTACHMENT, a2.disposition)
        assertEquals("Отчёт за ноябрь.txt", a2.fileName)
        assertTrue(a2.isMimeType("text/plain"))
        assertTrue(txt.contentEquals(a2.inputStream.readBytes()))

        // RFC 2231: filename*=UTF-8''... ; не RFC 2047 (=?UTF-8?...?=) внутри параметра.
        assertTrue(raw, raw.contains("filename*=UTF-8''%D0%9E%D1%82%D1%87%D1%91%D1%82%20"))
        assertTrue(raw, !raw.contains("filename=\"=?"))
        assertEquals("Тест вложений", parsed.subject)
    }

    @Test
    fun noAttachments_plainText() {
        val msg = MimeComposer.build(
            session, InternetAddress("me@example.org"), arrayOf(InternetAddress("you@example.org")), emptyArray(),
            "s", "body",
        )
        val (_, parsed) = roundTrip(msg)
        assertTrue(parsed.isMimeType("text/plain"))
    }
}

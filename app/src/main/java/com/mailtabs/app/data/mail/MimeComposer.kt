package com.mailtabs.app.data.mail

import com.mailtabs.app.data.ComposeAttachment
import java.util.Date
import javax.mail.Message
import javax.mail.Part
import javax.mail.Session
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

/** Сборка исходящего письма (без Android-зависимостей — покрыто unit-тестом). */
object MimeComposer {

    init {
        // Имена вложений с кириллицей: filename*=UTF-8''... (RFC 2231), а не RFC 2047 внутри параметра.
        System.setProperty("mail.mime.encodeparameters", "true")
        System.setProperty("mail.mime.encodefilename", "false")
        System.setProperty("mail.mime.decodeparameters", "true")
    }

    /**
     * Без вложений — простой text/plain; с вложениями — multipart/mixed:
     * текст первой частью, затем файлы с MIME-типом и именем.
     */
    fun build(
        session: Session,
        from: InternetAddress,
        to: Array<InternetAddress>,
        cc: Array<InternetAddress>,
        subject: String,
        body: String,
        inReplyTo: String? = null,
        references: String? = null,
        attachments: List<ComposeAttachment> = emptyList(),
    ): MimeMessage {
        val mime = MimeMessage(session)
        mime.setFrom(from)
        if (to.isNotEmpty()) mime.setRecipients(Message.RecipientType.TO, to)
        if (cc.isNotEmpty()) mime.setRecipients(Message.RecipientType.CC, cc)
        mime.setSubject(subject, "UTF-8")
        mime.sentDate = Date()
        inReplyTo?.let { mime.setHeader("In-Reply-To", it) }
        references?.let { mime.setHeader("References", it) }

        if (attachments.isEmpty()) {
            mime.setText(body, "UTF-8")
        } else {
            val mp = MimeMultipart("mixed")
            mp.addBodyPart(MimeBodyPart().apply { setText(body, "UTF-8") })
            attachments.forEach { a ->
                mp.addBodyPart(MimeBodyPart().apply {
                    attachFile(a.file, a.mimeType, "base64")
                    disposition = Part.ATTACHMENT
                    fileName = a.name
                })
            }
            mime.setContent(mp)
        }
        mime.saveChanges()
        return mime
    }
}

package com.mailtabs.app.data.mail

import com.mailtabs.app.data.db.AccountEntity
import com.mailtabs.app.data.db.Security
import org.eclipse.angus.mail.imap.IMAPStore
import jakarta.mail.Session
import jakarta.mail.Transport
import java.util.Properties

object MailSessions {

    fun connectImap(account: AccountEntity, password: String, readTimeoutMs: Int = 60_000): IMAPStore {
        val proto = if (account.imapSecurity == Security.SSL) "imaps" else "imap"
        val p = Properties()
        p["mail.store.protocol"] = proto
        p["mail.$proto.host"] = account.imapHost
        p["mail.$proto.port"] = account.imapPort.toString()
        p["mail.$proto.connectiontimeout"] = "20000"
        p["mail.$proto.timeout"] = readTimeoutMs.toString()
        p["mail.$proto.fetchsize"] = "262144"
        // Чтение тела не должно ставить \Seen — флагом управляем сами.
        p["mail.$proto.peek"] = "true"
        when (account.imapSecurity) {
            Security.SSL -> {
                p["mail.$proto.ssl.enable"] = "true"
                p["mail.$proto.ssl.checkserveridentity"] = "true"
            }
            Security.STARTTLS -> {
                p["mail.$proto.starttls.enable"] = "true"
                p["mail.$proto.starttls.required"] = "true"
                p["mail.$proto.ssl.checkserveridentity"] = "true"
            }
            Security.NONE -> Unit
        }
        val session = Session.getInstance(p)
        val store = session.getStore(proto) as IMAPStore
        store.connect(account.imapHost, account.imapPort, account.username, password)
        return store
    }

    fun smtpProtocol(account: AccountEntity) = if (account.smtpSecurity == Security.SSL) "smtps" else "smtp"

    fun smtpSession(account: AccountEntity): Session {
        val proto = smtpProtocol(account)
        val p = Properties()
        p["mail.transport.protocol"] = proto
        p["mail.$proto.host"] = account.smtpHost
        p["mail.$proto.port"] = account.smtpPort.toString()
        p["mail.$proto.auth"] = "true"
        p["mail.$proto.connectiontimeout"] = "20000"
        p["mail.$proto.timeout"] = "60000"
        when (account.smtpSecurity) {
            Security.SSL -> {
                p["mail.$proto.ssl.enable"] = "true"
                p["mail.$proto.ssl.checkserveridentity"] = "true"
            }
            Security.STARTTLS -> {
                p["mail.$proto.starttls.enable"] = "true"
                p["mail.$proto.starttls.required"] = "true"
                p["mail.$proto.ssl.checkserveridentity"] = "true"
            }
            Security.NONE -> Unit
        }
        return Session.getInstance(p)
    }

    fun connectSmtp(account: AccountEntity, password: String, session: Session = smtpSession(account)): Transport {
        val t = session.getTransport(smtpProtocol(account))
        t.connect(account.smtpHost, account.smtpPort, account.username, password)
        return t
    }
}

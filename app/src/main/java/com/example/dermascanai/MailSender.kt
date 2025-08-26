package com.example.dermascanai

import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

class MailSender(private val user: String, private val password: String) {

    fun sendMail(to: String, subject: String, message: String) {
        val props = Properties().apply {
            put("mail.smtp.host", "smtp.gmail.com")
            put("mail.smtp.socketFactory.port", "465")
            put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory")
            put("mail.smtp.auth", "true")
            put("mail.smtp.port", "465")
        }

        val session = Session.getInstance(props, object : Authenticator() {
            override fun getPasswordAuthentication(): PasswordAuthentication {
                return PasswordAuthentication(user, password)
            }
        })

        try {
            val mimeMessage = MimeMessage(session)
            mimeMessage.setFrom(InternetAddress(user))
            mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
            mimeMessage.subject = subject
            mimeMessage.setText(message)

            Transport.send(mimeMessage)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

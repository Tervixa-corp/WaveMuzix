package com.example.wavemuzix.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import javax.mail.*
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

object GmailSender {

    private const val GMAIL_SMTP_HOST = "smtp.gmail.com"
    private const val GMAIL_SMTP_PORT = "587"

    suspend fun sendOtpEmail(recipientEmail: String, otp: String) {
        withContext(Dispatchers.IO) {
            val senderEmail = com.example.wavemuzix.BuildConfig.GMAIL_SENDER_EMAIL
            // Clean the app password (remove spaces if any) as Google generates them in 4x4 blocks
            val appPassword = com.example.wavemuzix.BuildConfig.GMAIL_APP_PASSWORD.replace(" ", "")

            if (appPassword.isEmpty() || senderEmail.isEmpty()) {
                throw Exception("Gmail credentials are missing. Please add GMAIL_SENDER_EMAIL and GMAIL_APP_PASSWORD to local.properties")
            }

            if (appPassword.length != 16) {
                throw Exception("Invalid Gmail App Password. It must be a 16-character code (check for missing characters or incorrect copying).")
            }

            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.starttls.required", "true")
                put("mail.smtp.host", GMAIL_SMTP_HOST)
                put("mail.smtp.port", GMAIL_SMTP_PORT)
                put("mail.smtp.ssl.protocols", "TLSv1.2")
                // Set timeouts to prevent hanging
                put("mail.smtp.connectiontimeout", "10000")
                put("mail.smtp.timeout", "10000")
            }

            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    return PasswordAuthentication(senderEmail, appPassword)
                }
            })

            try {
                val htmlContent = """
                    <div style="font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; line-height: 1.6; color: #333; max-width: 600px; margin: auto; border: 1px solid #e0e0e0; padding: 20px; border-radius: 10px;">
                        <h2 style="color: #1a73e8; text-align: center;">Verify Your Identity</h2>
                        <p>Hello,</p>
                        <p>Thank you for choosing <strong>Lumino Global</strong>. We received a request to create an account or sign in using this email address. To ensure the security of your account, please use the following One-Time Password (OTP) to complete your verification:</p>
                        
                        <div style="background-color: #f8f9fa; padding: 20px; text-align: center; font-size: 32px; font-weight: bold; letter-spacing: 8px; color: #202124; border: 1px dashed #1a73e8; border-radius: 8px; margin: 25px 0;">
                            $otp
                        </div>
                        
                        <p><strong>Note:</strong> This code is valid for <strong>10 minutes</strong>. For your protection, please do not share this code with anyone. <strong>Lumino Global</strong> staff will never ask for your password or OTP over the phone or via email.</p>
                        
                        <hr style="border: none; border-top: 1px solid #eee; margin: 30px 0;" />
                        
                        <div style="font-size: 12px; color: #70757a; line-height: 1.4;">
                            <p><strong>Why am I receiving this email?</strong><br>
                            This security verification was triggered because someone entered this email address (<strong>$recipientEmail</strong>) on the Lumino Global platform. This step is mandatory to prevent unauthorized account creation and maintain a secure community.</p>
                            
                            <p><strong>Didn't request this code?</strong><br>
                            If you did not initiate this request, you can safely ignore this message. It is possible that another user mistyped their email address. No further action is required from your side, and your account/email remains secure.</p>
                            
                            <p style="text-align: center; margin-top: 20px;">
                                &copy; ${Calendar.getInstance().get(Calendar.YEAR)} <strong>Lumino Global</strong>. Empowering your digital experience.
                            </p>
                        </div>
                    </div>
                """.trimIndent()

                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(senderEmail, "LuminoSupport-(WaveMuzix)"))
                    setRecipients(Message.RecipientType.TO, InternetAddress.parse(recipientEmail))
                    setSubject("Lumino Verification")
                    setContent(htmlContent, "text/html; charset=utf-8")
                }

                Transport.send(message)
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        }
    }
}

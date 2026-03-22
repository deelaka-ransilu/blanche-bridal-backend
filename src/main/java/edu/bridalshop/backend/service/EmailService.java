package edu.bridalshop.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${frontend.url}")
    private String frontendUrl;

    // ── Email verification ─────────────────────────────────────────────
    @Async
    public void sendVerificationEmail(String toEmail,
                                      String fullName,
                                      String token) {
        String link = frontendUrl + "/verify-email?token=" + token;

        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #c084a0;">Welcome to Blanche Bridal, %s!</h2>
                    <p>Thank you for registering. Please verify your email address by clicking the button below.</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #c084a0; color: white; padding: 12px 30px;
                                  text-decoration: none; border-radius: 5px; font-size: 16px;">
                            Verify Email Address
                        </a>
                    </div>
                    <p style="color: #666; font-size: 14px;">This link expires in 24 hours.</p>
                    <p style="color: #666; font-size: 14px;">If you did not create an account, you can safely ignore this email.</p>
                </div>
                """.formatted(fullName, link);

        sendEmail(toEmail, "Verify your email — Blanche Bridal", html);
    }

    // ── Password reset ─────────────────────────────────────────────────
    @Async
    public void sendPasswordResetEmail(String toEmail,
                                       String fullName,
                                       String token) {
        String link = frontendUrl + "/reset-password?token=" + token;

        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;">
                    <h2 style="color: #c084a0;">Password Reset Request</h2>
                    <p>Hi %s,</p>
                    <p>We received a request to reset your password. Click the button below to proceed.</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s"
                           style="background-color: #c084a0; color: white; padding: 12px 30px;
                                  text-decoration: none; border-radius: 5px; font-size: 16px;">
                            Reset Password
                        </a>
                    </div>
                    <p style="color: #666; font-size: 14px;">This link expires in 1 hour.</p>
                    <p style="color: #666; font-size: 14px;">If you did not request a password reset, you can safely ignore this email.</p>
                </div>
                """.formatted(fullName, link);

        sendEmail(toEmail, "Reset your password — Blanche Bridal", html);
    }

    // ── Internal helper ────────────────────────────────────────────────
    private void sendEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send email: " + e.getMessage());
        }
    }
}
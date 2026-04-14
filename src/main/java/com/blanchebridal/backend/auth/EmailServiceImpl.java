package com.blanchebridal.backend.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        String link = frontendUrl + "/verify-email?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Verify your Blanche Bridal account");
        message.setText(
                "Welcome to Blanche Bridal!\n\n" +
                        "Please verify your email address by clicking the link below.\n" +
                        "This link expires in 5 minutes.\n\n" +
                        link + "\n\n" +
                        "If you did not create an account, you can ignore this email."
        );

        mailSender.send(message);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Reset your Blanche Bridal password");
        message.setText(
                "You requested a password reset for your Blanche Bridal account.\n\n" +
                        "Click the link below to set a new password.\n" +
                        "This link expires in 1 hour.\n\n" +
                        link + "\n\n" +
                        "If you did not request this, you can safely ignore this email."
        );

        mailSender.send(message);
    }
}
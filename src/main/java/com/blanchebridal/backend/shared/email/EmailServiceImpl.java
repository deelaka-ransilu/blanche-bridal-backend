package com.blanchebridal.backend.shared.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy");

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = frontendUrl + "/verify-email?token=" + encodedToken;

        String html = """
                <!DOCTYPE html>
   Succession Main Theme - 1 HOUR (EXTENDED Version - Piano + Strings + 808 + Beat)             <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px; text-align:center;">
                        <h1 style="color:#8b5e57; margin-bottom:10px;">Welcome to Blanche Bridal</h1>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Please verify your email address by clicking the button below.
                        </p>

                        <a href="%s"
                           style="display:inline-block; margin-top:20px; padding:14px 28px; background-color:#8b5e57; color:#ffffff; text-decoration:none; border-radius:8px; font-weight:bold;">
                            Verify Email
                        </a>

                        <p style="color:#444444; font-size:15px; line-height:1.6; margin-top:24px;">
                            This link expires in 20 minutes.
                        </p>

                        <p style="font-size:13px; color:#999999; margin-top:30px;">
                            If you did not create an account, you can ignore this email.
                        </p>

                        <p style="font-size:14px; color:#8b5e57; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(link);

        sendHtmlEmail(toEmail, "Verify your Blanche Bridal account", html);
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = frontendUrl + "/reset-password?token=" + encodedToken;

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px; text-align:center;">
                        <h1 style="color:#8b5e57; margin-bottom:10px;">Reset Your Password</h1>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            You requested a password reset for your Blanche Bridal account.
                        </p>

                        <a href="%s"
                           style="display:inline-block; margin-top:20px; padding:14px 28px; background-color:#8b5e57; color:#ffffff; text-decoration:none; border-radius:8px; font-weight:bold;">
                            Reset Password
                        </a>

                        <p style="color:#444444; font-size:15px; line-height:1.6; margin-top:24px;">
                            This link expires in 1 hour.
                        </p>

                        <p style="font-size:13px; color:#999999; margin-top:30px;">
                            If you did not request this, you can safely ignore this email.
                        </p>

                        <p style="font-size:14px; color:#8b5e57; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(link);

        sendHtmlEmail(toEmail, "Reset your Blanche Bridal password", html);
    }

    @Override
    public void sendOrderConfirmationEmail(String toEmail,
                                           String customerName,
                                           String orderId,
                                           BigDecimal totalAmount,
                                           List<String> itemSummaries) {

        StringBuilder itemHtml = new StringBuilder();

        if (itemSummaries != null && !itemSummaries.isEmpty()) {
            for (String item : itemSummaries) {
                itemHtml.append("""
                        <tr>
                            <td style="padding:10px 0; border-bottom:1px solid #eeeeee; color:#444444;">
                                %s
                            </td>
                        </tr>
                        """.formatted(escapeHtml(item)));
            }
        } else {
            itemHtml.append("""
                    <tr>
                        <td style="padding:10px 0; color:#444444;">
                            No item details available.
                        </td>
                    </tr>
                    """);
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                        <h1 style="color:#8b5e57; text-align:center;">Order Confirmed</h1>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Dear %s,
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Your order has been confirmed. Here's a summary:
                        </p>

                        <p style="color:#8b5e57; font-weight:bold;">
                            Order ID: #%s
                        </p>

                        <table style="width:100%%; border-collapse:collapse; margin-top:15px;">
                            %s
                        </table>

                        <p style="font-size:18px; color:#222222; font-weight:bold; margin-top:24px;">
                            Total: LKR %s
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Thank you for shopping with Blanche Bridal.
                        </p>

                        <p style="font-size:14px; color:#8b5e57; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(orderId),
                itemHtml,
                totalAmount
        );

        sendHtmlEmail(toEmail, "Your Blanche Bridal order is confirmed - #" + orderId, html);
    }

    @Override
    public void sendAppointmentConfirmationEmail(String toEmail,
                                                 String customerName,
                                                 LocalDate appointmentDate,
                                                 String timeSlot,
                                                 String appointmentType,
                                                 String productName) {

        String formattedType = formatType(appointmentType);
        String dateStr = appointmentDate.format(DATE_FORMAT);

        String productHtml = "";
        if (productName != null && !productName.isBlank()) {
            productHtml = """
                    <p style="color:#444444; font-size:16px;">
                        <strong>For:</strong> %s
                    </p>
                    """.formatted(escapeHtml(productName));
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                        <h1 style="color:#8b5e57; text-align:center;">Appointment Confirmed</h1>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Dear %s,
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Your %s appointment has been confirmed.
                        </p>

                        <div style="background-color:#f8f3f0; padding:18px; border-radius:10px; margin:20px 0;">
                            <p style="color:#444444; font-size:16px;">
                                <strong>Date:</strong> %s
                            </p>
                            <p style="color:#444444; font-size:16px;">
                                <strong>Time:</strong> %s
                            </p>
                            %s
                        </div>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            We look forward to seeing you. Please arrive 5 minutes before your appointment time.
                        </p>

                        <p style="font-size:14px; color:#8b5e57; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(formattedType),
                escapeHtml(dateStr),
                escapeHtml(timeSlot),
                productHtml
        );

        sendHtmlEmail(
                toEmail,
                "Appointment confirmed - " + formattedType + " on " + dateStr + " at " + timeSlot,
                html
        );
    }

    @Override
    public void sendAppointmentReminderEmail(String toEmail,
                                             String customerName,
                                             LocalDate appointmentDate,
                                             String timeSlot,
                                             String appointmentType) {

        String formattedType = formatType(appointmentType);
        String dateStr = appointmentDate.format(DATE_FORMAT);

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                        <h1 style="color:#8b5e57; text-align:center;">Appointment Reminder</h1>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Dear %s,
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            This is a reminder that you have a %s appointment tomorrow.
                        </p>

                        <div style="background-color:#f8f3f0; padding:18px; border-radius:10px; margin:20px 0;">
                            <p style="color:#444444; font-size:16px;">
                                <strong>Date:</strong> %s
                            </p>
                            <p style="color:#444444; font-size:16px;">
                                <strong>Time:</strong> %s
                            </p>
                        </div>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Please contact us if you need to reschedule.
                        </p>

                        <p style="font-size:14px; color:#8b5e57; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(formattedType),
                escapeHtml(dateStr),
                escapeHtml(timeSlot)
        );

        sendHtmlEmail(toEmail, "Reminder: Your appointment tomorrow at " + timeSlot, html);
    }

    @Override
    public void sendRentalOverdueEmail(String toEmail,
                                       String customerName,
                                       String productName,
                                       LocalDate rentalEnd,
                                       BigDecimal balanceDue) {

        String dateStr = rentalEnd.format(DATE_FORMAT);

        String balanceHtml = "";
        if (balanceDue != null && balanceDue.compareTo(BigDecimal.ZERO) > 0) {
            balanceHtml = """
                    <p style="font-size:17px; color:#b00020; font-weight:bold;">
                        Outstanding balance: LKR %s
                    </p>
                    """.formatted(balanceDue);
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                        <h1 style="color:#b00020; text-align:center;">Rental Return Overdue</h1>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Dear %s,
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Your rental of <strong>%s</strong> was due for return on <strong>%s</strong>.
                        </p>

                        %s

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Please return the item as soon as possible or contact us to arrange.
                        </p>

                        <p style="font-size:14px; color:#8b5e57; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(productName),
                escapeHtml(dateStr),
                balanceHtml
        );

        sendHtmlEmail(toEmail, "Action required: Rental return overdue", html);
    }

    private void sendHtmlEmail(String toEmail, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    private String formatType(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }

        String[] parts = type.split("_");
        StringBuilder sb = new StringBuilder();

        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }

            if (!sb.isEmpty()) {
                sb.append(" ");
            }

            sb.append(part.substring(0, 1).toUpperCase())
                    .append(part.substring(1).toLowerCase());
        }

        return sb.toString();
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
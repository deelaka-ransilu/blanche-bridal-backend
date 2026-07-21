package com.blanchebridal.backend.shared.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

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

    // Single brand color used across every email template — headers,
    // buttons, links, and the footer signature all use this so every
    // email looks consistent regardless of which flow triggered it.
    private static final String BRAND_COLOR = "#c8102e";

    @Async
    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = frontendUrl + "/verify-email?token=" + encodedToken;

        String html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px; text-align:center;">
                    <h1 style="color:%s; margin-bottom:10px;">Welcome to Blanche Bridal</h1>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        Please verify your email address by clicking the button below.
                    </p>

                    <a href="%s"
                       style="display:inline-block; margin-top:20px; padding:14px 28px; background-color:%s; color:#ffffff; text-decoration:none; border-radius:8px; font-weight:bold;">
                        Verify Email
                    </a>

                    <p style="color:#444444; font-size:15px; line-height:1.6; margin-top:24px;">
                        This link expires in 24 hours.
                    </p>

                    <p style="font-size:13px; color:#999999; margin-top:30px;">
                        If you did not create an account, you can ignore this email.
                    </p>

                    <p style="font-size:14px; color:%s; margin-top:28px;">
                        Blanche Bridal
                    </p>
                </div>
            </body>
            </html>
            """.formatted(BRAND_COLOR, link, BRAND_COLOR, BRAND_COLOR);

        sendHtmlEmail(toEmail, "Verify your Blanche Bridal account", html);
    }

    @Async
    @Override
    public void sendPasswordResetEmail(String toEmail, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = frontendUrl + "/reset-password?token=" + encodedToken;

        String html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px; text-align:center;">
                    <h1 style="color:%s; margin-bottom:10px;">Reset Your Password</h1>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        You requested a password reset for your Blanche Bridal account.
                    </p>

                    <a href="%s"
                       style="display:inline-block; margin-top:20px; padding:14px 28px; background-color:%s; color:#ffffff; text-decoration:none; border-radius:8px; font-weight:bold;">
                        Reset Password
                    </a>

                    <p style="color:#444444; font-size:15px; line-height:1.6; margin-top:24px;">
                        This link expires in 1 hour.
                    </p>

                    <p style="font-size:13px; color:#999999; margin-top:30px;">
                        If you did not request this, you can safely ignore this email.
                    </p>

                    <p style="font-size:14px; color:%s; margin-top:28px;">
                        Blanche Bridal
                    </p>
                </div>
            </body>
            </html>
            """.formatted(BRAND_COLOR, link, BRAND_COLOR, BRAND_COLOR);

        sendHtmlEmail(toEmail, "Reset your Blanche Bridal password", html);
    }

    @Async
    @Override
    public void sendOrderConfirmationEmail(String toEmail,
                                           String customerName,
                                           String orderId,
                                           BigDecimal totalAmount,
                                           List<String> itemSummaries,
                                           byte[] receiptPdfBytes) {

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

        String receiptNoteHtml = receiptPdfBytes != null
                ? """
                  <p style="color:#444444; font-size:15px; line-height:1.6;">
                      A receipt for this payment is attached as a PDF.
                  </p>
                  """
                : "";

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                        <h1 style="color:%s; text-align:center;">Order Confirmed</h1>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Dear %s,
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Your order has been confirmed. Here's a summary:
                        </p>

                        <p style="color:%s; font-weight:bold;">
                            Order ID: #%s
                        </p>

                        <table style="width:100%%; border-collapse:collapse; margin-top:15px;">
                            %s
                        </table>

                        <p style="font-size:18px; color:#222222; font-weight:bold; margin-top:24px;">
                            Total: LKR %s
                        </p>

                        %s

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Thank you for shopping with Blanche Bridal.
                        </p>

                        <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                BRAND_COLOR,
                escapeHtml(orderId),
                itemHtml,
                totalAmount,
                receiptNoteHtml,
                BRAND_COLOR
        );

        sendHtmlEmailWithAttachment(
                toEmail,
                "Your Blanche Bridal order is confirmed - #" + orderId,
                html,
                receiptPdfBytes,
                "receipt.pdf",
                "application/pdf"
        );
    }

    @Async
    @Override
    public void sendAppointmentConfirmationEmail(String toEmail,
                                                 String customerName,
                                                 UUID appointmentId,
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
                    <h1 style="color:%s; text-align:center;">Appointment Confirmed ✓</h1>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">Dear %s,</p>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        Your <strong>%s</strong> appointment has been confirmed by our team.
                    </p>

                    <div style="background-color:#f8f3f0; padding:18px; border-radius:10px; margin:20px 0;">
                        <p style="color:#444444; font-size:16px; margin:0 0 8px 0;">
                            <strong>Date:</strong> %s
                        </p>
                        <p style="color:#444444; font-size:16px; margin:0 0 8px 0;">
                            <strong>Time:</strong> %s
                        </p>
                        %s
                    </div>

                    <p style="color:#444444; font-size:15px; line-height:1.6;">
                        A calendar invite is attached — open it to add this appointment directly to your Google Calendar.
                    </p>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        Please arrive 5 minutes before your appointment time.
                    </p>

                    <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                        Blanche Bridal Couture
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                escapeHtml(formattedType),
                escapeHtml(dateStr),
                escapeHtml(timeSlot),
                productHtml,
                BRAND_COLOR
        );

        byte[] ical = buildIcalBytes(appointmentId, appointmentDate, timeSlot,
                appointmentType, "CONFIRMED");

        sendHtmlEmailWithIcal(
                toEmail,
                "Appointment confirmed — " + formattedType + " on " + dateStr + " at " + timeSlot,
                html,
                ical
        );
    }

    @Override
    public void sendAppointmentConfirmationEmail(String toEmail, String customerName, LocalDate appointmentDate, String timeSlot, String appointmentType, String productName) {
        EmailService.super.sendAppointmentConfirmationEmail(toEmail, customerName, appointmentDate, timeSlot, appointmentType, productName);
    }

    @Async
    @Override
    public void sendAppointmentBookingReceivedEmail(String toEmail,
                                                    String customerName,
                                                    UUID appointmentId,
                                                    LocalDate appointmentDate,
                                                    String timeSlot,
                                                    String appointmentType,
                                                    String productName) {
        String formattedType = formatType(appointmentType);
        String dateStr = appointmentDate.format(DATE_FORMAT);

        String productHtml = "";
        if (productName != null && !productName.isBlank()) {
            productHtml = """
                <p style="color:#444444; font-size:16px; margin:0 0 8px 0;">
                    <strong>For:</strong> %s
                </p>
                """.formatted(escapeHtml(productName));
        }

        String html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                    <h1 style="color:%s; text-align:center;">Booking Received</h1>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">Dear %s,</p>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        Thank you! We've received your <strong>%s</strong> appointment request.
                        Our team will review it and send you a confirmation shortly.
                    </p>

                    <div style="background-color:#f8f3f0; padding:18px; border-radius:10px; margin:20px 0;">
                        <p style="color:#444444; font-size:16px; margin:0 0 8px 0;">
                            <strong>Date:</strong> %s
                        </p>
                        <p style="color:#444444; font-size:16px; margin:0 0 8px 0;">
                            <strong>Time:</strong> %s
                        </p>
                        %s
                        <p style="color:#888888; font-size:14px; margin:12px 0 0 0;">
                            Status: <span style="color:#b07d00; font-weight:bold;">Pending Confirmation</span>
                        </p>
                    </div>

                    <p style="color:#444444; font-size:15px; line-height:1.6;">
                        A calendar placeholder is attached so you can block the time in your calendar
                        while you wait for confirmation.
                    </p>

                    <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                        Blanche Bridal Couture
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                escapeHtml(formattedType),
                escapeHtml(dateStr),
                escapeHtml(timeSlot),
                productHtml,
                BRAND_COLOR
        );

        byte[] ical = buildIcalBytes(appointmentId, appointmentDate, timeSlot,
                appointmentType, "TENTATIVE");

        sendHtmlEmailWithIcal(
                toEmail,
                "Appointment request received — " + formattedType + " on " + dateStr,
                html,
                ical
        );
    }

    @Async
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
                        <h1 style="color:%s; text-align:center;">Appointment Reminder</h1>

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

                        <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                escapeHtml(formattedType),
                escapeHtml(dateStr),
                escapeHtml(timeSlot),
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Reminder: Your appointment tomorrow at " + timeSlot, html);
    }

    @Async
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
                    <p style="font-size:17px; color:%s; font-weight:bold;">
                        Outstanding balance: LKR %s
                    </p>
                    """.formatted(BRAND_COLOR, balanceDue);
        }

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                        <h1 style="color:%s; text-align:center;">Rental Return Overdue</h1>

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

                        <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                escapeHtml(productName),
                escapeHtml(dateStr),
                balanceHtml,
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Action required: Rental return overdue", html);
    }

    @Async
    @Override
    public void sendAdminWelcomeEmail(String toEmail,
                                      String firstName,
                                      String lastName,
                                      String temporaryPassword) {

        String loginLink = frontendUrl + "/login";

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                        <h1 style="color:%s; text-align:center; margin-bottom:6px;">
                            Welcome to Blanche Bridal
                        </h1>
                        <p style="text-align:center; color:#999999; font-size:14px; margin-top:0;">
                            Admin Account Created
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6; margin-top:24px;">
                            Dear %s %s,
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            An admin account has been created for you on the Blanche Bridal management system.
                            Use the credentials below to sign in.
                        </p>

                        <div style="background-color:#f8f3f0; padding:20px; border-radius:10px; margin:24px 0;">
                            <p style="margin:0 0 10px 0; color:#444444; font-size:15px;">
                                <strong>Email:</strong> %s
                            </p>
                            <p style="margin:0; color:#444444; font-size:15px;">
                                <strong>Temporary password:</strong>
                                <span style="font-family:monospace; background-color:#ffffff; padding:3px 8px; border-radius:4px; border:1px solid #dddddd;">
                                    %s
                                </span>
                            </p>
                        </div>

                        <p style="color:#b05e00; font-size:14px; line-height:1.6;">
                            ⚠ Please change your password after your first login.
                        </p>

                        <div style="text-align:center; margin-top:28px;">
                            <a href="%s"
                               style="display:inline-block; padding:14px 32px; background-color:%s; color:#ffffff;
                                      text-decoration:none; border-radius:8px; font-weight:bold; font-size:15px;">
                                Sign In
                            </a>
                        </div>

                        <p style="font-size:13px; color:#999999; text-align:center; margin-top:30px;">
                            If you did not expect this email, please contact your system administrator.
                        </p>

                        <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                BRAND_COLOR,
                escapeHtml(firstName),
                escapeHtml(lastName),
                escapeHtml(toEmail),
                escapeHtml(temporaryPassword),
                loginLink,
                BRAND_COLOR,
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Your Blanche Bridal admin account is ready", html);
    }

    @Async
    @Override
    public void sendAppointmentRescheduledEmail(String toEmail,
                                                String customerName,
                                                LocalDate newDate,
                                                String newTimeSlot,
                                                String appointmentType) {
        String formattedType = formatType(appointmentType);
        String dateStr = newDate.format(DATE_FORMAT);

        String html = """
        <!DOCTYPE html>
        <html>
        <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
            <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                <h1 style="color:%s; text-align:center;">Appointment Rescheduled</h1>

                <p style="color:#444444; font-size:16px; line-height:1.6;">Dear %s,</p>

                <p style="color:#444444; font-size:16px; line-height:1.6;">
                    Your <strong>%s</strong> appointment has been rescheduled by our team.
                    Here are your updated details:
                </p>

                <div style="background-color:#f8f3f0; padding:18px; border-radius:10px; margin:20px 0;">
                    <p style="color:#444444; font-size:16px; margin:0 0 8px 0;">
                        <strong>New Date:</strong> %s
                    </p>
                    <p style="color:#444444; font-size:16px; margin:0;">
                        <strong>New Time:</strong> %s
                    </p>
                </div>

                <p style="color:#444444; font-size:15px; line-height:1.6;">
                    If you have any questions, please contact us directly.
                </p>

                <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                    Blanche Bridal Couture
                </p>
            </div>
        </body>
        </html>
        """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                escapeHtml(formattedType),
                escapeHtml(dateStr),
                escapeHtml(newTimeSlot),
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Your appointment has been rescheduled — " + dateStr + " at " + newTimeSlot, html);
    }

    @Async
    @Override
    public void sendInquiryReplyEmail(String toEmail, String customerName,
                                      String originalMessage, String replyMessage) {
        String html = """
        <!DOCTYPE html>
        <html>
        <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
            <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                <h1 style="color:%s; text-align:center;">Response to Your Enquiry</h1>

                <p style="color:#444444; font-size:16px; line-height:1.6;">Dear %s,</p>

                <p style="color:#444444; font-size:16px; line-height:1.6;">
                    Thank you for reaching out to Blanche Bridal. Here is our response to your enquiry:
                </p>

                <div style="background-color:#f8f3f0; padding:18px; border-radius:10px; margin:20px 0; border-left:4px solid %s;">
                    <p style="color:#444444; font-size:15px; line-height:1.7; margin:0; white-space:pre-wrap;">%s</p>
                </div>

                <hr style="border:none; border-top:1px solid #eeeeee; margin:24px 0;" />

                <p style="color:#999999; font-size:13px; line-height:1.6;">
                    <strong>Your original message:</strong><br/>
                    %s
                </p>

                <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                    Blanche Bridal Couture
                </p>
            </div>
        </body>
        </html>
        """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                BRAND_COLOR,
                escapeHtml(replyMessage),
                escapeHtml(originalMessage),
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Re: Your Blanche Bridal Enquiry", html);
    }

    @Async
    @Override
    public void sendAppointmentCancelledEmail(String toEmail,
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
            <h1 style="color:%s; text-align:center;">Appointment Cancelled</h1>

            <p style="color:#444444; font-size:16px; line-height:1.6;">Dear %s,</p>

            <p style="color:#444444; font-size:16px; line-height:1.6;">
                Your <strong>%s</strong> appointment scheduled for the details below has been cancelled.
            </p>

            <div style="background-color:#f8f3f0; padding:18px; border-radius:10px; margin:20px 0;">
                <p style="color:#444444; font-size:16px; margin:0 0 8px 0;">
                    <strong>Date:</strong> %s
                </p>
                <p style="color:#444444; font-size:16px; margin:0;">
                    <strong>Time:</strong> %s
                </p>
            </div>

            <p style="color:#444444; font-size:15px; line-height:1.6;">
                If this wasn't expected, or you'd like to book a new time, please contact us or
                book again from your account.
            </p>

            <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                Blanche Bridal Couture
            </p>
        </div>
    </body>
    </html>
    """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                escapeHtml(formattedType),
                escapeHtml(dateStr),
                escapeHtml(timeSlot),
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Your appointment on " + dateStr + " has been cancelled", html);
    }

    @Async
    @Override
    public void sendOrderReadyEmail(String toEmail,
                                    String customerName,
                                    String orderId,
                                    String fulfillmentMethod) {

        boolean isPickup = "PICKUP".equalsIgnoreCase(fulfillmentMethod);

        String headline = isPickup ? "Your Order is Ready for Pickup" : "Your Order is Ready to Ship";
        String bodyText = isPickup
                ? "Great news — your order is packed and ready for you to collect from our store."
                : "Great news — your order is packed and ready to be shipped to your delivery address.";

        String html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                    <h1 style="color:%s; text-align:center;">%s</h1>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        Dear %s,
                    </p>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        %s
                    </p>

                    <p style="color:%s; font-weight:bold;">
                        Order ID: #%s
                    </p>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        %s
                    </p>

                    <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                        Blanche Bridal
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                BRAND_COLOR,
                headline,
                escapeHtml(customerName),
                bodyText,
                BRAND_COLOR,
                escapeHtml(orderId),
                isPickup
                        ? "Please bring a valid ID when collecting your order. We look forward to seeing you."
                        : "You'll receive tracking details separately once the item is handed to the courier.",
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Your Blanche Bridal order is ready - #" + orderId, html);
    }

    @Async
    @Override
    public void sendOrderCancelledEmail(String toEmail,
                                        String customerName,
                                        String fullOrderId,   // full UUID, not truncated
                                        boolean refundOwed) {

        // Display-only short ID, same truncation style used everywhere else
        // (frontend does order.id.slice(0,8).toUpperCase() — mirror it here)
        String displayId = fullOrderId.length() >= 8
                ? fullOrderId.substring(0, 8).toUpperCase()
                : fullOrderId.toUpperCase();

        // Link must use the FULL id — that's what Next.js route /my/orders/[id]
        // actually resolves against, not the shortened display version.
        String refundLink = frontendUrl + "/my/orders/" + fullOrderId;

        String refundHtml = refundOwed
                ? """
              <p style="color:#444444; font-size:16px; line-height:1.6;">
                  A refund is owed for this order. Please submit your bank details so our team
                  can process the transfer.
              </p>
              <div style="text-align:center; margin:20px 0;">
                  <a href="%s"
                     style="display:inline-block; padding:14px 28px; background-color:%s; color:#ffffff;
                            text-decoration:none; border-radius:8px; font-weight:bold; font-size:15px;">
                      Submit Bank Details
                  </a>
              </div>
              """.formatted(refundLink, BRAND_COLOR)
                : """
              <p style="color:#444444; font-size:16px; line-height:1.6;">
                  If a payment was made for this order, any applicable refund will be processed
                  separately and you'll receive a confirmation once it's complete.
              </p>
              """;

        String html = """
        <!DOCTYPE html>
        <html>
        <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
            <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                <h1 style="color:%s; text-align:center;">Order Cancelled</h1>

                <p style="color:#444444; font-size:16px; line-height:1.6;">
                    Dear %s,
                </p>

                <p style="color:#444444; font-size:16px; line-height:1.6;">
                    Your order has been cancelled.
                </p>

                <p style="color:%s; font-weight:bold;">
                    Order ID: #%s
                </p>

                %s

                <p style="color:#444444; font-size:15px; line-height:1.6;">
                    If this wasn't expected, please contact us.
                </p>

                <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                    Blanche Bridal
                </p>
            </div>
        </body>
        </html>
        """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                BRAND_COLOR,
                escapeHtml(displayId),   // short form for display
                refundHtml,
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Your order #" + displayId + " has been cancelled", html);
    }

    // NOTE: this is the ONLY sendRefundProcessedEmail method — the old
    // 5-arg version (no proofImageUrl) was deleted. It's no longer part of
    // the EmailService interface, and leaving it in with @Override would
    // fail to compile ("does not override or implement a method from a
    // supertype"). If anything else in the codebase still calls the 5-arg
    // signature, that call site needs updating to pass proofImageUrl too.
    @Async
    @Override
    public void sendRefundProcessedEmail(String toEmail,
                                         String customerName,
                                         String orderId,
                                         BigDecimal amount,
                                         String reason,
                                         String proofImageUrl) {

        String reasonHtml = (reason != null && !reason.isBlank())
                ? """
              <p style="color:#444444; font-size:15px; line-height:1.6;">
                  <strong>Reason:</strong> %s
              </p>
              """.formatted(escapeHtml(reason))
                : "";

        // Receipt link only rendered when we actually have one — defensive,
        // since RefundServiceImpl already requires proofImageUrl to create
        // a Refund at all, so this should always be present in practice.
        String receiptHtml = (proofImageUrl != null && !proofImageUrl.isBlank())
                ? """
              <div style="text-align:center; margin:20px 0;">
                  <a href="%s"
                     style="display:inline-block; padding:12px 24px; background-color:#ffffff; color:%s;
                            text-decoration:none; border-radius:8px; font-weight:bold; font-size:14px;
                            border:2px solid %s;">
                      View Transfer Receipt
                  </a>
              </div>
              """.formatted(proofImageUrl, BRAND_COLOR, BRAND_COLOR)
                : "";

        String html = """
            <!DOCTYPE html>
            <html>
            <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                    <h1 style="color:%s; text-align:center;">Refund Processed</h1>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        Dear %s,
                    </p>

                    <p style="color:#444444; font-size:16px; line-height:1.6;">
                        A refund has been processed for your order via bank transfer.
                    </p>

                    <p style="color:%s; font-weight:bold;">
                        Order ID: #%s
                    </p>

                    <p style="font-size:18px; color:#222222; font-weight:bold; margin-top:16px;">
                        Refund amount: LKR %s
                    </p>

                    %s

                    %s

                    <p style="color:#444444; font-size:15px; line-height:1.6; margin-top:20px;">
                        A copy of the transfer receipt is on file with our team — you can view it above,
                        or find it any time on your order page. Please allow a few business days for the
                        transfer to reflect in your account.
                    </p>

                    <p style="color:#444444; font-size:15px; line-height:1.6;">
                        If you haven't received it or have any questions, please contact us.
                    </p>

                    <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                        Blanche Bridal
                    </p>
                </div>
            </body>
            </html>
            """.formatted(
                BRAND_COLOR,
                escapeHtml(customerName),
                BRAND_COLOR,
                escapeHtml(orderId),
                amount,
                reasonHtml,
                receiptHtml,
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Refund processed for order #" + orderId, html);
    }

    @Async
    @Override
    public void sendRentalReturnedEmail(String toEmail,
                                        String customerName,
                                        String productName,
                                        LocalDate returnDate,
                                        BigDecimal damageCost,
                                        BigDecimal lateFeeAmount,
                                        BigDecimal securityDepositRefundedAmount,
                                        BigDecimal amountOwedByCustomer) {

        String dateStr = returnDate.format(DATE_FORMAT);

        boolean hasDamage = damageCost != null && damageCost.compareTo(BigDecimal.ZERO) > 0;
        boolean hasLateFee = lateFeeAmount != null && lateFeeAmount.compareTo(BigDecimal.ZERO) > 0;
        boolean owesMore = amountOwedByCustomer != null && amountOwedByCustomer.compareTo(BigDecimal.ZERO) > 0;

        StringBuilder rows = new StringBuilder();

        if (hasDamage) {
            rows.append("""
                    <p style="color:#444444; font-size:15px; margin:0 0 8px 0;">
                        <strong>Damage cost:</strong> LKR %s
                    </p>
                    """.formatted(damageCost));
        }

        if (hasLateFee) {
            rows.append("""
                    <p style="color:#444444; font-size:15px; margin:0 0 8px 0;">
                        <strong>Late return fee:</strong> LKR %s
                    </p>
                    """.formatted(lateFeeAmount));
        }

        if (securityDepositRefundedAmount != null) {
            rows.append("""
                    <p style="color:#444444; font-size:15px; margin:0 0 8px 0;">
                        <strong>Security deposit refunded:</strong> LKR %s
                    </p>
                    """.formatted(securityDepositRefundedAmount));
        }

        String owedHtml = owesMore
                ? """
                  <p style="font-size:17px; color:%s; font-weight:bold; margin-top:16px;">
                      Amount still owed: LKR %s
                  </p>
                  <p style="color:#444444; font-size:15px; line-height:1.6;">
                      This exceeds your security deposit — please contact us to settle the
                      remaining balance.
                  </p>
                  """.formatted(BRAND_COLOR, amountOwedByCustomer)
                : "";

        String headline = (hasDamage || hasLateFee)
                ? "Your Rental Return — Summary"
                : "Your Rental Has Been Returned";

        String intro = (hasDamage || hasLateFee)
                ? "We've processed the return of your rental. Here's a summary of the deposit settlement:"
                : "We've processed the return of your rental. Your security deposit has been refunded in full.";

        String html = """
                <!DOCTYPE html>
                <html>
                <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Arial, sans-serif;">
                    <div style="max-width:600px; margin:30px auto; background-color:#ffffff; padding:30px; border-radius:12px;">
                        <h1 style="color:%s; text-align:center;">%s</h1>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            Dear %s,
                        </p>

                        <p style="color:#444444; font-size:16px; line-height:1.6;">
                            %s
                        </p>

                        <p style="color:%s; font-weight:bold;">
                            Item: %s
                        </p>
                        <p style="color:#444444; font-size:15px; margin-top:-8px;">
                            Returned on %s
                        </p>

                        <div style="background-color:#f8f3f0; padding:18px; border-radius:10px; margin:20px 0;">
                            %s
                        </div>

                        %s

                        <p style="color:#444444; font-size:15px; line-height:1.6; margin-top:20px;">
                            Thank you for renting with Blanche Bridal — we hope you had a wonderful occasion.
                        </p>

                        <p style="font-size:14px; color:%s; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                BRAND_COLOR,
                headline,
                escapeHtml(customerName),
                intro,
                BRAND_COLOR,
                escapeHtml(productName),
                escapeHtml(dateStr),
                rows.length() > 0 ? rows.toString() : """
                        <p style="color:#444444; font-size:15px; margin:0;">No damage or late fees applied.</p>
                        """,
                owedHtml,
                BRAND_COLOR
        );

        sendHtmlEmail(toEmail, "Your rental return — " + escapeHtml(productName), html);
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

    /**
     * Builds a valid iCal (.ics) byte array for the appointment.
     * STATUS is either "CONFIRMED" or "TENTATIVE".
     */
    private byte[] buildIcalBytes(UUID appointmentId,
                                  LocalDate date,
                                  String timeSlot,
                                  String appointmentType,
                                  String icalStatus) {
        LocalTime startTime = parseSlotTime(timeSlot);
        LocalTime endTime = startTime.plusHours(1);

        DateTimeFormatter compact = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter timeCompact = DateTimeFormatter.ofPattern("HHmmss");

        String dtStart = date.format(compact) + "T" + startTime.format(timeCompact);
        String dtEnd   = date.format(compact) + "T" + endTime.format(timeCompact);
        String dtStamp = LocalDateTime.now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));

        String formattedType = formatType(appointmentType);
        String uid = "appt-" + appointmentId + "@blanchebridal.com";

        // Lines must not exceed 75 chars — use folding for long DESCRIPTION
        String ics = "BEGIN:VCALENDAR\r\n" +
                "VERSION:2.0\r\n" +
                "PRODID:-//Blanche Bridal//EN\r\n" +
                "CALSCALE:GREGORIAN\r\n" +
                "METHOD:REQUEST\r\n" +
                "BEGIN:VEVENT\r\n" +
                "UID:" + uid + "\r\n" +
                "DTSTAMP:" + dtStamp + "\r\n" +
                "DTSTART:" + dtStart + "\r\n" +
                "DTEND:" + dtEnd + "\r\n" +
                "SUMMARY:Blanche Bridal — " + formattedType + " Appointment\r\n" +
                "DESCRIPTION:Your " + formattedType + " appointment at Blanche Bridal Couture." +
                " Please arrive 5 minutes early.\r\n" +
                "LOCATION:Blanche Bridal Couture\r\n" +
                "STATUS:" + icalStatus + "\r\n" +
                "BEGIN:VALARM\r\n" +
                "TRIGGER:-PT1H\r\n" +
                "ACTION:DISPLAY\r\n" +
                "DESCRIPTION:Reminder: Blanche Bridal appointment in 1 hour\r\n" +
                "END:VALARM\r\n" +
                "END:VEVENT\r\n" +
                "END:VCALENDAR\r\n";

        return ics.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parses slot strings like "09:00", "9:00", "9:00 AM", "09:00 AM".
     */
    private LocalTime parseSlotTime(String timeSlot) {
        if (timeSlot == null || timeSlot.isBlank()) return LocalTime.of(9, 0);
        String t = timeSlot.trim();
        // 12-hour with AM/PM
        for (String pattern : List.of("h:mm a", "hh:mm a", "h:mma", "hh:mma")) {
            try {
                return LocalTime.parse(t.toUpperCase(), DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {}
        }
        // 24-hour
        for (String pattern : List.of("H:mm", "HH:mm")) {
            try {
                return LocalTime.parse(t, DateTimeFormatter.ofPattern(pattern));
            } catch (Exception ignored) {}
        }
        return LocalTime.of(9, 0); // safe fallback
    }

    /**
     * Sends an HTML email with an optional single binary attachment.
     * attachmentBytes may be null, in which case no attachment is added —
     * this lets sendOrderConfirmationEmail call it unconditionally whether
     * or not a receipt was generated.
     */
    private void sendHtmlEmailWithAttachment(String toEmail, String subject, String html,
                                             byte[] attachmentBytes, String attachmentFilename,
                                             String attachmentMimeType) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);

            if (attachmentBytes != null) {
                helper.addAttachment(
                        attachmentFilename,
                        new ByteArrayResource(attachmentBytes),
                        attachmentMimeType
                );
            }

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }

    /**
     * Sends an HTML email with an optional iCal .ics attachment.
     * Thin wrapper over sendHtmlEmailWithAttachment for the appointment flows.
     */
    private void sendHtmlEmailWithIcal(String toEmail, String subject,
                                       String html, byte[] icalBytes) {
        sendHtmlEmailWithAttachment(
                toEmail, subject, html, icalBytes,
                "appointment.ics", "text/calendar; method=REQUEST; charset=UTF-8"
        );
    }
}
package com.blanchebridal.backend.shared.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
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

    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = frontendUrl + "/verify-email?token=" + encodedToken;

        String html = """
                <!DOCTYPE html>
                <html>
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
                    <h1 style="color:#8b5e57; text-align:center;">Appointment Confirmed ✓</h1>

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

                    <p style="font-size:14px; color:#8b5e57; text-align:center; margin-top:28px;">
                        Blanche Bridal Couture
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
                    <h1 style="color:#8b5e57; text-align:center;">Booking Received</h1>

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

                    <p style="font-size:14px; color:#8b5e57; text-align:center; margin-top:28px;">
                        Blanche Bridal Couture
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

        byte[] ical = buildIcalBytes(appointmentId, appointmentDate, timeSlot,
                appointmentType, "TENTATIVE");

        sendHtmlEmailWithIcal(
                toEmail,
                "Appointment request received — " + formattedType + " on " + dateStr,
                html,
                ical
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
                        <h1 style="color:#8b5e57; text-align:center; margin-bottom:6px;">
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
                               style="display:inline-block; padding:14px 32px; background-color:#8b5e57; color:#ffffff;
                                      text-decoration:none; border-radius:8px; font-weight:bold; font-size:15px;">
                                Sign In
                            </a>
                        </div>

                        <p style="font-size:13px; color:#999999; text-align:center; margin-top:30px;">
                            If you did not expect this email, please contact your system administrator.
                        </p>

                        <p style="font-size:14px; color:#8b5e57; text-align:center; margin-top:28px;">
                            Blanche Bridal
                        </p>
                    </div>
                </body>
                </html>
                """.formatted(
                escapeHtml(firstName),
                escapeHtml(lastName),
                escapeHtml(toEmail),
                escapeHtml(temporaryPassword),
                loginLink
        );

        sendHtmlEmail(toEmail, "Your Blanche Bridal admin account is ready", html);
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
     * Sends an HTML email with an optional iCal .ics attachment.
     */
    private void sendHtmlEmailWithIcal(String toEmail, String subject,
                                       String html, byte[] icalBytes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(html, true);

            if (icalBytes != null) {
                helper.addAttachment(
                        "appointment.ics",
                        new ByteArrayResource(icalBytes),
                        "text/calendar; method=REQUEST; charset=UTF-8"
                );
            }

            mailSender.send(message);

        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send email", e);
        }
    }
}
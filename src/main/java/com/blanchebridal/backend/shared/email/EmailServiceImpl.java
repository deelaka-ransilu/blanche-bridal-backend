package com.blanchebridal.backend.shared.email;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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

    // ── existing methods ──────────────────────────────────────────────────────

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
                        "This link expires in 20 minutes.\n\n" +
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

    // ── new notification methods ──────────────────────────────────────────────

    @Override
    public void sendOrderConfirmationEmail(String toEmail, String customerName,
                                           String orderId, BigDecimal totalAmount,
                                           List<String> itemSummaries) {
        StringBuilder items = new StringBuilder();
        for (String item : itemSummaries) {
            items.append("  • ").append(item).append("\n");
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Your Blanche Bridal order is confirmed — #" + orderId);
        message.setText(
                "Dear " + customerName + ",\n\n" +
                        "Your order has been confirmed. Here's a summary:\n\n" +
                        items +
                        "\nTotal: LKR " + totalAmount + "\n\n" +
                        "Thank you for shopping with Blanche Bridal.\n\n" +
                        "Blanche Bridal"
        );

        mailSender.send(message);
    }

    @Override
    public void sendAppointmentConfirmationEmail(String toEmail, String customerName,
                                                 LocalDate appointmentDate, String timeSlot,
                                                 String appointmentType, String productName) {
        String formattedType = formatType(appointmentType);
        String dateStr = appointmentDate.format(DATE_FORMAT);

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(customerName).append(",\n\n");
        body.append("Your ").append(formattedType).append(" appointment has been confirmed.\n\n");
        body.append("Date:  ").append(dateStr).append("\n");
        body.append("Time:  ").append(timeSlot).append("\n");
        if (productName != null && !productName.isBlank()) {
            body.append("For:   ").append(productName).append("\n");
        }
        body.append("\nWe look forward to seeing you.\n");
        body.append("Please arrive 5 minutes before your appointment time.\n\n");
        body.append("Blanche Bridal");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Appointment confirmed — " + formattedType + " on " + dateStr + " at " + timeSlot);
        message.setText(body.toString());

        mailSender.send(message);
    }

    @Override
    public void sendAppointmentReminderEmail(String toEmail, String customerName,
                                             LocalDate appointmentDate, String timeSlot,
                                             String appointmentType) {
        String formattedType = formatType(appointmentType);
        String dateStr = appointmentDate.format(DATE_FORMAT);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Reminder: Your appointment tomorrow at " + timeSlot);
        message.setText(
                "Dear " + customerName + ",\n\n" +
                        "This is a reminder that you have a " + formattedType + " appointment tomorrow.\n\n" +
                        "Date:  " + dateStr + "\n" +
                        "Time:  " + timeSlot + "\n\n" +
                        "Please contact us if you need to reschedule.\n\n" +
                        "Blanche Bridal"
        );

        mailSender.send(message);
    }

    @Override
    public void sendRentalOverdueEmail(String toEmail, String customerName,
                                       String productName, LocalDate rentalEnd,
                                       BigDecimal balanceDue) {
        String dateStr = rentalEnd.format(DATE_FORMAT);

        StringBuilder body = new StringBuilder();
        body.append("Dear ").append(customerName).append(",\n\n");
        body.append("Your rental of ").append(productName)
                .append(" was due for return on ").append(dateStr).append(".\n");

        if (balanceDue != null && balanceDue.compareTo(BigDecimal.ZERO) > 0) {
            body.append("\nOutstanding balance: LKR ").append(balanceDue).append("\n");
        }

        body.append("\nPlease return the item as soon as possible or contact us to arrange.\n\n");
        body.append("Blanche Bridal");

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Action required: Rental return overdue");
        message.setText(body.toString());

        mailSender.send(message);
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /** Converts "RENTAL_PICKUP" → "Rental Pickup", "FITTING" → "Fitting" */
    private String formatType(String type) {
        if (type == null) return "";
        String[] parts = type.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(part.charAt(0))
                    .append(part.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
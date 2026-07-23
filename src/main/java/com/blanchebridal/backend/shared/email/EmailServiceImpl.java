package com.blanchebridal.backend.shared.email;

import com.blanchebridal.backend.order.dto.res.CustomQuoteResponse;
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

    private static final String BRAND_COLOR = "#c8102e";

    /**
     * Wraps a heading + body content in the shared card layout used by
     * every transactional email. Table-based (not div-based) so Gmail,
     * Outlook, and Apple Mail all center and render it consistently —
     * div + margin:auto centering is unreliable across email clients.
     */
    private String wrapEmail(String heading, String bodyHtml) {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
            </head>
            <body style="margin:0; padding:0; background-color:#f8f3f0; font-family:Georgia, 'Times New Roman', serif;">
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f8f3f0; padding:40px 16px;">
                    <tr>
                        <td align="center">
                            <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:560px; background-color:#ffffff; border-radius:16px; overflow:hidden; box-shadow:0 2px 12px rgba(0,0,0,0.05);">
                                <tr>
                                    <td style="padding:40px 40px 8px 40px; text-align:center;">
                                        <p style="margin:0 0 4px 0; font-family:Arial, sans-serif; font-size:11px; letter-spacing:2px; text-transform:uppercase; color:#a8a29a;">
                                            Blanche Bridal
                                        </p>
                                        <h1 style="margin:0 0 4px 0; font-size:24px; font-weight:500; color:%s;">
                                            %s
                                        </h1>
                                    </td>
                                </tr>
                                <tr>
                                    <td style="padding:16px 40px 40px 40px; font-family:Arial, sans-serif; color:#5c5854; font-size:15px; line-height:1.6; text-align:left;">
                                        %s
                                    </td>
                                </tr>
                            </table>
                            <p style="margin:24px 0 0 0; font-family:Arial, sans-serif; font-size:12px; color:#c2bdb6;">
                                Blanche Bridal
                            </p>
                        </td>
                    </tr>
                </table>
            </body>
            </html>
            """.formatted(BRAND_COLOR, heading, bodyHtml);
    }

    /** Soft cream info card used for date/time/order-detail groupings. */
    private String infoBox(String innerHtml) {
        return """
            <div style="background-color:#f8f3f0; padding:18px 20px; border-radius:12px; margin:20px 0;">
                %s
            </div>
            """.formatted(innerHtml);
    }

    /** Solid pill CTA button — primary action per email. */
    private String ctaButton(String href, String label) {
        return """
            <div style="text-align:center; margin:28px 0 8px 0;">
                <a href="%s" style="display:inline-block; padding:14px 36px; background-color:%s; color:#ffffff; text-decoration:none; border-radius:999px; font-family:Arial, sans-serif; font-size:14px; font-weight:600;">
                    %s
                </a>
            </div>
            """.formatted(href, BRAND_COLOR, label);
    }

    /** Outlined pill button — secondary action (e.g. "view receipt"). */
    private String outlineButton(String href, String label) {
        return """
            <div style="text-align:center; margin:20px 0;">
                <a href="%s" style="display:inline-block; padding:12px 28px; background-color:#ffffff; color:%s; text-decoration:none; border-radius:999px; font-weight:600; font-size:14px; border:2px solid %s;">
                    %s
                </a>
            </div>
            """.formatted(href, BRAND_COLOR, BRAND_COLOR, label);
    }

    /** Muted footer note, separated by a hairline — used for fine print. */
    private String footerNote(String text) {
        return """
            <p style="margin-top:24px; padding-top:20px; border-top:1px solid #f0ebe6; font-size:13px; color:#a8a29a;">
                %s
            </p>
            """.formatted(text);
    }

    // ---------------------------------------------------------------
    // Email builders
    // ---------------------------------------------------------------

    @Async
    @Override
    public void sendVerificationEmail(String toEmail, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = frontendUrl + "/verify-email?token=" + encodedToken;

        String body = """
                <p style="text-align:center;">Please verify your email address to activate your account.</p>
                %s
                <p style="text-align:center; font-size:13px; color:#a8a29a;">This link expires in 24 hours.</p>
                %s
                """.formatted(
                ctaButton(link, "Verify Email"),
                footerNote("If you did not create an account, you can safely ignore this email.")
        );

        sendHtmlEmail(toEmail, "Verify your Blanche Bridal account",
                wrapEmail("Welcome to Blanche Bridal", body));
    }

    @Async
    @Override
    public void sendPasswordResetEmail(String toEmail, String token) {
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = frontendUrl + "/reset-password?token=" + encodedToken;

        String body = """
                <p style="text-align:center;">You requested a password reset for your Blanche Bridal account.</p>
                %s
                <p style="text-align:center; font-size:13px; color:#a8a29a;">This link expires in 1 hour.</p>
                %s
                """.formatted(
                ctaButton(link, "Reset Password"),
                footerNote("If you did not request this, you can safely ignore this email.")
        );

        sendHtmlEmail(toEmail, "Reset your Blanche Bridal password",
                wrapEmail("Reset Your Password", body));
    }

    @Async
    @Override
    public void sendOrderConfirmationEmail(String toEmail,
                                           String customerName,
                                           String orderId,
                                           BigDecimal totalAmount,
                                           List<String> itemSummaries,
                                           byte[] receiptPdfBytes) {

        // Same short-ID convention used everywhere else (frontend does
        // order.id.slice(0,8).toUpperCase(), and sendOrderCancelledEmail
        // already does this) — the full UUID has no value to the customer
        // and looks unpolished in the email body.
        String displayId = orderId.length() >= 8
                ? orderId.substring(0, 8).toUpperCase()
                : orderId.toUpperCase();

        StringBuilder itemRows = new StringBuilder();

        if (itemSummaries != null && !itemSummaries.isEmpty()) {
            for (String item : itemSummaries) {
                itemRows.append("""
                    <tr>
                        <td style="padding:10px 0; border-bottom:1px solid #f0ebe6;">%s</td>
                    </tr>
                    """.formatted(escapeHtml(item)));
            }
        } else {
            itemRows.append("""
                <tr>
                    <td style="padding:10px 0;">No item details available.</td>
                </tr>
                """);
        }

        String receiptNote = receiptPdfBytes != null
                ? "<p>A receipt for this payment is attached as a PDF.</p>"
                : "";

        String body = """
            <p>Dear %s,</p>
            <p>Your order has been confirmed. Here's a summary:</p>
            <p style="color:%s; font-weight:bold;">Order ID: #%s</p>
            <table style="width:100%%; border-collapse:collapse; margin-top:8px;">
                %s
            </table>
            <p style="font-size:18px; color:#2b2926; font-weight:bold; margin-top:20px;">
                Total: LKR %s
            </p>
            %s
            <p>Thank you for shopping with Blanche Bridal.</p>
            """.formatted(
                escapeHtml(customerName),
                BRAND_COLOR,
                escapeHtml(displayId),
                itemRows,
                totalAmount,
                receiptNote
        );

        sendHtmlEmailWithAttachment(
                toEmail,
                "Your Blanche Bridal order is confirmed - #" + displayId,
                wrapEmail("Order Confirmed", body),
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

        String productLine = (productName != null && !productName.isBlank())
                ? "<p style=\"margin:8px 0 0 0;\"><strong>For:</strong> %s</p>".formatted(escapeHtml(productName))
                : "";

        String body = """
                <p>Dear %s,</p>
                <p>Your <strong>%s</strong> appointment has been confirmed by our team.</p>
                %s
                <p>A calendar invite is attached — open it to add this appointment directly to your Google Calendar.</p>
                <p>Please arrive 5 minutes before your appointment time.</p>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(formattedType),
                infoBox("""
                        <p style="margin:0 0 8px 0;"><strong>Date:</strong> %s</p>
                        <p style="margin:0;"><strong>Time:</strong> %s</p>
                        %s
                        """.formatted(escapeHtml(dateStr), escapeHtml(timeSlot), productLine))
        );

        byte[] ical = buildIcalBytes(appointmentId, appointmentDate, timeSlot,
                appointmentType, "CONFIRMED");

        sendHtmlEmailWithIcal(
                toEmail,
                "Appointment confirmed — " + formattedType + " on " + dateStr + " at " + timeSlot,
                wrapEmail("Appointment Confirmed", body),
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

        String productLine = (productName != null && !productName.isBlank())
                ? "<p style=\"margin:0 0 8px 0;\"><strong>For:</strong> %s</p>".formatted(escapeHtml(productName))
                : "";

        String body = """
                <p>Dear %s,</p>
                <p>Thank you! We've received your <strong>%s</strong> appointment request.
                Our team will review it and send you a confirmation shortly.</p>
                %s
                <p>A calendar placeholder is attached so you can block the time in your calendar
                while you wait for confirmation.</p>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(formattedType),
                infoBox("""
                        <p style="margin:0 0 8px 0;"><strong>Date:</strong> %s</p>
                        <p style="margin:0 0 8px 0;"><strong>Time:</strong> %s</p>
                        %s
                        <p style="margin:12px 0 0 0; font-size:13px; color:#888888;">
                            Status: <span style="color:#b07d00; font-weight:bold;">Pending Confirmation</span>
                        </p>
                        """.formatted(escapeHtml(dateStr), escapeHtml(timeSlot), productLine))
        );

        byte[] ical = buildIcalBytes(appointmentId, appointmentDate, timeSlot,
                appointmentType, "TENTATIVE");

        sendHtmlEmailWithIcal(
                toEmail,
                "Appointment request received — " + formattedType + " on " + dateStr,
                wrapEmail("Booking Received", body),
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

        String body = """
                <p>Dear %s,</p>
                <p>This is a reminder that you have a %s appointment tomorrow.</p>
                %s
                <p>Please contact us if you need to reschedule.</p>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(formattedType),
                infoBox("""
                        <p style="margin:0 0 8px 0;"><strong>Date:</strong> %s</p>
                        <p style="margin:0;"><strong>Time:</strong> %s</p>
                        """.formatted(escapeHtml(dateStr), escapeHtml(timeSlot)))
        );

        sendHtmlEmail(toEmail, "Reminder: Your appointment tomorrow at " + timeSlot,
                wrapEmail("Appointment Reminder", body));
    }

    @Async
    @Override
    public void sendRentalOverdueEmail(String toEmail,
                                       String customerName,
                                       String productName,
                                       LocalDate rentalEnd,
                                       BigDecimal balanceDue) {

        String dateStr = rentalEnd.format(DATE_FORMAT);

        String balanceLine = (balanceDue != null && balanceDue.compareTo(BigDecimal.ZERO) > 0)
                ? "<p style=\"font-size:17px; color:%s; font-weight:bold;\">Outstanding balance: LKR %s</p>".formatted(BRAND_COLOR, balanceDue)
                : "";

        String body = """
                <p>Dear %s,</p>
                <p>Your rental of <strong>%s</strong> was due for return on <strong>%s</strong>.</p>
                %s
                <p>Please return the item as soon as possible or contact us to arrange.</p>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(productName),
                escapeHtml(dateStr),
                balanceLine
        );

        sendHtmlEmail(toEmail, "Action required: Rental return overdue",
                wrapEmail("Rental Return Overdue", body));
    }

    @Async
    @Override
    public void sendAdminWelcomeEmail(String toEmail,
                                      String firstName,
                                      String lastName,
                                      String temporaryPassword) {

        String loginLink = frontendUrl + "/login";

        String body = """
                <p style="text-align:center; margin-top:-8px; color:#a8a29a; font-size:13px;">Admin Account Created</p>
                <p>Dear %s %s,</p>
                <p>An admin account has been created for you on the Blanche Bridal management system.
                Use the credentials below to sign in.</p>
                %s
                <p style="color:#b05e00; font-size:14px;">Please change your password after your first login.</p>
                %s
                %s
                """.formatted(
                escapeHtml(firstName),
                escapeHtml(lastName),
                infoBox("""
                        <p style="margin:0 0 10px 0;"><strong>Email:</strong> %s</p>
                        <p style="margin:0;"><strong>Temporary password:</strong>
                            <span style="font-family:monospace; background-color:#ffffff; padding:3px 8px; border-radius:4px; border:1px solid #e6e0da;">%s</span>
                        </p>
                        """.formatted(escapeHtml(toEmail), escapeHtml(temporaryPassword))),
                ctaButton(loginLink, "Sign In"),
                footerNote("If you did not expect this email, please contact your system administrator.")
        );

        sendHtmlEmail(toEmail, "Your Blanche Bridal admin account is ready",
                wrapEmail("Welcome to Blanche Bridal", body));
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

        String body = """
                <p>Dear %s,</p>
                <p>Your <strong>%s</strong> appointment has been rescheduled by our team.
                Here are your updated details:</p>
                %s
                <p>If you have any questions, please contact us directly.</p>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(formattedType),
                infoBox("""
                        <p style="margin:0 0 8px 0;"><strong>New Date:</strong> %s</p>
                        <p style="margin:0;"><strong>New Time:</strong> %s</p>
                        """.formatted(escapeHtml(dateStr), escapeHtml(newTimeSlot)))
        );

        sendHtmlEmail(toEmail, "Your appointment has been rescheduled — " + dateStr + " at " + newTimeSlot,
                wrapEmail("Appointment Rescheduled", body));
    }

    @Async
    @Override
    public void sendInquiryReplyEmail(String toEmail, String customerName,
                                      String originalMessage, String replyMessage) {
        String body = """
                <p>Dear %s,</p>
                <p>Thank you for reaching out to Blanche Bridal. Here is our response to your enquiry:</p>
                <div style="background-color:#f8f3f0; padding:18px 20px; border-radius:12px; margin:20px 0; border-left:4px solid %s;">
                    <p style="margin:0; white-space:pre-wrap;">%s</p>
                </div>
                <hr style="border:none; border-top:1px solid #f0ebe6; margin:24px 0;" />
                <p style="font-size:13px; color:#a8a29a;"><strong>Your original message:</strong><br/>%s</p>
                """.formatted(
                escapeHtml(customerName),
                BRAND_COLOR,
                escapeHtml(replyMessage),
                escapeHtml(originalMessage)
        );

        sendHtmlEmail(toEmail, "Re: Your Blanche Bridal Enquiry",
                wrapEmail("Response to Your Enquiry", body));
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

        String body = """
                <p>Dear %s,</p>
                <p>Your <strong>%s</strong> appointment scheduled for the details below has been cancelled.</p>
                %s
                <p>If this wasn't expected, or you'd like to book a new time, please contact us or
                book again from your account.</p>
                """.formatted(
                escapeHtml(customerName),
                escapeHtml(formattedType),
                infoBox("""
                        <p style="margin:0 0 8px 0;"><strong>Date:</strong> %s</p>
                        <p style="margin:0;"><strong>Time:</strong> %s</p>
                        """.formatted(escapeHtml(dateStr), escapeHtml(timeSlot)))
        );

        sendHtmlEmail(toEmail, "Your appointment on " + dateStr + " has been cancelled",
                wrapEmail("Appointment Cancelled", body));
    }

    @Async
    @Override
    public void sendOrderReadyEmail(String toEmail,
                                    String customerName,
                                    String orderId,
                                    String fulfillmentMethod) {

        // Same short-ID convention as the other order emails.
        String displayId = orderId.length() >= 8
                ? orderId.substring(0, 8).toUpperCase()
                : orderId.toUpperCase();

        boolean isPickup = "PICKUP".equalsIgnoreCase(fulfillmentMethod);

        String headline = isPickup ? "Your Order is Ready for Pickup" : "Your Order is Ready to Ship";
        String bodyText = isPickup
                ? "Great news — your order is packed and ready for you to collect from our store."
                : "Great news — your order is packed and ready to be shipped to your delivery address.";
        String closingText = isPickup
                ? "Please bring a valid ID when collecting your order. We look forward to seeing you."
                : "You'll receive tracking details separately once the item is handed to the courier.";

        String body = """
            <p>Dear %s,</p>
            <p>%s</p>
            <p style="color:%s; font-weight:bold;">Order ID: #%s</p>
            <p>%s</p>
            """.formatted(
                escapeHtml(customerName),
                bodyText,
                BRAND_COLOR,
                escapeHtml(displayId),
                closingText
        );

        sendHtmlEmail(toEmail, "Your Blanche Bridal order is ready - #" + displayId,
                wrapEmail(headline, body));
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

        String refundSection = refundOwed
                ? """
                  <p>A refund is owed for this order. Please submit your bank details so our team
                  can process the transfer.</p>
                  %s
                  """.formatted(ctaButton(refundLink, "Submit Bank Details"))
                : "<p>If a payment was made for this order, any applicable refund will be processed " +
                  "separately and you'll receive a confirmation once it's complete.</p>";

        String body = """
                <p>Dear %s,</p>
                <p>Your order has been cancelled.</p>
                <p style="color:%s; font-weight:bold;">Order ID: #%s</p>
                %s
                <p>If this wasn't expected, please contact us.</p>
                """.formatted(
                escapeHtml(customerName),
                BRAND_COLOR,
                escapeHtml(displayId),
                refundSection
        );

        sendHtmlEmail(toEmail, "Your order #" + displayId + " has been cancelled",
                wrapEmail("Order Cancelled", body));
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

        // Same short-ID convention as the other order emails.
        String displayId = orderId.length() >= 8
                ? orderId.substring(0, 8).toUpperCase()
                : orderId.toUpperCase();

        String reasonLine = (reason != null && !reason.isBlank())
                ? "<p><strong>Reason:</strong> %s</p>".formatted(escapeHtml(reason))
                : "";

        String receiptSection = (proofImageUrl != null && !proofImageUrl.isBlank())
                ? outlineButton(proofImageUrl, "View Transfer Receipt")
                : "";

        String body = """
            <p>Dear %s,</p>
            <p>A refund has been processed for your order via bank transfer.</p>
            <p style="color:%s; font-weight:bold;">Order ID: #%s</p>
            <p style="font-size:18px; color:#2b2926; font-weight:bold; margin-top:16px;">
                Refund amount: LKR %s
            </p>
            %s
            %s
            <p style="margin-top:20px;">
                A copy of the transfer receipt is on file with our team — you can view it above,
                or find it any time on your order page. Please allow a few business days for the
                transfer to reflect in your account.
            </p>
            <p>If you haven't received it or have any questions, please contact us.</p>
            """.formatted(
                escapeHtml(customerName),
                BRAND_COLOR,
                escapeHtml(displayId),
                amount,
                reasonLine,
                receiptSection
        );

        sendHtmlEmail(toEmail, "Refund processed for order #" + displayId,
                wrapEmail("Refund Processed", body));
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
            rows.append("<p style=\"margin:0 0 8px 0;\"><strong>Damage cost:</strong> LKR %s</p>"
                    .formatted(damageCost));
        }
        if (hasLateFee) {
            rows.append("<p style=\"margin:0 0 8px 0;\"><strong>Late return fee:</strong> LKR %s</p>"
                    .formatted(lateFeeAmount));
        }
        if (securityDepositRefundedAmount != null) {
            rows.append("<p style=\"margin:0;\"><strong>Security deposit refunded:</strong> LKR %s</p>"
                    .formatted(securityDepositRefundedAmount));
        }
        if (rows.length() == 0) {
            rows.append("<p style=\"margin:0;\">No damage or late fees applied.</p>");
        }

        String owedSection = owesMore
                ? """
                  <p style="font-size:17px; color:%s; font-weight:bold; margin-top:16px;">
                      Amount still owed: LKR %s
                  </p>
                  <p>This exceeds your security deposit — please contact us to settle the
                  remaining balance.</p>
                  """.formatted(BRAND_COLOR, amountOwedByCustomer)
                : "";

        String headline = (hasDamage || hasLateFee)
                ? "Your Rental Return — Summary"
                : "Your Rental Has Been Returned";

        String intro = (hasDamage || hasLateFee)
                ? "We've processed the return of your rental. Here's a summary of the deposit settlement:"
                : "We've processed the return of your rental. Your security deposit has been refunded in full.";

        String body = """
                <p>Dear %s,</p>
                <p>%s</p>
                <p style="color:%s; font-weight:bold; margin-bottom:0;">Item: %s</p>
                <p style="font-size:13px; color:#a8a29a; margin-top:2px;">Returned on %s</p>
                %s
                %s
                <p style="margin-top:20px;">
                    Thank you for renting with Blanche Bridal — we hope you had a wonderful occasion.
                </p>
                """.formatted(
                escapeHtml(customerName),
                intro,
                BRAND_COLOR,
                escapeHtml(productName),
                escapeHtml(dateStr),
                infoBox(rows.toString()),
                owedSection
        );

        sendHtmlEmail(toEmail, "Your rental return — " + escapeHtml(productName),
                wrapEmail(headline, body));
    }

    @Async
    @Override
    public void sendCustomQuoteEmail(String toEmail,
                                     String customerName,
                                     UUID customDesignRequestId,
                                     com.blanchebridal.backend.order.dto.res.CustomQuoteResponse quote) {

        String viewLink = frontendUrl + "/my/custom-design/" + customDesignRequestId;
        String validUntilStr = quote.validUntil().format(DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a"));

        String otherRow = quote.otherAmount().compareTo(BigDecimal.ZERO) > 0
                ? """
                  <tr>
                      <td style="padding:8px 0;">Other (%s)</td>
                      <td style="padding:8px 0; text-align:right;">LKR %s</td>
                  </tr>
                  """.formatted(escapeHtml(quote.otherNote()), quote.otherAmount())
                : "";

        String paymentTerms = quote.splitType() == com.blanchebridal.backend.order.entity.SplitType.FULL_UPFRONT
                ? "Full amount upfront"
                : "50% now, 50% at pickup";

        String body = """
                <p>Dear %s,</p>
                <p>Here's the quote (version %d) for your custom design:</p>

                <table style="width:100%%; border-collapse:collapse; margin-top:8px;">
                    <tr>
                        <td style="padding:8px 0; border-bottom:1px solid #f0ebe6;">Fabric &amp; materials</td>
                        <td style="padding:8px 0; border-bottom:1px solid #f0ebe6; text-align:right;">LKR %s</td>
                    </tr>
                    <tr>
                        <td style="padding:8px 0; border-bottom:1px solid #f0ebe6;">Stitching &amp; tailoring labor</td>
                        <td style="padding:8px 0; border-bottom:1px solid #f0ebe6; text-align:right;">LKR %s</td>
                    </tr>
                    <tr>
                        <td style="padding:8px 0; border-bottom:1px solid #f0ebe6;">Embellishments &amp; detailing</td>
                        <td style="padding:8px 0; border-bottom:1px solid #f0ebe6; text-align:right;">LKR %s</td>
                    </tr>
                    <tr>
                        <td style="padding:8px 0; border-bottom:1px solid #f0ebe6;">Alterations &amp; fitting</td>
                        <td style="padding:8px 0; border-bottom:1px solid #f0ebe6; text-align:right;">LKR %s</td>
                    </tr>
                    %s
                </table>

                <p style="font-size:18px; color:#2b2926; font-weight:bold; margin-top:20px;">
                    Total: LKR %s
                </p>
                <p>Payment: %s</p>
                <p style="color:#b05e00; font-size:14px;">
                    This quote is valid until %s. Please review and respond before then.
                </p>
                %s
                """.formatted(
                escapeHtml(customerName),
                quote.version(),
                quote.fabricAmount(),
                quote.laborAmount(),
                quote.embellishmentAmount(),
                quote.alterationsAmount(),
                otherRow,
                quote.totalAmount(),
                paymentTerms,
                validUntilStr,
                ctaButton(viewLink, "View & Respond to Quote")
        );

        sendHtmlEmail(toEmail, "Your Blanche Bridal custom order quote is ready",
                wrapEmail("Your Custom Order Quote", body));
    }

    @Async
    @Override
    public void sendCustomDressFittingReadyEmail(String toEmail, String customerName, UUID customDesignRequestId) {
        String viewLink = frontendUrl + "/my/custom-design/" + customDesignRequestId;

        String body = """
                <p>Dear %s,</p>
                <p>Great news — your custom dress has reached the fitting stage!
                Please contact us to schedule your fitting appointment at a time that suits you.</p>
                <p>At the fitting, our team will check the fit and make any final adjustments
                to ensure your dress is perfect for your special day.</p>
                %s
                """.formatted(
                escapeHtml(customerName),
                ctaButton(viewLink, "View Your Order")
        );

        sendHtmlEmail(toEmail, "Your custom dress is ready for fitting — Blanche Bridal",
                wrapEmail("Your Dress is Ready for Fitting", body));
    }

    @Async
    @Override
    public void sendCustomDressReadyForPickupEmail(String toEmail, String customerName, UUID customDesignRequestId) {
        String viewLink = frontendUrl + "/my/custom-design/" + customDesignRequestId;

        String body = """
                <p>Dear %s,</p>
                <p>Your custom dress has passed our quality check and is ready for pickup!
                Please visit our store at your earliest convenience to collect it.</p>
                <p>The remaining balance will be collected at pickup.
                Please bring a valid ID when you come to collect your dress.</p>
                %s
                """.formatted(
                escapeHtml(customerName),
                ctaButton(viewLink, "View Your Order")
        );

        sendHtmlEmail(toEmail, "Your custom dress is ready for pickup — Blanche Bridal",
                wrapEmail("Your Dress is Ready for Pickup", body));
    }

    // ---------------------------------------------------------------
    // Send helpers
    // ---------------------------------------------------------------

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
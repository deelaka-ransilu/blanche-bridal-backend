package com.blanchebridal.backend.shared.email;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmailService {

    void sendVerificationEmail(String toEmail, String token);

    void sendPasswordResetEmail(String toEmail, String token);

    void sendOrderConfirmationEmail(String toEmail,
                                    String customerName,
                                    String orderId,
                                    BigDecimal totalAmount,
                                    List<String> itemSummaries);

    void sendAppointmentConfirmationEmail(String toEmail,
                                          String customerName,
                                          UUID appointmentId,          // ← ADD THIS
                                          LocalDate appointmentDate,
                                          String timeSlot,
                                          String appointmentType,
                                          String productName);

    default void sendAppointmentConfirmationEmail(String toEmail,
                                                  String customerName,
                                                  LocalDate appointmentDate,
                                                  String timeSlot,
                                                  String appointmentType,
                                                  String productName) {
        sendAppointmentConfirmationEmail(
                toEmail,
                customerName,
                UUID.randomUUID(),
                appointmentDate,
                timeSlot,
                appointmentType,
                productName
        );
    }

    void sendAppointmentBookingReceivedEmail(String toEmail,
                                             String customerName,
                                             UUID appointmentId,
                                             LocalDate appointmentDate,
                                             String timeSlot,
                                             String appointmentType,
                                             String productName);

    void sendAppointmentReminderEmail(String toEmail,
                                      String customerName,
                                      LocalDate appointmentDate,
                                      String timeSlot,
                                      String appointmentType);

    void sendRentalOverdueEmail(String toEmail,
                                String customerName,
                                String productName,
                                LocalDate rentalEnd,
                                BigDecimal balanceDue);

    void sendAdminWelcomeEmail(String toEmail,
                               String firstName,
                               String lastName,
                               String temporaryPassword);

    void sendAppointmentRescheduledEmail(String toEmail,
                                         String customerName,
                                         LocalDate newDate,
                                         String newTimeSlot,
                                         String appointmentType);

    void sendInquiryReplyEmail(String toEmail,
                               String customerName,
                               String originalMessage,
                               String replyMessage);

    void sendAppointmentCancelledEmail(String toEmail,
                                       String customerName,
                                       LocalDate appointmentDate,
                                       String timeSlot,
                                       String appointmentType);

    /**
     * Sent when an order transitions to READY — i.e. ready for the
     * customer to collect (pickup) or ready to be shipped/delivered.
     * fulfillmentMethod is passed through as a plain string (e.g. "PICKUP",
     * "DELIVERY") so the template can vary the wording without EmailService
     * depending on the order module's enum type.
     */
    void sendOrderReadyEmail(String toEmail,
                             String customerName,
                             String orderId,
                             String fulfillmentMethod);

    /**
     * Sent when an order is cancelled, whether by the customer or by staff.
     */
    void sendOrderCancelledEmail(String toEmail, String customerName, String fullOrderId, boolean refundOwed);

    /**
     * Sent when a refund has been processed for an order. proofImageUrl is
     * the admin-uploaded transfer receipt (Cloudinary URL) — linked in the
     * email so "a copy is on file" is a real clickable reference, not just
     * a claim in the text. Can be null/blank in theory (defensive only —
     * RefundServiceImpl.createRefund already requires it to be non-blank
     * before a Refund can be created at all).
     */
    void sendRefundProcessedEmail(String toEmail,
                                  String customerName,
                                  String orderId,
                                  BigDecimal amount,
                                  String reason,
                                  String proofImageUrl);

    void sendRentalReturnedEmail(String toEmail,
                                 String customerName,
                                 String productName,
                                 java.time.LocalDate returnDate,
                                 java.math.BigDecimal damageCost,
                                 java.math.BigDecimal lateFeeAmount,
                                 java.math.BigDecimal securityDepositRefundedAmount,
                                 java.math.BigDecimal amountOwedByCustomer);
}
package com.blanchebridal.backend.shared.email;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface EmailService {

    void sendVerificationEmail(String toEmail, String token);

    void sendPasswordResetEmail(String toEmail, String token);

    // Extended with an optional receipt PDF attachment. Kept as the primary
    // abstract method; the old 5-arg signature becomes a default that
    // delegates with receiptPdfBytes = null, so any other caller (if one
    // exists) keeps compiling without changes.
    void sendOrderConfirmationEmail(String toEmail,
                                    String customerName,
                                    String orderId,
                                    BigDecimal totalAmount,
                                    List<String> itemSummaries,
                                    byte[] receiptPdfBytes);

    default void sendOrderConfirmationEmail(String toEmail,
                                            String customerName,
                                            String orderId,
                                            BigDecimal totalAmount,
                                            List<String> itemSummaries) {
        sendOrderConfirmationEmail(toEmail, customerName, orderId, totalAmount, itemSummaries, null);
    }

    void sendAppointmentConfirmationEmail(String toEmail,
                                          String customerName,
                                          UUID appointmentId,
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

    void sendOrderReadyEmail(String toEmail,
                             String customerName,
                             String orderId,
                             String fulfillmentMethod);

    void sendOrderCancelledEmail(String toEmail, String customerName, String fullOrderId, boolean refundOwed);

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

    void sendCustomQuoteEmail(String toEmail,
                              String customerName,
                              UUID customDesignRequestId,
                              com.blanchebridal.backend.order.dto.res.CustomQuoteResponse quote);
}
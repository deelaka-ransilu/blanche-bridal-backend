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
}
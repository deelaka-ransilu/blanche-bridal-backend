package com.blanchebridal.backend.payment.service.impl;

import com.blanchebridal.backend.exception.ResourceNotFoundException;
import com.blanchebridal.backend.exception.UnauthorizedException;
import com.blanchebridal.backend.order.entity.Order;
import com.blanchebridal.backend.order.entity.OrderItem;
import com.blanchebridal.backend.payment.dto.res.ReceiptResponse;
import com.blanchebridal.backend.payment.entity.Payment;
import com.blanchebridal.backend.payment.entity.Receipt;
import com.blanchebridal.backend.payment.repository.ReceiptRepository;
import com.blanchebridal.backend.payment.service.ReceiptService;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    // ─── Brand colors ────────────────────────────────────────────────────────
    // Mirrors the light-theme palette in globals.css so the PDF feels
    // consistent with the rest of the site rather than using arbitrary colors.
    private static final DeviceRgb PRIMARY          = new DeviceRgb(0xD2, 0x33, 0x5E); // --primary
    private static final DeviceRgb PRIMARY_LIGHT     = new DeviceRgb(0xFB, 0xEC, 0xF1); // tint of --primary for backgrounds
    private static final DeviceRgb MUTED_FOREGROUND  = new DeviceRgb(0x6B, 0x6B, 0x6B); // --muted-foreground
    private static final DeviceRgb BORDER            = new DeviceRgb(0xE8, 0xE5, 0xE0); // --border
    private static final DeviceRgb STATUS_COMPLETED  = new DeviceRgb(0x4C, 0x8B, 0x63); // --status-completed
    private static final DeviceRgb STATUS_PENDING    = new DeviceRgb(0xC9, 0x96, 0x2C); // --status-pending
    private static final DeviceRgb STATUS_CANCELLED  = new DeviceRgb(0xA3, 0x48, 0x48); // --status-cancelled

    // ─── Public API ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Receipt generateReceipt(Order order, Payment payment) {
        // Idempotency: if receipt already exists for this order, return it
        return receiptRepository.findByOrder_Id(order.getId())
                .orElseGet(() -> {
                    try {
                        String receiptNumber = buildNextReceiptNumber();
                        byte[] pdfBytes      = buildPdf(order, payment, receiptNumber);

                        Receipt receipt = Receipt.builder()
                                .order(order)
                                .payment(payment)
                                .receiptNumber(receiptNumber)
                                .pdfData(pdfBytes)
                                .build();

                        return receiptRepository.save(receipt);

                    } catch (Exception e) {
                        // Log but don't crash the webhook — order is already CONFIRMED
                        log.error("Receipt generation failed for order {}: {}",
                                order.getId(), e.getMessage(), e);
                        throw new RuntimeException("Receipt generation failed", e);
                    }
                });
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReceiptResponse> getMyReceipts(UUID userId) {
        return receiptRepository.findByOrder_User_Id(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReceiptResponse> getAllReceipts(Pageable pageable) {
        return receiptRepository.findAll(pageable).map(this::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public String getReceiptPdfUrl(UUID receiptId, UUID requestingUserId, String role) {
        // Legacy field — only populated on receipts generated before the
        // switch to DB storage. Will be null for anything generated after.
        return getAuthorizedReceipt(receiptId, requestingUserId, role).getPdfUrl();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadReceiptPdf(UUID receiptId, UUID requestingUserId, String role) {
        Receipt receipt = getAuthorizedReceipt(receiptId, requestingUserId, role);

        byte[] pdfData = receipt.getPdfData();
        if (pdfData == null || pdfData.length == 0) {
            // Covers receipts created before the DB-storage migration whose
            // only copy lives in Cloudinary — that delivery path is
            // currently blocked (untrusted free-tier account), so there's
            // nothing we can serve for these until they're backfilled.
            log.error("No pdf_data stored for receipt {} — likely a pre-migration " +
                    "Cloudinary-only receipt with no local backfill.", receiptId);
            throw new ResourceNotFoundException(
                    "Receipt PDF is not available for download: " + receiptId);
        }

        return pdfData;
    }

    @Override
    @Transactional(readOnly = true)
    public String getReceiptFilename(UUID receiptId, UUID requestingUserId, String role) {
        Receipt receipt = getAuthorizedReceipt(receiptId, requestingUserId, role);
        return receipt.getReceiptNumber() + ".pdf";
    }

    @Override
    @Transactional(readOnly = true)
    public ReceiptResponse getReceiptByOrderId(UUID orderId, UUID requestingUserId, String role) {
        Receipt receipt = receiptRepository.findByOrder_Id(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No receipt found for order: " + orderId));

        boolean isAdminOrEmployee = "ADMIN".equals(role) || "EMPLOYEE".equals(role);
        if (!isAdminOrEmployee) {
            UUID ownerUserId = receipt.getOrder().getUser().getId();
            if (!ownerUserId.equals(requestingUserId)) {
                throw new UnauthorizedException("Access denied to this receipt");
            }
        }

        return toResponse(receipt);
    }

    // ─── Shared ownership check ─────────────────────────────────────────────

    private Receipt getAuthorizedReceipt(UUID receiptId, UUID requestingUserId, String role) {
        Receipt receipt = receiptRepository.findById(receiptId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Receipt not found: " + receiptId));

        boolean isAdminOrEmployee =
                "ADMIN".equals(role) || "EMPLOYEE".equals(role);

        if (!isAdminOrEmployee) {
            UUID ownerUserId = receipt.getOrder().getUser().getId();
            if (!ownerUserId.equals(requestingUserId)) {
                throw new UnauthorizedException("Access denied to this receipt");
            }
        }

        return receipt;
    }

    // ─── Receipt number ───────────────────────────────────────────────────────

    private String buildNextReceiptNumber() {
        int year = LocalDateTime.now().getYear();

        return receiptRepository.findLatestReceiptNumber()
                .map(latest -> {
                    // Format: RCP-2025-00042 → parse the 5-digit sequence
                    try {
                        String[] parts = latest.split("-");
                        int lastYear = Integer.parseInt(parts[1]);
                        int seq      = Integer.parseInt(parts[2]);
                        // Reset sequence when year rolls over
                        int nextSeq = (lastYear == year) ? seq + 1 : 1;
                        return "RCP-%d-%05d".formatted(year, nextSeq);
                    } catch (Exception e) {
                        return "RCP-%d-00001".formatted(year);
                    }
                })
                .orElse("RCP-%d-00001".formatted(year));
    }

    // ─── PDF builder ──────────────────────────────────────────────────────────

    private byte[] buildPdf(Order order, Payment payment,
                            String receiptNumber) throws IOException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter   writer  = new PdfWriter(baos);
        PdfDocument pdfDoc  = new PdfDocument(writer);
        Document    document = new Document(pdfDoc);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        // ── Header ──────────────────────────────────────────────────────────
        document.add(new Paragraph("Blanche Bridal")
                .setFont(bold)
                .setFontSize(24)
                .setFontColor(PRIMARY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));

        // Thin brand-colored rule under the header, echoing the site's
        // primary accent rather than a plain gray divider.
        document.add(new com.itextpdf.layout.element.Div()
                .setHeight(2)
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(PRIMARY)
                .setMarginBottom(18));

        // ── Receipt meta ─────────────────────────────────────────────────────
        document.add(metaLine("Receipt No:", receiptNumber, bold, regular));
        document.add(metaLine("Issue Date:",
                LocalDateTime.now().format(DATE_FMT), bold, regular));
        document.add(metaLine("Order ID:",
                order.getId().toString().substring(0, 8).toUpperCase(), bold, regular));
        document.add(metaLine("Order Date:",
                order.getCreatedAt().format(DATE_FMT), bold, regular));

        // ── Customer info ────────────────────────────────────────────────────
        document.add(new Paragraph("\nCustomer Details")
                .setFont(bold).setFontSize(12).setMarginTop(10));

        String fullName = ((order.getUser().getFirstName() != null
                ? order.getUser().getFirstName() : "") + " "
                + (order.getUser().getLastName() != null
                ? order.getUser().getLastName() : "")).trim();

        document.add(metaLine("Name:",  fullName.isEmpty() ? "—" : fullName,
                bold, regular));
        document.add(metaLine("Email:", order.getUser().getEmail(), bold, regular));

        // ── Items table ───────────────────────────────────────────────────────
        document.add(new Paragraph("\nOrder Items")
                .setFont(bold).setFontSize(12).setMarginTop(10));

        Table table = new Table(UnitValue.createPercentArray(
                new float[]{40, 15, 10, 17.5f, 17.5f}))
                .setWidth(UnitValue.createPercentValue(100));

        // Header row
        for (String col : new String[]{"Product", "Size", "Qty",
                "Unit Price (LKR)", "Subtotal (LKR)"}) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(col).setFont(bold).setFontSize(10).setFontColor(PRIMARY))
                    .setBackgroundColor(PRIMARY_LIGHT)
                    .setPadding(6));
        }

        // Data rows
        for (OrderItem item : order.getItems()) {
            BigDecimal subtotal = item.getUnitPrice()
                    .multiply(BigDecimal.valueOf(item.getQuantity()));

            table.addCell(cell(item.getProductName() != null
                    ? item.getProductName() : "—", regular));
            table.addCell(cell(item.getSize() != null
                    ? item.getSize() : "—", regular));
            table.addCell(cell(String.valueOf(item.getQuantity()), regular));
            table.addCell(cell(formatAmount(item.getUnitPrice()), regular));
            table.addCell(cell(formatAmount(subtotal), regular));
        }

        // Total row — tinted to draw the eye, matching the table header
        table.addCell(new Cell(1, 4)
                .add(new Paragraph("TOTAL").setFont(bold).setFontSize(10))
                .setBackgroundColor(PRIMARY_LIGHT)
                .setBorderTop(null)
                .setPadding(6));
        table.addCell(new Cell()
                .add(new Paragraph(formatAmount(order.getTotalAmount()))
                        .setFont(bold).setFontSize(10).setFontColor(PRIMARY))
                .setBackgroundColor(PRIMARY_LIGHT)
                .setPadding(6));

        document.add(table);

        // ── Payment info ─────────────────────────────────────────────────────
        document.add(new Paragraph("\nPayment Details")
                .setFont(bold).setFontSize(12).setMarginTop(14));

        document.add(metaLine("Method:",
                payment.getMethod().name(), bold, regular));

        DeviceRgb statusColor = switch (payment.getStatus().name()) {
            case "COMPLETED" -> STATUS_COMPLETED;
            case "FAILED", "CANCELLED" -> STATUS_CANCELLED;
            default -> STATUS_PENDING; // PENDING and any other in-progress state
        };
        document.add(new Paragraph()
                .add(new Text("Status:  ").setFont(bold).setFontSize(10))
                .add(new Text(payment.getStatus().name())
                        .setFont(bold).setFontSize(10).setFontColor(statusColor))
                .setMarginBottom(2));

        if (payment.getPaidAt() != null) {
            document.add(metaLine("Paid At:",
                    payment.getPaidAt().format(DATE_FMT), bold, regular));
        }
        if (payment.getPayherePaymentId() != null) {
            document.add(metaLine("Transaction ID:",
                    payment.getPayherePaymentId(), bold, regular));
        }

        // ── Footer ───────────────────────────────────────────────────────────
        document.add(new com.itextpdf.layout.element.Div()
                .setHeight(1)
                .setWidth(UnitValue.createPercentValue(100))
                .setBackgroundColor(BORDER)
                .setMarginTop(28)
                .setMarginBottom(14));

        document.add(new Paragraph(
                "Thank you for choosing Blanche Bridal. "
                        + "We look forward to being part of your special day.")
                .setFont(regular)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(MUTED_FOREGROUND));

        document.close();
        return baos.toByteArray();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Paragraph metaLine(String label, String value,
                               PdfFont bold, PdfFont regular) {
        return new Paragraph()
                .add(new Text(label + "  ").setFont(bold).setFontSize(10))
                .add(new Text(value).setFont(regular).setFontSize(10))
                .setMarginBottom(2);
    }

    private Cell cell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(10))
                .setPadding(5);
    }

    private String formatAmount(BigDecimal amount) {
        return "%,.2f".formatted(amount);
    }

    private ReceiptResponse toResponse(Receipt r) {
        return ReceiptResponse.builder()
                .id(r.getId())
                .receiptNumber(r.getReceiptNumber())
                .pdfUrl(r.getPdfUrl())
                .issuedAt(r.getIssuedAt())
                .orderId(r.getOrder().getId())
                .totalAmount(r.getOrder().getTotalAmount())
                .build();
    }
}
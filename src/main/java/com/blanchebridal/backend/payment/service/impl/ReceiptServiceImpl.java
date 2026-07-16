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
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.ColorConstants;
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
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

    private final ReceiptRepository receiptRepository;
    private final Cloudinary cloudinary;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    // ─── Public API ──────────────────────────────────────────────────────────

    @Override
    @Transactional
    public Receipt generateReceipt(Order order, Payment payment) {
        // Idempotency: if receipt already exists for this order, return it
        return receiptRepository.findByOrder_Id(order.getId())
                .orElseGet(() -> {
                    try {
                        String receiptNumber = buildNextReceiptNumber();
                        String storageKey    = UUID.randomUUID().toString();
                        byte[] pdfBytes      = buildPdf(order, payment, receiptNumber);

                        Map<String, Object> uploadResult = uploadToCloudinary(pdfBytes, storageKey);
                        String pdfUrl   = (String) uploadResult.get("secure_url");
                        Integer version = (Integer) uploadResult.get("version");

                        Receipt receipt = Receipt.builder()
                                .order(order)
                                .payment(payment)
                                .receiptNumber(receiptNumber)
                                .storageKey(storageKey)
                                .storageVersion(version)
                                .pdfUrl(pdfUrl)
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
        return getAuthorizedReceipt(receiptId, requestingUserId, role).getPdfUrl();
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadReceiptPdf(UUID receiptId, UUID requestingUserId, String role)
            throws IOException, InterruptedException {

        Receipt receipt = getAuthorizedReceipt(receiptId, requestingUserId, role);

        // Signed URL for an authenticated-type resource. Cloudinary's signature
        // for authenticated raw resources is computed including the resource's
        // version, so it must be included here or the signature is invalid and
        // Cloudinary returns 401 at fetch time -- captured once at upload time
        // and stored as receipt.storageVersion.
        String signedUrl = cloudinary.url()
                .resourceType("raw")
                .type("authenticated")
                .signed(true)
                .version(receipt.getStorageVersion())
                .format("pdf")
                .generate("receipts/" + receipt.getStorageKey());

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(signedUrl)).GET().build();
        HttpResponse<byte[]> response =
                client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            log.error("Cloudinary signed fetch failed for receipt {}: HTTP {}",
                    receiptId, response.statusCode());
            throw new RuntimeException("Failed to retrieve receipt PDF from storage");
        }

        return response.body();
    }

    @Override
    @Transactional(readOnly = true)
    public String getReceiptFilename(UUID receiptId, UUID requestingUserId, String role) {
        Receipt receipt = getAuthorizedReceipt(receiptId, requestingUserId, role);
        return receipt.getReceiptNumber() + ".pdf";
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
                .setFontSize(22)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(4));

        document.add(new Paragraph("Official Receipt")
                .setFont(regular)
                .setFontSize(12)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginBottom(20));

        // ── Receipt meta ─────────────────────────────────────────────────────
        document.add(metaLine("Receipt No:", receiptNumber, bold, regular));
        document.add(metaLine("Issue Date:",
                LocalDateTime.now().format(DATE_FMT), bold, regular));
        document.add(metaLine("Order ID:",
                order.getId().toString(), bold, regular));
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
                    .add(new Paragraph(col).setFont(bold).setFontSize(10))
                    .setBackgroundColor(ColorConstants.LIGHT_GRAY)
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

        // Total row
        table.addCell(new Cell(1, 4)
                .add(new Paragraph("TOTAL").setFont(bold).setFontSize(10))
                .setBorderTop(null)
                .setPadding(6));
        table.addCell(new Cell()
                .add(new Paragraph(formatAmount(order.getTotalAmount()))
                        .setFont(bold).setFontSize(10))
                .setPadding(6));

        document.add(table);

        // ── Payment info ─────────────────────────────────────────────────────
        document.add(new Paragraph("\nPayment Details")
                .setFont(bold).setFontSize(12).setMarginTop(14));

        document.add(metaLine("Method:",
                payment.getMethod().name(), bold, regular));
        document.add(metaLine("Status:",
                payment.getStatus().name(), bold, regular));

        if (payment.getPaidAt() != null) {
            document.add(metaLine("Paid At:",
                    payment.getPaidAt().format(DATE_FMT), bold, regular));
        }
        if (payment.getPayherePaymentId() != null) {
            document.add(metaLine("Transaction ID:",
                    payment.getPayherePaymentId(), bold, regular));
        }

        // ── Footer ───────────────────────────────────────────────────────────
        document.add(new Paragraph(
                "\nThank you for choosing Blanche Bridal. "
                        + "We look forward to being part of your special day.")
                .setFont(regular)
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER)
                .setFontColor(ColorConstants.GRAY)
                .setMarginTop(24));

        document.close();
        return baos.toByteArray();
    }

    // ─── Cloudinary upload ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private Map<String, Object> uploadToCloudinary(byte[] pdfBytes,
                                                   String storageKey) throws Exception {
        // "format", "pdf" is what makes the resulting secure_url end in
        // .pdf (e.g. .../receipts/<storageKey>.pdf) instead of a bare
        // extensionless public_id. Without it, browsers have no way to
        // recognize the file as a PDF and just force a generic download
        // dialog with no extension -- confirmed by pasting the raw URL
        // directly in a browser tab.
        //
        // public_id is now a random storageKey rather than the sequential
        // receiptNumber -- keeps the Cloudinary path unguessable even
        // though receiptNumber (e.g. RCP-2026-00002) is shown to the
        // customer and increments predictably.
        //
        // type "authenticated" makes this a genuinely access-controlled
        // resource -- unlike "upload" type, where Cloudinary's account-level
        // "no unauthenticated PDF/ZIP delivery" restriction blocks ALL
        // delivery regardless of signing. Return the full upload result
        // (not just secure_url) so the caller can also capture "version"
        // (kept on the Receipt row for reference, though privateDownloadUrl
        // no longer needs it directly).
        return cloudinary.uploader().upload(
                pdfBytes,
                ObjectUtils.asMap(
                        "resource_type", "raw",
                        "type",          "authenticated",
                        "folder",        "receipts",
                        "public_id",     storageKey,
                        "format",        "pdf",
                        "overwrite",     true
                ));
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
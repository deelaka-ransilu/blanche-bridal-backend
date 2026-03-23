package edu.bridalshop.backend.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PayloadSanitizerTest {

    private final PayloadSanitizer sanitizer = new PayloadSanitizer();

    @Test
    void sanitizeEmail_shouldLowercaseAndTrim() {
        String result = sanitizer.sanitizeEmail("  JOHN@GMAIL.COM  ");
        assertEquals("john@gmail.com", result);
    }

    @Test
    void sanitizeEmail_shouldReturnNull_whenInputIsNull() {
        assertNull(sanitizer.sanitizeEmail(null));
    }

    @Test
    void sanitizeText_shouldStripHtmlTags() {
        String result = sanitizer.sanitizeText("<script>alert(1)</script>John");
        assertEquals("John", result);
    }

    @Test
    void sanitizeText_shouldReturnNull_whenInputIsNull() {
        assertNull(sanitizer.sanitizeText(null));
    }
}
package edu.bridalshop.backend.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class PayloadSanitizer {

    // Strip ALL HTML — plain text only
    // "  <script>alert(1)</script>John  " → "John"
    public String sanitizeText(String input) {
        if (input == null) return null;
        return Jsoup.clean(input.trim(), Safelist.none());
    }

    // Normalize email — trim + lowercase
    // "  JOHN@GMAIL.COM  " → "john@gmail.com"
    public String sanitizeEmail(String email) {
        if (email == null) return null;
        return Jsoup.clean(email.trim().toLowerCase(), Safelist.none());
    }

    // Allow basic formatting for longer text (notes, descriptions)
    // Allows: <b> <i> <p> <br> — strips everything else
    public String sanitizeRichText(String input) {
        if (input == null) return null;
        return Jsoup.clean(input.trim(), Safelist.basic());
    }
}
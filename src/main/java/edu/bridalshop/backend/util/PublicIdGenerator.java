package edu.bridalshop.backend.util;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import org.springframework.stereotype.Component;

@Component
public class PublicIdGenerator {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

    private String generate(String prefix) {
        return prefix + "_" + NanoIdUtils.randomNanoId(
                NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
                ALPHABET,
                12
        );
    }

    public String forUser()     { return generate("usr"); }
    public String forOrder()    { return generate("ord"); }
    public String forBooking()  { return generate("bkg"); }
    public String forDress()    { return generate("drs"); }
    public String forInquiry()  { return generate("inq"); }
}
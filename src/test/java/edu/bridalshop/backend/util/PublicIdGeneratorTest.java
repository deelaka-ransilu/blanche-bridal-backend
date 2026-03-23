package edu.bridalshop.backend.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PublicIdGeneratorTest {

    private final PublicIdGenerator generator = new PublicIdGenerator();

    @Test
    void forUser_shouldStartWithUsr() {
        String id = generator.forUser();
        assertTrue(id.startsWith("usr_"));
    }

    @Test
    void forUser_shouldGenerateUniqueIds() {
        String id1 = generator.forUser();
        String id2 = generator.forUser();
        assertNotEquals(id1, id2);
    }

    @Test
    void forDress_shouldStartWithDrs() {
        assertTrue(generator.forDress().startsWith("drs_"));
    }

    @Test
    void forOrder_shouldStartWithOrd() {
        assertTrue(generator.forOrder().startsWith("ord_"));
    }
}
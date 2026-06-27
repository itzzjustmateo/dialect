package com.vomlabs.dialect.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TextSanitizerTest {

    @Test
    void testSanitizeNull() {
        assertEquals("", TextSanitizer.sanitize(null));
    }

    @Test
    void testSanitizeNormalText() {
        assertEquals("hello world", TextSanitizer.sanitize("hello world"));
    }

    @Test
    void testSanitizeControlCharacters() {
        String input = "hel\u0000lo\u0007world";
        assertEquals("helloworld", TextSanitizer.sanitize(input));
    }

    @Test
    void testTruncateLongMessage() {
        StringBuilder longMsg = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            longMsg.append("a");
        }
        String result = TextSanitizer.sanitize(longMsg.toString());
        assertTrue(result.length() <= 1024);
    }

    @Test
    void testTruncateExplicit() {
        assertEquals("hello", TextSanitizer.truncate("hello world", 5));
        assertEquals("", TextSanitizer.truncate(null, 5));
    }

    @Test
    void testIsTooLong() {
        StringBuilder longMsg = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            longMsg.append("a");
        }
        assertTrue(TextSanitizer.isTooLong(longMsg.toString()));
        assertFalse(TextSanitizer.isTooLong("short"));
    }

    @Test
    void testSanitizeExcessiveWhitespace() {
        assertEquals("a b", TextSanitizer.sanitize("a   b"));
    }
}

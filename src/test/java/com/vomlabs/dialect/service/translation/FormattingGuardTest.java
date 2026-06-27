package com.vomlabs.dialect.service.translation;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FormattingGuardTest {

    @Test
    void testTokenizeMinecraftColors() {
        FormattingGuard guard = new FormattingGuard();
        String result = guard.tokenize("&aHello &cWorld");
        assertTrue(result.contains("__TAG_0__"));
        assertTrue(result.contains("__TAG_1__"));
        assertFalse(result.contains("&a"));
        assertFalse(result.contains("&c"));
    }

    @Test
    void testTokenizeSectionColors() {
        FormattingGuard guard = new FormattingGuard();
        String result = guard.tokenize("§aHello §cWorld");
        assertTrue(result.contains("__TAG_0__"));
        assertTrue(result.contains("__TAG_1__"));
    }

    @Test
    void testTokenizeMiniMessage() {
        FormattingGuard guard = new FormattingGuard();
        String result = guard.tokenize("<red>Hello</red> <bold>World</bold>");
        assertTrue(result.contains("__TAG_0__"));
        assertTrue(result.contains("__TAG_1__"));
        assertTrue(result.contains("__TAG_2__"));
        assertFalse(result.contains("<red>"));
    }

    @Test
    void testTokenizeMentions() {
        FormattingGuard guard = new FormattingGuard();
        String result = guard.tokenize("Hello @PlayerName how are you?");
        assertTrue(result.contains("__TAG_0__"));
        assertFalse(result.contains("@PlayerName"));
    }

    @Test
    void testRestoreAfterTokenization() {
        FormattingGuard guard = new FormattingGuard();
        String original = "&aHello @PlayerName <red>World</red>";
        String tokenized = guard.tokenize(original);
        String restored = guard.restore(tokenized.replace("World", "Mundo"));
        assertEquals("&aHello @PlayerName <red>Mundo</red>", restored);
    }

    @Test
    void testCompleteRoundTrip() {
        FormattingGuard guard = new FormattingGuard();
        String original = "&aHello <red>World</red> @PlayerName";
        String tokenized = guard.tokenize(original);
        String restored = guard.restore(tokenized);
        assertEquals(original, restored);
    }

    @Test
    void testTokenizeEmpty() {
        FormattingGuard guard = new FormattingGuard();
        assertEquals("", guard.tokenize(""));
        assertNull(guard.tokenize(null));
    }

    @Test
    void testRestoreNull() {
        FormattingGuard guard = new FormattingGuard();
        assertNull(guard.restore(null));
    }

    @Test
    void testGetTokens() {
        FormattingGuard guard = new FormattingGuard();
        guard.tokenize("&aHello");
        assertEquals(1, guard.getTokens().size());
        assertEquals("&a", guard.getTokens().get(0).original());
        assertTrue(guard.getTokens().get(0).placeholder().startsWith("__TAG_"));
    }

    @Test
    void testClear() {
        FormattingGuard guard = new FormattingGuard();
        guard.tokenize("&aHello &bWorld");
        assertEquals(2, guard.getTokens().size());
        guard.clear();
        assertEquals(0, guard.getTokens().size());
    }

    @Test
    void testMultipleRestore() {
        FormattingGuard guard = new FormattingGuard();
        String original = "&aTest";
        String tokenized = guard.tokenize(original);
        String intermediate = tokenized.replace("Test", "Prueba");
        String restored = guard.restore(intermediate);
        assertEquals("&aPrueba", restored);

        guard.clear();
        String secondOriginal = "&bAnother";
        String secondTokenized = guard.tokenize(secondOriginal);
        String secondRestored = guard.restore(secondTokenized);
        assertEquals("&bAnother", secondRestored);
    }

    @Test
    void testStaticProcess() {
        String result = FormattingGuard.process("&aHello World", translated -> translated.replace("World", "Mundo"));
        assertEquals("&aHello Mundo", result);
    }
}

package com.vomlabs.dialect.model;

import org.junit.jupiter.api.Test;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LanguageTest {

    @Test
    void testFromValidCode() {
        Optional<Language> lang = Language.fromCode("en");
        assertTrue(lang.isPresent());
        assertEquals("en", lang.get().code());
    }

    @Test
    void testFromUpperCaseCode() {
        Optional<Language> lang = Language.fromCode("FR");
        assertTrue(lang.isPresent());
        assertEquals("fr", lang.get().code());
    }

    @Test
    void testFromInvalidCode() {
        Optional<Language> lang = Language.fromCode("zz");
        assertFalse(lang.isPresent());
    }

    @Test
    void testFromNullCode() {
        Optional<Language> lang = Language.fromCode(null);
        assertFalse(lang.isPresent());
    }

    @Test
    void testFromBlankCode() {
        Optional<Language> lang = Language.fromCode("");
        assertFalse(lang.isPresent());
    }

    @Test
    void testIsValid() {
        assertTrue(Language.isValid("en"));
        assertTrue(Language.isValid("es"));
        assertTrue(Language.isValid("fr"));
        assertFalse(Language.isValid("zz"));
        assertFalse(Language.isValid(""));
    }

    @Test
    void testEquality() {
        Optional<Language> en1 = Language.fromCode("en");
        Optional<Language> en2 = Language.fromCode("en");
        Optional<Language> es = Language.fromCode("es");

        assertTrue(en1.isPresent() && en2.isPresent());
        assertEquals(en1.get(), en2.get());
        assertNotEquals(en1.get(), es.get());
    }

    @Test
    void testHashCode() {
        Optional<Language> en = Language.fromCode("en");
        Optional<Language> same = Language.fromCode("EN");
        assertTrue(en.isPresent() && same.isPresent());
        assertEquals(en.get().hashCode(), same.get().hashCode());
    }

    @Test
    void testInvalidCodeThrows() {
        assertThrows(IllegalArgumentException.class, () -> new Language("", "Empty"));
        assertThrows(IllegalArgumentException.class, () -> new Language("abc", "Too Long"));
        assertThrows(IllegalArgumentException.class, () -> new Language(null, "Null"));
    }

    @Test
    void testConstructorNormalizesToLowercase() {
        Language lang = new Language("EN", "English");
        assertEquals("en", lang.code());
    }

    @Test
    void testKnownLanguagesPresent() {
        assertTrue(Language.fromCode("en").isPresent());
        assertTrue(Language.fromCode("es").isPresent());
        assertTrue(Language.fromCode("fr").isPresent());
        assertTrue(Language.fromCode("de").isPresent());
        assertTrue(Language.fromCode("zh").isPresent());
        assertTrue(Language.fromCode("ja").isPresent());
        assertTrue(Language.fromCode("ko").isPresent());
        assertTrue(Language.fromCode("ar").isPresent());
    }
}

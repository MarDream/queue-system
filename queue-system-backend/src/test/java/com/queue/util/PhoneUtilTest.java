package com.queue.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneUtilTest {

    @Test
    void normalizeShouldStripNonDigits() {
        assertEquals("13800138000", PhoneUtil.normalize(" 138-0013-8000 "));
    }

    @Test
    void maskShouldUseNormalizedPhone() {
        assertEquals("138****8000", PhoneUtil.mask("138 0013 8000"));
    }

    @Test
    void extractLast4ShouldUseNormalizedPhone() {
        assertEquals("8000", PhoneUtil.extractLast4("138-0013-8000"));
    }

    @Test
    void looksLikeCompletePhoneShouldRejectMaskedValue() {
        assertTrue(PhoneUtil.looksLikeCompletePhone("13800138000"));
        assertFalse(PhoneUtil.looksLikeCompletePhone("138****8000"));
        assertFalse(PhoneUtil.looksLikeCompletePhone("abc"));
    }
}

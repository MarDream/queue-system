package com.queue.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PhoneCryptoServiceTest {

    private PhoneCryptoService phoneCryptoService;

    @BeforeEach
    void setUp() {
        phoneCryptoService = new PhoneCryptoService();
        ReflectionTestUtils.setField(phoneCryptoService, "configuredMasterKey", "unit-test-phone-key");
        ReflectionTestUtils.setField(phoneCryptoService, "fallbackMasterKey", "unit-test-fallback-key");
        ReflectionTestUtils.setField(phoneCryptoService, "keyVersion", 3);
        phoneCryptoService.init();
    }

    @Test
    void protectShouldReturnCipherHashAndMaskedValue() {
        PhoneCryptoService.ProtectedPhone protectedPhone = phoneCryptoService.protect("13800138000");

        assertEquals("13800138000", protectedPhone.normalizedPhone());
        assertEquals("138****8000", protectedPhone.masked());
        assertEquals("8000", protectedPhone.last4());
        assertEquals(3, protectedPhone.keyVersion());
        assertNotNull(protectedPhone.ciphertext());
        assertNotNull(protectedPhone.hash());
        assertNotEquals("13800138000", protectedPhone.ciphertext());
    }

    @Test
    void encryptAndDecryptShouldRoundTripNormalizedPhone() {
        String ciphertext = phoneCryptoService.encrypt("138-0013-8000");

        assertEquals("13800138000", phoneCryptoService.decrypt(ciphertext));
    }

    @Test
    void hashShouldBeStableForEquivalentInput() {
        String first = phoneCryptoService.hash("13800138000");
        String second = phoneCryptoService.hash("138-0013-8000");

        assertEquals(first, second);
    }
}

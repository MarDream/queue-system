package com.queue.service;

import com.queue.util.PhoneUtil;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class PhoneCryptoService {

    private static final String AES_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LENGTH = 12;

    @Value("${app.phone-encryption.master-key:}")
    private String configuredMasterKey;

    @Value("${jwt.secret:queue-system-secret-key-2024-queue-management-system-secure-key}")
    private String fallbackMasterKey;

    @Value("${app.phone-encryption.key-version:1}")
    private Integer keyVersion;

    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKeySpec secretKeySpec;

    @PostConstruct
    public void init() {
        String effectiveKey = configuredMasterKey != null && !configuredMasterKey.isBlank()
                ? configuredMasterKey.trim()
                : fallbackMasterKey;
        this.secretKeySpec = new SecretKeySpec(sha256(effectiveKey.getBytes(StandardCharsets.UTF_8)), "AES");
    }

    public ProtectedPhone protect(String rawPhone) {
        String normalizedPhone = PhoneUtil.normalize(rawPhone);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return new ProtectedPhone(null, null, null, null, null, keyVersionValue());
        }
        return new ProtectedPhone(
                normalizedPhone,
                encrypt(normalizedPhone),
                hash(normalizedPhone),
                PhoneUtil.mask(normalizedPhone),
                PhoneUtil.extractLast4(normalizedPhone),
                keyVersionValue()
        );
    }

    public String encrypt(String rawPhone) {
        String normalizedPhone = PhoneUtil.normalize(rawPhone);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return normalizedPhone;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(normalizedPhone.getBytes(StandardCharsets.UTF_8));

            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("手机号加密失败", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return ciphertext;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(ciphertext);
            if (payload.length <= IV_LENGTH) {
                throw new IllegalArgumentException("Invalid ciphertext payload");
            }
            byte[] iv = new byte[IV_LENGTH];
            byte[] encrypted = new byte[payload.length - IV_LENGTH];
            System.arraycopy(payload, 0, iv, 0, IV_LENGTH);
            System.arraycopy(payload, IV_LENGTH, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(AES_ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("手机号解密失败", e);
        }
    }

    public String hash(String rawPhone) {
        String normalizedPhone = PhoneUtil.normalize(rawPhone);
        if (normalizedPhone == null || normalizedPhone.isBlank()) {
            return normalizedPhone;
        }
        return toHex(sha256(normalizedPhone.getBytes(StandardCharsets.UTF_8)));
    }

    public int keyVersionValue() {
        return keyVersion == null || keyVersion <= 0 ? 1 : keyVersion;
    }

    private byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 计算失败", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    public record ProtectedPhone(
            String normalizedPhone,
            String ciphertext,
            String hash,
            String masked,
            String last4,
            Integer keyVersion
    ) {
    }
}

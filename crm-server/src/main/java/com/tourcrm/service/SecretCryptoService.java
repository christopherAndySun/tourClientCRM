package com.tourcrm.service;

import com.tourcrm.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class SecretCryptoService {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKeySpec keySpec;

    public SecretCryptoService(
            @Value("${app.settings-crypto-key:${CRM_SETTINGS_CRYPTO_KEY:}}") String settingsCryptoKey,
            @Value("${app.jwt-secret:tour-client-crm-default-secret}") String jwtSecret
    ) {
        this.keySpec = new SecretKeySpec(sha256(StringUtils.hasText(settingsCryptoKey) ? settingsCryptoKey : jwtSecret), "AES");
    }

    public String encrypt(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = value.trim();
        if (isEncrypted(cleaned)) {
            return cleaned;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(cleaned.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherText.length);
            buffer.put(iv);
            buffer.put(cipherText);
            return PREFIX + Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception error) {
            throw new BusinessException("敏感配置加密失败，请检查系统配置");
        }
    }

    public String decrypt(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        String cleaned = value.trim();
        if (!isEncrypted(cleaned)) {
            return cleaned;
        }
        try {
            byte[] payload = Base64.getDecoder().decode(cleaned.substring(PREFIX.length()));
            ByteBuffer buffer = ByteBuffer.wrap(payload);
            byte[] iv = new byte[IV_LENGTH];
            buffer.get(iv);
            byte[] cipherText = new byte[buffer.remaining()];
            buffer.get(cipherText);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new BusinessException("敏感配置解密失败，请检查系统密钥是否变更");
        }
    }

    public boolean isEncrypted(String value) {
        return StringUtils.hasText(value) && value.trim().startsWith(PREFIX);
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new BusinessException("敏感配置密钥初始化失败");
        }
    }
}

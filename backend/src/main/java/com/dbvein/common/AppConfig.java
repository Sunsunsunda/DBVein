package com.dbvein.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Configuration
public class AppConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                        .allowedMethods("*");
            }
        };
    }

    @Bean
    public PasswordCipher passwordCipher(@Value("${dbvein.crypto-key}") String key) {
        return new PasswordCipher(key);
    }

    public static class PasswordCipher {
        private final SecretKeySpec keySpec;

        public PasswordCipher(String key) {
            byte[] bytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] normalized = new byte[16];
            System.arraycopy(bytes, 0, normalized, 0, Math.min(bytes.length, 16));
            this.keySpec = new SecretKeySpec(normalized, "AES");
        }

        public String encrypt(String plain) {
            if (plain == null || plain.isBlank()) {
                return plain;
            }
            try {
                Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                cipher.init(Cipher.ENCRYPT_MODE, keySpec);
                return Base64.getEncoder().encodeToString(cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8)));
            } catch (Exception e) {
                throw new IllegalStateException("Failed to encrypt password");
            }
        }

        public String decrypt(String encrypted) {
            if (encrypted == null || encrypted.isBlank()) {
                return encrypted;
            }
            try {
                Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
                cipher.init(Cipher.DECRYPT_MODE, keySpec);
                return new String(cipher.doFinal(Base64.getDecoder().decode(encrypted)), StandardCharsets.UTF_8);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to decrypt password", e);
            }
        }

        public boolean canDecrypt(String encrypted) {
            if (encrypted == null || encrypted.isBlank()) {
                return false;
            }
            try {
                decrypt(encrypted);
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}

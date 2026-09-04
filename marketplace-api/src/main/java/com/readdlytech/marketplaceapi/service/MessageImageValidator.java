package com.readdlytech.marketplaceapi.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

@Component
public class MessageImageValidator {
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private final long maxFileSize;

    public MessageImageValidator(@Value("${app.storage.max-file-size-bytes}") long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Images cannot be empty");
        if (file.getSize() > maxFileSize) throw new IllegalArgumentException("Each image must be 5 MB or smaller");
        String contentType = file.getContentType();
        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are accepted");
        }
        try (InputStream input = file.getInputStream()) {
            byte[] header = input.readNBytes(12);
            if (!matchesSignature(contentType, header)) {
                throw new IllegalArgumentException("The selected file is not a valid " + contentType.substring(6).toUpperCase() + " image");
            }
        } catch (IOException exception) {
            throw new IllegalArgumentException("The selected image could not be read", exception);
        }
    }

    private boolean matchesSignature(String contentType, byte[] header) {
        return switch (contentType) {
            case "image/jpeg" -> header.length >= 3 && unsigned(header[0]) == 0xff && unsigned(header[1]) == 0xd8
                    && unsigned(header[2]) == 0xff;
            case "image/png" -> header.length >= 8 && unsigned(header[0]) == 0x89 && header[1] == 0x50
                    && header[2] == 0x4e && header[3] == 0x47 && header[4] == 0x0d && header[5] == 0x0a
                    && header[6] == 0x1a && header[7] == 0x0a;
            case "image/webp" -> header.length >= 12 && text(header, 0, "RIFF") && text(header, 8, "WEBP");
            default -> false;
        };
    }

    private boolean text(byte[] bytes, int offset, String expected) {
        if (bytes.length < offset + expected.length()) return false;
        for (int index = 0; index < expected.length(); index++) {
            if (bytes[offset + index] != (byte) expected.charAt(index)) return false;
        }
        return true;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }
}

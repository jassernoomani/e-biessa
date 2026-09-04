package com.readdlytech.marketplaceapi.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "cloudinary.enabled", havingValue = "false", matchIfMissing = true)
public class LocalImageStorageService implements ImageStorageService {
    private final Path directory;

    public LocalImageStorageService(@Value("${app.storage.local-directory}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    public StoredImage upload(MultipartFile file) {
        try {
            Files.createDirectories(directory);
            String extension = switch (file.getContentType() == null ? "" : file.getContentType()) {
                case "image/jpeg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/webp" -> ".webp";
                default -> ".img";
            };
            String filename = UUID.randomUUID() + extension;
            Path target = directory.resolve(filename).normalize();
            if (!target.startsWith(directory)) throw new IllegalArgumentException("Invalid image filename");
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredImage("/uploads/" + filename, filename);
        } catch (IOException exception) {
            throw new IllegalStateException("Image upload failed", exception);
        }
    }

    @Override
    public void delete(String publicId, String url) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            Path target = directory.resolve(publicId).normalize();
            if (target.startsWith(directory)) Files.deleteIfExists(target);
        } catch (IOException exception) {
            throw new IllegalStateException("Image deletion failed", exception);
        }
    }
}

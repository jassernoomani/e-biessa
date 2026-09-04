package com.readdlytech.marketplaceapi.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cloudinary.enabled", havingValue = "true")
public class CloudinaryImageStorageService implements ImageStorageService {
    private final Cloudinary cloudinary;

    @Override
    public StoredImage upload(MultipartFile file) {
        try {
            Map<?, ?> result = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "marketplace/parts",
                    "resource_type", "image"));
            return new StoredImage(result.get("secure_url").toString(), result.get("public_id").toString());
        } catch (IOException exception) {
            throw new IllegalStateException("Image upload failed", exception);
        }
    }

    @Override
    public void delete(String publicId, String url) {
        if (publicId == null || publicId.isBlank()) return;
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
        } catch (IOException exception) {
            throw new IllegalStateException("Image deletion failed", exception);
        }
    }
}

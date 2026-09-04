package com.readdlytech.marketplaceapi.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ImageStorageService {
    StoredImage upload(MultipartFile file);
    void delete(String publicId, String url);
}

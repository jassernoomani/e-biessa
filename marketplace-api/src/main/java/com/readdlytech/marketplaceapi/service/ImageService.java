package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.ImageResponse;
import com.readdlytech.marketplaceapi.entity.Image;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.repository.ImageRepository;
import com.readdlytech.marketplaceapi.storage.ImageStorageService;
import com.readdlytech.marketplaceapi.storage.StoredImage;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Service
@RequiredArgsConstructor
public class ImageService {
    private static final int MAX_IMAGES_PER_LISTING = 6;
    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final ImageRepository imageRepository;
    private final ImageStorageService storageService;
    private final PartService partService;

    @Value("${app.storage.max-file-size-bytes}")
    private long maxFileSize;

    @Transactional
    public List<ImageResponse> upload(Long partId, List<MultipartFile> files, User actor) {
        Part part = partService.findPart(partId);
        partService.assertCanManage(part, actor);
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("Select at least one image");
        long existing = imageRepository.countByPartId(partId);
        if (existing + files.size() > MAX_IMAGES_PER_LISTING) {
            throw new IllegalArgumentException("A listing can contain at most six images");
        }

        for (MultipartFile file : files) {
            validate(file);
            StoredImage stored = storageService.upload(file);
            Image image = new Image();
            image.setPart(part);
            image.setUrl(stored.url());
            image.setPublicId(stored.publicId());
            image.setIsPrimary(existing == 0 && part.getImages().isEmpty());
            image.setSortOrder((int) existing);
            imageRepository.save(image);
            part.getImages().add(image);
            existing++;
        }
        return ordered(partId).stream().map(ImageResponse::from).toList();
    }

    @Transactional
    public List<ImageResponse> makePrimary(Long partId, Long imageId, User actor) {
        Part part = partService.findPart(partId);
        partService.assertCanManage(part, actor);
        Image selected = imageRepository.findByIdAndPartId(imageId, partId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        List<Image> images = ordered(partId);
        images.remove(selected);
        images.add(0, selected);
        applyOrder(images);
        return imageRepository.saveAll(images).stream().map(ImageResponse::from).toList();
    }

    @Transactional
    public List<ImageResponse> reorder(Long partId, List<Long> imageIds, User actor) {
        Part part = partService.findPart(partId);
        partService.assertCanManage(part, actor);
        List<Image> images = ordered(partId);
        Set<Long> existingIds = images.stream().map(Image::getId).collect(java.util.stream.Collectors.toSet());
        if (imageIds == null || imageIds.size() != images.size()
                || new HashSet<>(imageIds).size() != imageIds.size()
                || !existingIds.equals(new HashSet<>(imageIds))) {
            throw new IllegalArgumentException("Image order must contain every listing image exactly once");
        }
        java.util.Map<Long, Image> byId = images.stream()
                .collect(java.util.stream.Collectors.toMap(Image::getId, image -> image));
        List<Image> reordered = imageIds.stream().map(byId::get).toList();
        applyOrder(reordered);
        return imageRepository.saveAll(reordered).stream().map(ImageResponse::from).toList();
    }

    @Transactional
    public void delete(Long partId, Long imageId, User actor) {
        Part part = partService.findPart(partId);
        partService.assertCanManage(part, actor);
        Image image = imageRepository.findByIdAndPartId(imageId, partId)
                .orElseThrow(() -> new ResourceNotFoundException("Image not found"));
        boolean wasPrimary = Boolean.TRUE.equals(image.getIsPrimary());
        storageService.delete(image.getPublicId(), image.getUrl());
        imageRepository.delete(image);
        if (wasPrimary) {
            ordered(partId).stream().findFirst().ifPresent(next -> {
                next.setIsPrimary(true);
                imageRepository.save(next);
            });
        }
        List<Image> remaining = ordered(partId);
        applyOrder(remaining);
        imageRepository.saveAll(remaining);
    }

    private List<Image> ordered(Long partId) {
        return new java.util.ArrayList<>(imageRepository.findByPartIdOrderBySortOrderAscIdAsc(partId));
    }

    private void applyOrder(List<Image> images) {
        for (int index = 0; index < images.size(); index++) {
            images.get(index).setSortOrder(index);
            images.get(index).setIsPrimary(index == 0);
        }
    }

    private void validate(MultipartFile file) {
        if (file.isEmpty()) throw new IllegalArgumentException("Images cannot be empty");
        if (file.getSize() > maxFileSize) throw new IllegalArgumentException("Each image must be 5 MB or smaller");
        if (!ALLOWED_TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Only JPEG, PNG, and WebP images are accepted");
        }
    }
}

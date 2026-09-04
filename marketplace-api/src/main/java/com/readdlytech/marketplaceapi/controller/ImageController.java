package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.ImageResponse;
import com.readdlytech.marketplaceapi.dto.ImageOrderRequest;
import jakarta.validation.Valid;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.ImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/parts/{partId}/images")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SELLER','ADMIN')")
public class ImageController {
    private final ImageService imageService;
    private final CurrentUserService currentUserService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<ImageResponse>> upload(@PathVariable Long partId,
                                                       @RequestPart("files") List<MultipartFile> files,
                                                       Authentication authentication) {
        return ResponseEntity.status(201)
                .body(imageService.upload(partId, files, currentUserService.require(authentication)));
    }

    @PutMapping("/{imageId}/primary")
    public ResponseEntity<List<ImageResponse>> makePrimary(@PathVariable Long partId,
                                                            @PathVariable Long imageId,
                                                            Authentication authentication) {
        return ResponseEntity.ok(imageService.makePrimary(partId, imageId, currentUserService.require(authentication)));
    }

    @DeleteMapping("/{imageId}")
    public ResponseEntity<Void> delete(@PathVariable Long partId, @PathVariable Long imageId,
                                       Authentication authentication) {
        imageService.delete(partId, imageId, currentUserService.require(authentication));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/order")
    public ResponseEntity<List<ImageResponse>> reorder(@PathVariable Long partId,
                                                        @Valid @RequestBody ImageOrderRequest request,
                                                        Authentication authentication) {
        return ResponseEntity.ok(imageService.reorder(partId, request.imageIds(),
                currentUserService.require(authentication)));
    }
}

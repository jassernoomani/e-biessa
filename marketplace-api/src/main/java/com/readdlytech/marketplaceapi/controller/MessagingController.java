package com.readdlytech.marketplaceapi.controller;

import com.readdlytech.marketplaceapi.dto.*;
import com.readdlytech.marketplaceapi.security.CurrentUserService;
import com.readdlytech.marketplaceapi.service.MessagingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/messages/conversations")
@RequiredArgsConstructor
public class MessagingController {
    private final MessagingService messagingService;
    private final CurrentUserService currentUserService;
    private final com.readdlytech.marketplaceapi.service.RealtimeTicketService realtimeTicketService;

    @PostMapping("/realtime-ticket")
    public ResponseEntity<RealtimeTicketResponse> realtimeTicket(Authentication authentication) {
        return ResponseEntity.ok(realtimeTicketService.issue(currentUserService.require(authentication)));
    }

    @GetMapping public ResponseEntity<List<ConversationResponse>> inbox(Authentication authentication) {
        return ResponseEntity.ok(messagingService.inbox(currentUserService.require(authentication)));
    }
    @PostMapping public ResponseEntity<ConversationDetailResponse> start(@Valid @RequestBody StartConversationRequest request, Authentication authentication) {
        return ResponseEntity.status(201).body(messagingService.start(request, currentUserService.require(authentication)));
    }
    @GetMapping("/{id}") public ResponseEntity<ConversationDetailResponse> detail(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(messagingService.detail(id, currentUserService.require(authentication)));
    }
    @PostMapping(value = "/{id}/messages", consumes = MediaType.APPLICATION_JSON_VALUE) public ResponseEntity<MessageResponse> send(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request, Authentication authentication) {
        return ResponseEntity.status(201).body(messagingService.send(id, request, currentUserService.require(authentication)));
    }
    @PostMapping(value = "/{id}/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> sendWithImages(@PathVariable Long id,
                                                           @RequestParam(defaultValue = "") String message,
                                                           @RequestPart("files") List<MultipartFile> files,
                                                           Authentication authentication) {
        return ResponseEntity.status(201).body(messagingService.sendWithImages(
                id, message, files, currentUserService.require(authentication)));
    }
}

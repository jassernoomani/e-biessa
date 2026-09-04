package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.*;
import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.repository.ConversationRepository;
import com.readdlytech.marketplaceapi.repository.MessageRepository;
import com.readdlytech.marketplaceapi.repository.MessageAttachmentRepository;
import com.readdlytech.marketplaceapi.storage.ImageStorageService;
import com.readdlytech.marketplaceapi.storage.StoredImage;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.context.ApplicationEventPublisher;
import com.readdlytech.marketplaceapi.realtime.RealtimeMessageEvent;

@Service
@RequiredArgsConstructor
public class MessagingService {
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final PartService partService;
    private final MessageAttachmentRepository attachmentRepository;
    private final ImageStorageService imageStorageService;
    private final MessageImageValidator messageImageValidator;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ConversationDetailResponse start(StartConversationRequest request, User buyer) {
        if (buyer.getRole() != UserRole.BUYER) throw new AccessDeniedException("Only buyers can start a conversation");
        Part part = partService.findPart(request.partId());
        if (!partService.isPubliclyVisible(part)) throw new ResourceNotFoundException("Part not found");
        if (part.getSeller().getId().equals(buyer.getId())) throw new AccessDeniedException("You cannot message yourself");
        Conversation conversation = conversationRepository.findByPartIdAndBuyerId(part.getId(), buyer.getId())
                .orElseGet(() -> {
                    Conversation created = new Conversation();
                    created.setPart(part); created.setBuyer(buyer); created.setSeller(part.getSeller());
                    return conversationRepository.save(created);
                });
        Message message = addMessage(conversation, buyer, request.message());
        publishRealtime(conversation, message);
        return detailInternal(conversation, buyer, false);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> inbox(User user) {
        return conversationRepository.findByBuyerIdOrSellerIdOrderByUpdatedAtDesc(user.getId(), user.getId()).stream()
                .map(conversation -> summary(conversation, user)).toList();
    }

    @Transactional
    public ConversationDetailResponse detail(Long id, User user) {
        return detailInternal(requireParticipant(id, user), user, true);
    }

    @Transactional
    public MessageResponse send(Long id, SendMessageRequest request, User user) {
        Conversation conversation = requireParticipant(id, user);
        Message message = addMessage(conversation, user, request.message());
        MessageResponse response = MessageResponse.from(message);
        eventPublisher.publishEvent(new RealtimeMessageEvent(conversation.getId(), response,
                conversation.getBuyer().getId(), conversation.getSeller().getId()));
        return response;
    }

    @Transactional
    public MessageResponse sendWithImages(Long id, String body, List<MultipartFile> files, User user) {
        Conversation conversation = requireParticipant(id, user);
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("Select at least one image");
        if (files.size() > 4) throw new IllegalArgumentException("A message can contain at most four images");
        String cleanBody = body == null ? "" : body.trim();
        if (cleanBody.length() > 2000) throw new IllegalArgumentException("Messages can contain at most 2000 characters");
        files.forEach(messageImageValidator::validate);

        Message message = addMessage(conversation, user, cleanBody);
        List<StoredImage> storedImages = new ArrayList<>();
        try {
            for (MultipartFile file : files) {
                StoredImage stored = imageStorageService.upload(file);
                storedImages.add(stored);
                MessageAttachment attachment = new MessageAttachment();
                attachment.setMessage(message);
                attachment.setUrl(stored.url());
                attachment.setPublicId(stored.publicId());
                attachment.setOriginalName(safeOriginalName(file.getOriginalFilename()));
                attachment.setContentType(file.getContentType());
                attachment.setSizeBytes(file.getSize());
                message.getAttachments().add(attachmentRepository.save(attachment));
            }
            MessageResponse response = MessageResponse.from(message);
            eventPublisher.publishEvent(new RealtimeMessageEvent(conversation.getId(), response,
                    conversation.getBuyer().getId(), conversation.getSeller().getId()));
            return response;
        } catch (RuntimeException exception) {
            storedImages.forEach(stored -> {
                try { imageStorageService.delete(stored.publicId(), stored.url()); }
                catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            });
            throw exception;
        }
    }

    private Message addMessage(Conversation conversation, User sender, String body) {
        Message message = new Message();
        message.setConversation(conversation); message.setSender(sender); message.setBody(body.trim());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        return messageRepository.save(message);
    }

    private void publishRealtime(Conversation conversation, Message message) {
        eventPublisher.publishEvent(new RealtimeMessageEvent(conversation.getId(), MessageResponse.from(message),
                conversation.getBuyer().getId(), conversation.getSeller().getId()));
    }

    private ConversationDetailResponse detailInternal(Conversation conversation, User user, boolean markRead) {
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        if (markRead) {
            LocalDateTime now = LocalDateTime.now();
            messages.stream().filter(message -> !message.getSender().getId().equals(user.getId()) && message.getReadAt() == null)
                    .forEach(message -> message.setReadAt(now));
        }
        return new ConversationDetailResponse(summary(conversation, user), messages.stream().map(MessageResponse::from).toList());
    }

    private Conversation requireParticipant(Long id, User user) {
        Conversation conversation = conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        if (!conversation.getBuyer().getId().equals(user.getId()) && !conversation.getSeller().getId().equals(user.getId()))
            throw new AccessDeniedException("You are not part of this conversation");
        return conversation;
    }

    private ConversationResponse summary(Conversation conversation, User user) {
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId());
        Message last = messages.isEmpty() ? null : messages.getLast();
        String imageUrl = conversation.getPart().getImages().stream()
                .sorted(Comparator.comparing(Image::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .map(Image::getUrl).findFirst().orElse(null);
        String otherParty = conversation.getBuyer().getId().equals(user.getId())
                ? conversation.getSeller().getFullName() : conversation.getBuyer().getFullName();
        return new ConversationResponse(conversation.getId(), conversation.getPart().getId(), conversation.getPart().getTitle(), imageUrl,
                conversation.getBuyer().getId(), conversation.getBuyer().getFullName(), conversation.getSeller().getId(),
                conversation.getSeller().getFullName(), otherParty, last == null ? null : preview(last),
                last == null ? conversation.getUpdatedAt() : last.getCreatedAt(),
                messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(conversation.getId(), user.getId()));
    }

    private String preview(Message message) {
        if (message.getBody() != null && !message.getBody().isBlank()) return message.getBody();
        int count = message.getAttachments().size();
        return count == 1 ? "Photo" : count + " photos";
    }

    private String safeOriginalName(String name) {
        if (name == null || name.isBlank()) return "image";
        String normalized = name.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        return filename.length() > 255 ? filename.substring(filename.length() - 255) : filename;
    }
}

package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {
}

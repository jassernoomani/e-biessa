package com.readdlytech.marketplaceapi.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "message_attachments", indexes = @Index(name = "idx_message_attachment_message", columnList = "message_id"))
@Data
@NoArgsConstructor
public class MessageAttachment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false) @JoinColumn(name = "message_id", nullable = false)
    private Message message;
    @Column(nullable = false, length = 1000)
    private String url;
    @Column(name = "public_id", length = 500)
    private String publicId;
    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;
    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;
    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;
}

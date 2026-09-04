package com.readdlytech.marketplaceapi.dto;

import com.readdlytech.marketplaceapi.entity.AdminAuditLog;
import java.time.LocalDateTime;

public record AdminAuditLogResponse(Long id, Long adminId, String adminEmail, String action,
        String targetType, Long targetId, String targetLabel, String details, LocalDateTime createdAt) {
    public static AdminAuditLogResponse from(AdminAuditLog log) {
        return new AdminAuditLogResponse(log.getId(), log.getAdminId(), log.getAdminEmail(), log.getAction(),
                log.getTargetType(), log.getTargetId(), log.getTargetLabel(), log.getDetails(), log.getCreatedAt());
    }
}

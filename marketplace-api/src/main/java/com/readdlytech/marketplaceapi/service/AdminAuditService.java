package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.AdminAuditLogResponse;
import com.readdlytech.marketplaceapi.entity.AdminAuditLog;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.repository.AdminAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminAuditService {
    private final AdminAuditLogRepository repository;

    @Transactional
    public void record(User admin, String action, String targetType, Long targetId, String targetLabel, String details) {
        AdminAuditLog log = new AdminAuditLog();
        log.setAdminId(admin.getId());
        log.setAdminEmail(admin.getEmail());
        log.setAction(action);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetLabel(targetLabel);
        log.setDetails(details);
        repository.save(log);
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> latest() {
        return repository.findTop200ByOrderByCreatedAtDesc().stream().map(AdminAuditLogResponse::from).toList();
    }
}

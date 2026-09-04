package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.AdminAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AdminAuditLogRepository extends JpaRepository<AdminAuditLog, Long> {
    List<AdminAuditLog> findTop200ByOrderByCreatedAtDesc();
}

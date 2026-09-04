package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.PasswordRecoveryCode;
import com.readdlytech.marketplaceapi.entity.RecoveryChannel;
import com.readdlytech.marketplaceapi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordRecoveryCodeRepository extends JpaRepository<PasswordRecoveryCode, Long> {
    Optional<PasswordRecoveryCode> findTopByUserAndChannelOrderByCreatedAtDesc(User user, RecoveryChannel channel);
}

package com.readdlytech.marketplaceapi.repository;

import com.readdlytech.marketplaceapi.entity.AuthExchangeCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AuthExchangeCodeRepository extends JpaRepository<AuthExchangeCode, Long> {
    Optional<AuthExchangeCode> findByCodeHash(String codeHash);
}

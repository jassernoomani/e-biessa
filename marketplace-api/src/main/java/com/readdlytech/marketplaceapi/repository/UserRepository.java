package com.readdlytech.marketplaceapi.repository;


import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.entity.SellerApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    boolean existsByEmail(String email);
    List<User> findBySellerApplicationStatusOrderBySellerApplicationSubmittedAtAsc(SellerApplicationStatus status);
}

package com.readdlytech.marketplaceapi.repository;


import com.readdlytech.marketplaceapi.entity.Favorite;
import com.readdlytech.marketplaceapi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {
    List<Favorite> findByUser(User user);
    Optional<Favorite> findByUserIdAndPartId(Long userId, Long partId);
    void deleteByUserIdAndPartId(Long userId, Long partId);
    boolean existsByUserIdAndPartId(Long userId, Long partId);
    long countByPartId(Long partId);
}

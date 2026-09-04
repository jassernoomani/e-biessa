package com.readdlytech.marketplaceapi.repository;


import com.readdlytech.marketplaceapi.entity.Image;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ImageRepository extends JpaRepository<Image, Long> {
    List<Image> findByPartId(Long partId);
    List<Image> findByPartIdOrderBySortOrderAscIdAsc(Long partId);
    Optional<Image> findByIdAndPartId(Long id, Long partId);
    long countByPartId(Long partId);
}

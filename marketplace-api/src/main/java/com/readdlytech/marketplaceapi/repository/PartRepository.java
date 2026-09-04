package com.readdlytech.marketplaceapi.repository;



import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PartRepository extends JpaRepository<Part, Long>, JpaSpecificationExecutor<Part> {
    List<Part> findBySellerOrderByCreatedAtDesc(User seller);
    List<Part> findBySellerAndStatusOrderByCreatedAtDesc(User seller, PartStatus status);
    long countBySellerAndStatus(User seller, PartStatus status);
    List<Part> findByStatus(PartStatus status);
}

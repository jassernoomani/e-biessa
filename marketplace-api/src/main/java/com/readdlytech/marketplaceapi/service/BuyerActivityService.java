package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Service @RequiredArgsConstructor
public class BuyerActivityService {
    private final UserRepository users;
    private final BuyerInteractionRepository interactions;

    @Transactional
    public void record(Long buyerId, BuyerInteraction.Kind kind, Long targetId, Long categoryId, Long sellerId) {
        if (buyerId == null || targetId == null) return;
        // Serialize writes per buyer, including reset/settings, so refreshes cannot inflate history.
        User buyer = users.lockById(buyerId).orElse(null);
        if (buyer == null || buyer.getRole() != UserRole.BUYER || Boolean.TRUE.equals(buyer.getSuspended())
                || !Boolean.TRUE.equals(buyer.getPersonalizationEnabled())) return;
        LocalDateTime now = LocalDateTime.now();
        List<BuyerInteraction> rows = interactions.findByBuyerIdOrderByOccurredAtDescIdDesc(buyerId);
        BuyerInteraction row = rows.stream().filter(r -> r.getKind() == kind && r.getTargetId().equals(targetId))
                .findFirst().orElseGet(BuyerInteraction::new);
        row.setBuyer(buyer); row.setKind(kind); row.setTargetId(targetId);
        row.setCategoryId(categoryId); row.setSellerId(sellerId); row.setOccurredAt(now);
        interactions.save(row);
        // At most 200 signals and 90 days; updated signal always survives pruning.
        var keep = interactions.findByBuyerIdOrderByOccurredAtDescIdDesc(buyerId);
        for (int i = 0; i < keep.size(); i++) {
            if (i >= 200 || keep.get(i).getOccurredAt().isBefore(now.minusDays(90))) interactions.delete(keep.get(i));
        }
    }
}

package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.PartResponse;
import com.readdlytech.marketplaceapi.entity.Favorite;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.User;
import com.readdlytech.marketplaceapi.repository.FavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteService {
    private final FavoriteRepository favoriteRepository;
    private final PartService partService;

    @Transactional
    public void add(User user, Long partId) {
        Part part = partService.findPart(partId);
        if (!favoriteRepository.existsByUserIdAndPartId(user.getId(), partId)) {
            Favorite favorite = new Favorite();
            favorite.setUser(user);
            favorite.setPart(part);
            favoriteRepository.save(favorite);
        }
    }

    @Transactional
    public void remove(User user, Long partId) {
        favoriteRepository.deleteByUserIdAndPartId(user.getId(), partId);
    }

    @Transactional(readOnly = true)
    public List<PartResponse> list(User user) {
        return favoriteRepository.findByUser(user).stream()
                .map(Favorite::getPart)
                .filter(partService::isPubliclyVisible)
                .map(part -> partService.toResponse(part, user.getId()))
                .toList();
    }
}

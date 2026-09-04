package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.PartRequest;
import com.readdlytech.marketplaceapi.dto.PartResponse;
import com.readdlytech.marketplaceapi.dto.AiListingDocument;
import com.readdlytech.marketplaceapi.entity.*;
import com.readdlytech.marketplaceapi.event.AiListingChangedEvent;
import com.readdlytech.marketplaceapi.exception.ResourceNotFoundException;
import com.readdlytech.marketplaceapi.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDateTime;
import java.util.Comparator;

@Service
@RequiredArgsConstructor
public class PartService {
    private final PartRepository partRepository;
    private final CategoryRepository categoryRepository;
    private final VehicleRepository vehicleRepository;
    private final FavoriteRepository favoriteRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<PartResponse> getAllParts(Specification<Part> spec, Pageable pageable, Long userId) {
        return partRepository.findAll(spec, pageable).map(part -> toResponse(part, userId));
    }

    @Transactional
    public PartResponse getPartById(Long id, Long userId) {
        Part part = findPart(id);
        if (!isPubliclyVisible(part)) {
            throw new ResourceNotFoundException("Part not found");
        }
        part.setViews((part.getViews() == null ? 0 : part.getViews()) + 1);
        return toResponse(part, userId);
    }

    @Transactional
    public PartResponse createPart(PartRequest request, User seller) {
        if (seller.getRole() == UserRole.BUYER) {
            throw new AccessDeniedException("Activate seller mode before publishing a listing");
        }
        Part part = new Part();
        applyRequest(part, request);
        part.setSeller(seller);
        part.setStatus(PartStatus.ACTIVE);
        PartResponse response = toResponse(partRepository.save(part), seller.getId());
        publishIndexChange(response);
        return response;
    }

    @Transactional
    public PartResponse updatePart(Long id, PartRequest request, User actor) {
        Part part = findPart(id);
        assertCanManage(part, actor);
        applyRequest(part, request);
        PartResponse response = toResponse(part, actor.getId());
        publishIndexChange(response);
        return response;
    }

    @Transactional
    public PartResponse updateStatus(Long id, PartStatus status, User actor) {
        Part part = findPart(id);
        assertCanManage(part, actor);
        if (status == PartStatus.SOLD) {
            if (part.getStatus() != PartStatus.SOLD || part.getSoldAt() == null) {
                part.setSoldAt(LocalDateTime.now());
            }
        } else {
            part.setSoldAt(null);
        }
        part.setStatus(status);
        PartResponse response = toResponse(part, actor.getId());
        publishIndexChange(response);
        return response;
    }

    @Transactional
    public void deletePart(Long id, User actor) {
        Part part = findPart(id);
        assertCanManage(part, actor);
        partRepository.delete(part);
        eventPublisher.publishEvent(AiListingChangedEvent.delete(id));
    }

    @Transactional(readOnly = true)
    public List<PartResponse> getMyListings(User seller) {
        return partRepository.findBySellerOrderByCreatedAtDesc(seller).stream()
                .map(part -> toResponse(part, seller.getId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PartResponse toResponse(Part part, Long userId) {
        PartResponse response = new PartResponse();
        response.setId(part.getId());
        response.setTitle(part.getTitle());
        response.setDescription(part.getDescription());
        response.setPrice(part.getPrice());
        response.setCondition(part.getCondition());
        if (part.getCategory() != null) {
            response.setCategoryId(part.getCategory().getId());
            response.setCategoryName(part.getCategory().getName());
        }
        if (part.getSeller() != null) {
            response.setSellerId(part.getSeller().getId());
            response.setSellerName(part.getSeller().getFullName());
        }
        List<Vehicle> compatibleVehicles = effectiveVehicles(part);
        response.setCompatibleVehicles(compatibleVehicles.stream()
                .map(com.readdlytech.marketplaceapi.dto.VehicleResponse::from)
                .toList());
        if (!compatibleVehicles.isEmpty()) {
            Vehicle primaryVehicle = compatibleVehicles.getFirst();
            response.setBrand(primaryVehicle.getBrand());
            response.setModel(primaryVehicle.getModel());
            response.setYear(primaryVehicle.getYear());
        }
        response.setManufacturer(part.getManufacturer());
        response.setPartBrand(part.getPartBrand());
        response.setOemNumber(part.getOemNumber());
        response.setAlternativeOemNumbers(part.getAlternativeOemNumbers().stream().sorted().toList());
        response.setPartPosition(part.getPartPosition());
        response.setEngineCompatibility(part.getEngineCompatibility());
        response.setFuelCompatibility(part.getFuelCompatibility());
        response.setWarrantyInformation(part.getWarrantyInformation());
        response.setDeliveryAvailable(Boolean.TRUE.equals(part.getDeliveryAvailable()));
        response.setCollectionAvailable(Boolean.TRUE.equals(part.getCollectionAvailable()));
        response.setNegotiable(Boolean.TRUE.equals(part.getNegotiable()));
        response.setLocation(part.getLocation());
        response.setStatus(part.getStatus().name());
        response.setViews(part.getViews());
        response.setCreatedAt(part.getCreatedAt());
        response.setSoldAt(part.getSoldAt());
        response.setImageUrls(part.getImages().stream()
                .sorted(imageOrder())
                .map(Image::getUrl)
                .toList());
        response.setImages(part.getImages().stream()
                .sorted(imageOrder())
                .map(com.readdlytech.marketplaceapi.dto.ImageResponse::from)
                .toList());
        response.setFavoriteCount(favoriteRepository.countByPartId(part.getId()));
        response.setFavorited(userId != null && favoriteRepository.existsByUserIdAndPartId(userId, part.getId()));
        return response;
    }

    public Part findPart(Long id) {
        return partRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Part not found"));
    }

    public void assertCanManage(Part part, User actor) {
        boolean owner = part.getSeller().getId().equals(actor.getId());
        if (!owner && actor.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("You can only manage your own listings");
        }
    }

    public Specification<Part> publiclyVisibleSpecification() {
        return (root, query, cb) -> cb.equal(root.get("status"), PartStatus.ACTIVE);
    }

    public boolean isPubliclyVisible(Part part) {
        return part.getStatus() == PartStatus.ACTIVE;
    }

    public void removeFromAiIndexAfterCommit(Long listingId) {
        eventPublisher.publishEvent(AiListingChangedEvent.delete(listingId));
    }

    private Comparator<Image> imageOrder() {
        return Comparator.comparing(Image::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(Image::getId, Comparator.nullsLast(Long::compareTo));
    }

    private List<Vehicle> effectiveVehicles(Part part) {
        Set<Vehicle> vehicles = new LinkedHashSet<>(part.getCompatibleVehicles());
        if (vehicles.isEmpty() && part.getVehicle() != null) vehicles.add(part.getVehicle());
        return vehicles.stream()
                .sorted(Comparator.comparing(Vehicle::getBrand)
                        .thenComparing(Vehicle::getModel)
                        .thenComparing(Vehicle::getYear, Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private void applyRequest(Part part, PartRequest request) {
        part.setTitle(request.getTitle().trim());
        part.setDescription(request.getDescription());
        part.setPrice(request.getPrice());
        part.setCondition(request.getCondition());
        part.setManufacturer(clean(request.getManufacturer()));
        part.setPartBrand(clean(request.getPartBrand()));
        part.setOemNumber(request.getOemNumber());
        part.setAlternativeOemNumbers(request.getAlternativeOemNumbers() == null ? new LinkedHashSet<>()
                : request.getAlternativeOemNumbers().stream()
                .map(this::clean).filter(value -> value != null).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
        part.setPartPosition(clean(request.getPartPosition()));
        part.setEngineCompatibility(clean(request.getEngineCompatibility()));
        part.setFuelCompatibility(clean(request.getFuelCompatibility()));
        part.setWarrantyInformation(clean(request.getWarrantyInformation()));
        part.setDeliveryAvailable(request.isDeliveryAvailable());
        part.setCollectionAvailable(request.isCollectionAvailable());
        part.setNegotiable(request.isNegotiable());
        part.setLocation(request.getLocation());
        part.setCategory(request.getCategoryId() == null ? null : categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found")));
        Set<Long> requestedVehicleIds = request.getVehicleIds() == null ? new LinkedHashSet<>() : new LinkedHashSet<>(request.getVehicleIds());
        if (request.getVehicleId() != null) requestedVehicleIds.add(request.getVehicleId());
        List<Vehicle> vehicles = requestedVehicleIds.stream().map(vehicleId -> vehicleRepository.findById(vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found: " + vehicleId))).toList();
        part.setCompatibleVehicles(new LinkedHashSet<>(vehicles));
        part.setVehicle(vehicles.isEmpty() ? null : vehicles.getFirst());
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void publishIndexChange(PartResponse response) {
        if (PartStatus.ACTIVE.name().equals(response.getStatus())) {
            eventPublisher.publishEvent(AiListingChangedEvent.upsert(AiListingDocument.from(response)));
        } else {
            eventPublisher.publishEvent(AiListingChangedEvent.delete(response.getId()));
        }
    }
}

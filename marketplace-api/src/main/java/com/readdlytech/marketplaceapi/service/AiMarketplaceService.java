package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.*;
import com.readdlytech.marketplaceapi.entity.Part;
import com.readdlytech.marketplaceapi.entity.PartStatus;
import com.readdlytech.marketplaceapi.repository.PartRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiMarketplaceService {
    private final AiServiceClient aiServiceClient;
    private final PartRepository partRepository;
    private final PartService partService;

    @Transactional(readOnly = true)
    public int reindex() {
        List<AiListingDocument> documents = partRepository.findByStatus(PartStatus.ACTIVE).stream()
                .map(part -> AiListingDocument.from(partService.toResponse(part, null)))
                .toList();
        return aiServiceClient.replaceAll(documents);
    }

    @Transactional(readOnly = true)
    public SemanticSearchResponse search(String query, int limit, double minimumScore, AiSearchFilter filters, Long userId) {
        AiServiceSearchResponse response = aiServiceClient.search(query, limit, minimumScore, filters);
        List<Long> ids = response.hits().stream().map(AiSearchHit::listingId).toList();
        Map<Long, Part> parts = partRepository.findAllById(ids).stream()
                .filter(part -> part.getStatus() == PartStatus.ACTIVE)
                .collect(Collectors.toMap(Part::getId, Function.identity()));
        List<SemanticPartResult> results = response.hits().stream()
                .filter(hit -> parts.containsKey(hit.listingId()))
                .map(hit -> new SemanticPartResult(partService.toResponse(parts.get(hit.listingId()), userId),
                        hit.score(), hit.matchReasons() == null ? List.of() : hit.matchReasons()))
                .toList();
        GroundedAnswerResponse answer = deterministicAnswer(results);
        return new SemanticSearchResponse(response.query(), results, response.tookMs(),
                response.extractedConstraints() == null ? Map.of() : response.extractedConstraints(),
                answer);
    }

    private GroundedAnswerResponse deterministicAnswer(List<SemanticPartResult> results) {
        if (results.isEmpty()) {
            return new GroundedAnswerResponse(
                    "No active listing met all verified compatibility requirements.",
                    List.of(),
                    List.of("Try removing one optional constraint or ask a seller to confirm an alternative part."),
                    "deterministic");
        }
        PartResponse best = results.getFirst().part();
        String noun = results.size() == 1 ? "listing" : "listings";
        String summary = "Found %d active compatibility-checked %s. The strongest current match is Listing #%d: %s."
                .formatted(results.size(), noun, best.getId(), best.getTitle());
        return new GroundedAnswerResponse(
                summary,
                results.stream().limit(3).map(result -> result.part().getId()).toList(),
                List.of("Confirm the OEM or interchange number and exact fitment with the seller before buying."),
                "deterministic");
    }
}

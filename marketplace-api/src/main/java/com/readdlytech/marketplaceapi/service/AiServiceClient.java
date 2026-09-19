package com.readdlytech.marketplaceapi.service;

import com.readdlytech.marketplaceapi.dto.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

@Service
@Slf4j
public class AiServiceClient {
    private final RestClient client;
    private final boolean enabled;
    private final long cacheSeconds;
    private final long acquireTimeoutMillis;
    private final Semaphore searchSlots;
    private final Map<SearchKey, CachedSearch> searchCache = new ConcurrentHashMap<>();

    public AiServiceClient(RestClient.Builder builder,
                           @Value("${app.ai.base-url:http://127.0.0.1:8001}") String baseUrl,
                           @Value("${app.ai.token:}") String token,
                           @Value("${app.ai.enabled:false}") boolean enabled,
                           @Value("${app.ai.search.cache-seconds:120}") long cacheSeconds,
                           @Value("${app.ai.search.max-concurrent:2}") int maxConcurrent,
                           @Value("${app.ai.search.acquire-timeout-ms:500}") long acquireTimeoutMillis,
                           @Value("${app.ai.connect-timeout-seconds:3}") int connectTimeoutSeconds,
                           @Value("${app.ai.read-timeout-seconds:95}") int readTimeoutSeconds) {
        this.enabled = enabled;
        this.cacheSeconds = Math.max(0, cacheSeconds);
        this.acquireTimeoutMillis = Math.max(0, acquireTimeoutMillis);
        this.searchSlots = new Semaphore(Math.max(1, maxConcurrent), true);
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)));
        requestFactory.setReadTimeout(Duration.ofSeconds(Math.max(1, readTimeoutSeconds)));
        this.client = builder.baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .defaultHeader("X-AI-Service-Token", token)
                .build();
    }

    public boolean isEnabled() { return enabled; }

    public void upsertQuietly(AiListingDocument document) {
        if (!enabled) return;
        try {
            client.put().uri("/v1/listings/{id}", document.id())
                    .contentType(MediaType.APPLICATION_JSON).body(document).retrieve().toBodilessEntity();
            searchCache.clear();
        } catch (RestClientException exception) {
            log.warn("AI listing index update failed for listing {}: {}", document.id(), exception.getMessage());
        }
    }

    public void deleteQuietly(Long listingId) {
        if (!enabled) return;
        try {
            client.delete().uri("/v1/listings/{id}", listingId).retrieve().toBodilessEntity();
            searchCache.clear();
        } catch (RestClientException exception) {
            log.warn("AI listing index deletion failed for listing {}: {}", listingId, exception.getMessage());
        }
    }

    public int replaceAll(List<AiListingDocument> documents) {
        requireEnabled();
        try {
            IndexResponse response = client.post().uri("/v1/listings/reindex")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("listings", documents))
                    .retrieve().body(IndexResponse.class);
            searchCache.clear();
            return response == null ? 0 : response.indexed();
        } catch (RestClientException exception) {
            throw unavailable(exception);
        }
    }

    public AiServiceSearchResponse search(String query, int limit, double minimumScore, AiSearchFilter filters) {
        requireEnabled();
        SearchKey key = new SearchKey(query.toLowerCase().trim(), limit, minimumScore, filters);
        CachedSearch cached = searchCache.get(key);
        if (cached != null && !cached.expired()) return cached.response();

        boolean acquired = false;
        try {
            acquired = searchSlots.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ResponseStatusException(TOO_MANY_REQUESTS,
                        "AI search is busy. Wait a moment and try again.");
            }
            cached = searchCache.get(key);
            if (cached != null && !cached.expired()) return cached.response();
            AiServiceSearchResponse response = client.post().uri("/v1/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "limit", limit, "minimumScore", minimumScore, "filters", filters))
                    .retrieve().body(AiServiceSearchResponse.class);
            AiServiceSearchResponse safeResponse = response == null
                    ? new AiServiceSearchResponse(query, List.of(), 0, Map.of(), GroundedAnswerResponse.empty())
                    : response;
            if (cacheSeconds > 0) {
                searchCache.put(key, new CachedSearch(safeResponse, Instant.now().plusSeconds(cacheSeconds)));
                if (searchCache.size() > 1_000) searchCache.entrySet().removeIf(entry -> entry.getValue().expired());
            }
            return safeResponse;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AI search was interrupted.");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable(exception);
        } finally {
            if (acquired) searchSlots.release();
        }
    }

    public AiServiceAssistantResponse assist(String query, int limit, double minimumScore, AiSearchFilter filters,
                                              List<AiRecommendationCandidate> recommendationCandidates) {
        requireEnabled();
        boolean acquired = false;
        try {
            acquired = searchSlots.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) throw new ResponseStatusException(TOO_MANY_REQUESTS,
                    "AI assistant is busy. Wait a moment and try again.");
            AiServiceAssistantResponse response = client.post().uri("/v1/assistant")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("query", query, "limit", limit, "minimumScore", minimumScore,
                            "filters", filters, "recommendationCandidates", recommendationCandidates))
                    .retrieve().body(AiServiceAssistantResponse.class);
            return response == null
                    ? new AiServiceAssistantResponse(query, "SEARCH", List.of(), 0, Map.of(), List.of(), GroundedAnswerResponse.empty())
                    : response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "AI assistant was interrupted.");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable(exception);
        } finally {
            if (acquired) searchSlots.release();
        }
    }

    public AiVisionResponse identifyPartImage(byte[] image, MediaType mediaType) {
        requireEnabled();
        boolean acquired = false;
        try {
            acquired = searchSlots.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!acquired) {
                throw new ResponseStatusException(TOO_MANY_REQUESTS,
                        "AI image identification is busy. Wait a moment and try again.");
            }
            AiVisionResponse response = client.post().uri("/v1/vision/identify")
                    .contentType(mediaType)
                    .body(image)
                    .retrieve().body(AiVisionResponse.class);
            if (response == null) {
                throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                        "AI image identification returned no result.");
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                    "AI image identification was interrupted.");
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw unavailable(exception);
        } finally {
            if (acquired) searchSlots.release();
        }
    }

    private void requireEnabled() {
        if (!enabled) throw new ResponseStatusException(SERVICE_UNAVAILABLE,
                "AI search is not enabled. Start the FastAPI service and set AI_SERVICE_ENABLED=true.");
    }

    private ResponseStatusException unavailable(RestClientException exception) {
        log.warn("AI service request failed: {}", exception.getMessage());
        return new ResponseStatusException(SERVICE_UNAVAILABLE,
                "AI search is temporarily unavailable. Verify that the FastAPI service is running.");
    }

    private record IndexResponse(int indexed, int deleted) {}
    private record SearchKey(String query, int limit, double minimumScore, AiSearchFilter filters) {}
    private record CachedSearch(AiServiceSearchResponse response, Instant expiresAt) {
        boolean expired() { return !expiresAt.isAfter(Instant.now()); }
    }
}

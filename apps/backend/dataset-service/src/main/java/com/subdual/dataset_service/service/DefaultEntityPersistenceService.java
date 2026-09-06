package com.subdual.dataset_service.service;

import com.subdual.dataset_service.domain.EnrichedAttribute;
import com.subdual.dataset_service.domain.EnrichedEntity;
import com.subdual.dataset_service.domain.EnrichedSource;
import com.subdual.dataset_service.dto.EntityAttributeDto;
import com.subdual.dataset_service.dto.EntityDetailResponse;
import com.subdual.dataset_service.dto.EntitySourceDto;
import com.subdual.dataset_service.dto.EntitySummaryResponse;
import com.subdual.dataset_service.dto.PersistEntityRequest;
import com.subdual.dataset_service.repository.EnrichedEntityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class DefaultEntityPersistenceService implements EntityPersistenceService {

    private final EnrichedEntityRepository entityRepository;

    @Override
    public EntityDetailResponse persistOrUpdate(PersistEntityRequest request) {
        return persistOrUpdate(request, null);
    }

    @Override
    public EntityDetailResponse persistOrUpdate(PersistEntityRequest request, String userId) {
        EnrichedEntity entity = findOrCreateEntity(request.entityId());
        if (userId != null && !userId.isBlank()) {
            entity.setUserId(userId);
        }
        updateMetadata(entity, request);
        replaceSources(entity, request.sources());
        replaceAttributes(entity, request.attributes());

        EnrichedEntity saved = entityRepository.save(entity);
        return toDetailResponse(saved);
    }

    private EnrichedEntity findOrCreateEntity(String entityId) {
        return entityRepository.findById(entityId)
                .orElseGet(() -> EnrichedEntity.builder()
                        .entityId(entityId)
                        .sources(new ArrayList<>())
                        .attributes(new ArrayList<>())
                        .build());
    }

    private void updateMetadata(EnrichedEntity entity, PersistEntityRequest request) {
        entity.setDisplayName(request.displayName());
        entity.setEntityType(request.entityType());
        entity.setCanonicalUrl(request.canonicalUrl());
        if (request.executionStatus() != null) entity.setExecutionStatus(request.executionStatus());
        if (request.executionMessage() != null) entity.setExecutionMessage(request.executionMessage());
        if (request.priorityTier() != null) entity.setPriorityTier(request.priorityTier());
        if (request.relevanceScore() != null) entity.setRelevanceScore(request.relevanceScore());
        if (request.profileJson() != null) entity.setProfileJson(request.profileJson());
        if (request.assessmentJson() != null) entity.setAssessmentJson(request.assessmentJson());
        if (request.recommendationJson() != null) entity.setRecommendationJson(request.recommendationJson());
        if (request.findingsJson() != null) entity.setFindingsJson(request.findingsJson());
    }

    private void replaceSources(EnrichedEntity entity, List<EntitySourceDto> sourceDtos) {
        entity.getSources().clear();
        if (sourceDtos == null || sourceDtos.isEmpty()) {
            return;
        }

        for (EntitySourceDto s : sourceDtos) {
            entity.getSources().add(toEnrichedSource(entity, s));
        }
    }

    private EnrichedSource toEnrichedSource(EnrichedEntity entity, EntitySourceDto s) {
        return EnrichedSource.builder()
                .entity(entity)
                .sourceUrl(s.url())
                .title(s.title())
                .snippet(s.snippet())
                .sourceType(s.sourceType())
                .domain(s.domain())
                .provider(s.provider())
                .relevance(s.relevance())
                .retrievedAt(s.retrievedAt())
                .build();
    }

    private void replaceAttributes(EnrichedEntity entity, Map<String, EntityAttributeDto> attributeDtos) {
        entity.getAttributes().clear();
        if (attributeDtos == null || attributeDtos.isEmpty()) {
            return;
        }

        for (Map.Entry<String, EntityAttributeDto> entry : attributeDtos.entrySet()) {
            entity.getAttributes().add(toEnrichedAttribute(entity, entry.getKey(), entry.getValue()));
        }
    }

    private EnrichedAttribute toEnrichedAttribute(EnrichedEntity entity, String attributeName, EntityAttributeDto a) {
        return EnrichedAttribute.builder()
                .entity(entity)
                .attributeName(attributeName)
                .attributeValue(a.value())
                .sourceUrl(a.sourceUrl())
                .evidenceSnippet(a.evidenceSnippet())
                .confidence(a.confidence())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntityDetailResponse> findById(String entityId) {
        return findById(entityId, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntityDetailResponse> findById(String entityId, String userId) {
        Optional<EnrichedEntity> entityOpt = entityRepository.findById(entityId);
        if (entityOpt.isEmpty()) {
            return Optional.empty();
        }
        EnrichedEntity entity = entityOpt.get();
        if (userId != null && !userId.isBlank() && entity.getUserId() != null && !entity.getUserId().equals(userId)) {
            // IDOR Protection: Prevent User B from inspecting User A's entity details
            return Optional.empty();
        }
        return Optional.of(toDetailResponse(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> listAll() {
        return listAll(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> listAll(String userId) {
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        List<EnrichedEntity> list = (userId != null && !userId.isBlank())
                ? entityRepository.findByUserId(userId, sort)
                : entityRepository.findAll(sort);

        return list.stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> list(int page, int size) {
        return list(page, size, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> list(int page, int size, String userId) {
        Pageable pageable = buildPageRequest(page, size);
        return ((userId != null && !userId.isBlank())
                ? entityRepository.findByUserId(userId, pageable)
                : entityRepository.findAll(pageable))
                .map(this::toSummaryResponse)
                .getContent();
    }

    private Pageable buildPageRequest(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        return PageRequest.of(safePage, safeSize, sort);
    }

    private EntitySummaryResponse toSummaryResponse(EnrichedEntity e) {
        int sourceCount = e.getSources() != null ? e.getSources().size() : 0;
        int attributeCount = e.getAttributes() != null ? e.getAttributes().size() : 0;

        return new EntitySummaryResponse(
                e.getEntityId(),
                e.getDisplayName(),
                e.getEntityType(),
                e.getCanonicalUrl(),
                sourceCount,
                attributeCount,
                e.getUpdatedAt(),
                e.getPriorityTier(),
                e.getRelevanceScore(),
                e.getExecutionStatus()
        );
    }

    private EntityDetailResponse toDetailResponse(EnrichedEntity entity) {
        List<EntitySourceDto> sources = mapSourcesToDto(entity.getSources());
        Map<String, EntityAttributeDto> attributes = mapAttributesToDto(entity.getAttributes());

        return new EntityDetailResponse(
                entity.getEntityId(),
                entity.getDisplayName(),
                entity.getEntityType(),
                entity.getCanonicalUrl(),
                sources,
                attributes,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getExecutionStatus(),
                entity.getExecutionMessage(),
                entity.getPriorityTier(),
                entity.getRelevanceScore(),
                entity.getProfileJson(),
                entity.getAssessmentJson(),
                entity.getRecommendationJson(),
                entity.getFindingsJson()
        );
    }

    private List<EntitySourceDto> mapSourcesToDto(List<EnrichedSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
                .map(this::toSourceDto)
                .toList();
    }

    private EntitySourceDto toSourceDto(EnrichedSource s) {
        return new EntitySourceDto(
                s.getSourceUrl(),
                s.getTitle(),
                s.getSnippet(),
                s.getSourceType(),
                s.getDomain(),
                s.getProvider(),
                s.getRelevance(),
                s.getRetrievedAt()
        );
    }

    private Map<String, EntityAttributeDto> mapAttributesToDto(List<EnrichedAttribute> attributes) {
        Map<String, EntityAttributeDto> attributeDtos = new LinkedHashMap<>();
        if (attributes != null) {
            for (EnrichedAttribute a : attributes) {
                attributeDtos.put(a.getAttributeName(), toAttributeDto(a));
            }
        }
        return attributeDtos;
    }

    private EntityAttributeDto toAttributeDto(EnrichedAttribute a) {
        return new EntityAttributeDto(
                a.getAttributeValue(),
                a.getSourceUrl(),
                a.getEvidenceSnippet(),
                a.getConfidence()
        );
    }
}

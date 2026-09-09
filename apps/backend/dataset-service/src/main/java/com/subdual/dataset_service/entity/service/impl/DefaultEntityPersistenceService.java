package com.subdual.dataset_service.entity.service.impl;

import com.subdual.dataset_service.common.dto.EntityAttributeDto;
import com.subdual.dataset_service.common.dto.EntitySourceDto;
import com.subdual.dataset_service.entity.api.dto.request.PersistEntityRequest;
import com.subdual.dataset_service.entity.api.dto.response.EntityDetailResponse;
import com.subdual.dataset_service.entity.api.dto.response.EntitySummaryResponse;
import com.subdual.dataset_service.entity.model.EnrichedEntity;
import com.subdual.dataset_service.entity.repository.EnrichedEntityRepository;
import com.subdual.dataset_service.entity.service.EntityPersistenceService;
import com.subdual.dataset_service.entity.service.helper.EntityIdResolver;
import com.subdual.dataset_service.entity.service.helper.EntityMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Transactional
public class DefaultEntityPersistenceService implements EntityPersistenceService {

    private final EnrichedEntityRepository entityRepository;
    private final EntityIdResolver entityIdResolver;
    private final EntityMapper entityMapper;

    @Autowired
    public DefaultEntityPersistenceService(
            EnrichedEntityRepository entityRepository,
            EntityIdResolver entityIdResolver,
            EntityMapper entityMapper
    ) {
        this.entityRepository = entityRepository;
        this.entityIdResolver = entityIdResolver;
        this.entityMapper = entityMapper;
    }

    public DefaultEntityPersistenceService(EnrichedEntityRepository entityRepository) {
        this(entityRepository, new EntityIdResolver(), new EntityMapper());
    }

    @Override
    public EntityDetailResponse persistOrUpdate(PersistEntityRequest request) {
        return persistOrUpdate(request, null);
    }

    @Override // Persists or merges enriched entity, attributes, and source evidence.
    public EntityDetailResponse persistOrUpdate(PersistEntityRequest request, String userId) {
        String effectiveEntityId = resolveScopedEntityId(request.entityId(), userId);
        EnrichedEntity entity = findOrCreateEntity(effectiveEntityId, userId);
        if (userId != null && !userId.isBlank()) {
            entity.setUserId(userId);
        }
        updateMetadata(entity, request);
        replaceSources(entity, request.sources());
        replaceAttributes(entity, request.attributes());

        EnrichedEntity saved = entityRepository.save(entity);
        return entityMapper.toDetailResponse(saved);
    }

    public String resolveScopedEntityId(String rawEntityId, String userId) {
        return entityIdResolver.resolveScopedEntityId(rawEntityId, userId);
    }

    private EnrichedEntity findOrCreateEntity(String entityId, String userId) {
        if (userId != null && !userId.isBlank()) {
            return entityRepository.findByEntityIdAndUserId(entityId, userId)
                    .orElseGet(() -> EnrichedEntity.builder()
                            .entityId(entityId)
                            .userId(userId)
                            .sources(new ArrayList<>())
                            .attributes(new ArrayList<>())
                            .build());
        }
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
            entity.getSources().add(entityMapper.toEnrichedSource(entity, s));
        }
    }

    private void replaceAttributes(EnrichedEntity entity, Map<String, EntityAttributeDto> attributeDtos) {
        entity.getAttributes().clear();
        if (attributeDtos == null || attributeDtos.isEmpty()) {
            return;
        }

        for (Map.Entry<String, EntityAttributeDto> entry : attributeDtos.entrySet()) {
            entity.getAttributes().add(entityMapper.toEnrichedAttribute(entity, entry.getKey(), entry.getValue()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntityDetailResponse> findById(String entityId) {
        return findById(entityId, null);
    }

    @Override // Retrieves enriched entity details isolated to the requesting user.
    @Transactional(readOnly = true)
    public Optional<EntityDetailResponse> findById(String entityId, String userId) {
        if (userId == null || userId.isBlank()) {
            return Optional.empty();
        }
        return entityRepository.findByEntityIdAndUserId(entityId, userId)
                .map(entityMapper::toDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> listAll() {
        return List.of();
    }

    @Override // Lists all enriched entities belonging to the specified user.
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> listAll(String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        List<EnrichedEntity> list = entityRepository.findByUserId(userId, sort);

        return list.stream()
                .map(entityMapper::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> list(int page, int size) {
        return List.of();
    }

    @Override // Fetches paginated enriched entity summaries for the specified user.
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> list(int page, int size, String userId) {
        if (userId == null || userId.isBlank()) {
            return List.of();
        }
        Pageable pageable = buildPageRequest(page, size);
        return entityRepository.findByUserId(userId, pageable)
                .map(entityMapper::toSummaryResponse)
                .getContent();
    }

    private Pageable buildPageRequest(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        Sort sort = Sort.by(Sort.Direction.DESC, "updatedAt");
        return PageRequest.of(safePage, safeSize, sort);
    }
}

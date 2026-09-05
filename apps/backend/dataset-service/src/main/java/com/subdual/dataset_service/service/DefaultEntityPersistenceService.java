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
        EnrichedEntity entity = entityRepository.findById(request.entityId())
                .orElseGet(() -> EnrichedEntity.builder()
                        .entityId(request.entityId())
                        .sources(new ArrayList<>())
                        .attributes(new ArrayList<>())
                        .build());

        entity.setDisplayName(request.displayName());
        entity.setEntityType(request.entityType());
        entity.setCanonicalUrl(request.canonicalUrl());

        // Replace sources
        entity.getSources().clear();
        if (request.sources() != null) {
            for (EntitySourceDto s : request.sources()) {
                EnrichedSource source = EnrichedSource.builder()
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
                entity.getSources().add(source);
            }
        }

        // Replace attributes
        entity.getAttributes().clear();
        if (request.attributes() != null) {
            for (Map.Entry<String, EntityAttributeDto> entry : request.attributes().entrySet()) {
                EntityAttributeDto a = entry.getValue();
                EnrichedAttribute attr = EnrichedAttribute.builder()
                        .entity(entity)
                        .attributeName(entry.getKey())
                        .attributeValue(a.value())
                        .sourceUrl(a.sourceUrl())
                        .evidenceSnippet(a.evidenceSnippet())
                        .confidence(a.confidence())
                        .build();
                entity.getAttributes().add(attr);
            }
        }

        EnrichedEntity saved = entityRepository.save(entity);
        return toDetailResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<EntityDetailResponse> findById(String entityId) {
        return entityRepository.findById(entityId).map(this::toDetailResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> listAll() {
        return entityRepository.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt"))
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<EntitySummaryResponse> list(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(
                safePage, safeSize, org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "updatedAt")
        );

        return entityRepository.findAll(pageable)
                .map(this::toSummaryResponse)
                .getContent();
    }

    private EntitySummaryResponse toSummaryResponse(EnrichedEntity e) {
        return new EntitySummaryResponse(
                e.getEntityId(),
                e.getDisplayName(),
                e.getEntityType(),
                e.getCanonicalUrl(),
                e.getSources() != null ? e.getSources().size() : 0,
                e.getAttributes() != null ? e.getAttributes().size() : 0,
                e.getUpdatedAt()
        );
    }

    private EntityDetailResponse toDetailResponse(EnrichedEntity entity) {
        List<EntitySourceDto> sources = entity.getSources() != null
                ? entity.getSources().stream()
                .map(s -> new EntitySourceDto(
                        s.getSourceUrl(),
                        s.getTitle(),
                        s.getSnippet(),
                        s.getSourceType(),
                        s.getDomain(),
                        s.getProvider(),
                        s.getRelevance(),
                        s.getRetrievedAt()))
                .toList()
                : List.of();

        Map<String, EntityAttributeDto> attributes = new LinkedHashMap<>();
        if (entity.getAttributes() != null) {
            for (EnrichedAttribute a : entity.getAttributes()) {
                attributes.put(a.getAttributeName(), new EntityAttributeDto(
                        a.getAttributeValue(),
                        a.getSourceUrl(),
                        a.getEvidenceSnippet(),
                        a.getConfidence()));
            }
        }

        return new EntityDetailResponse(
                entity.getEntityId(),
                entity.getDisplayName(),
                entity.getEntityType(),
                entity.getCanonicalUrl(),
                sources,
                attributes,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

package com.subdual.dataset_service.entity.service.helper;

import com.subdual.dataset_service.common.dto.EntityAttributeDto;
import com.subdual.dataset_service.common.dto.EntitySourceDto;
import com.subdual.dataset_service.entity.api.dto.response.EntityDetailResponse;
import com.subdual.dataset_service.entity.api.dto.response.EntitySummaryResponse;
import com.subdual.dataset_service.entity.model.EnrichedAttribute;
import com.subdual.dataset_service.entity.model.EnrichedEntity;
import com.subdual.dataset_service.entity.model.EnrichedSource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EntityMapper {

    public EntitySummaryResponse toSummaryResponse(EnrichedEntity e) {
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

    public EntityDetailResponse toDetailResponse(EnrichedEntity entity) {
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

    public EnrichedSource toEnrichedSource(EnrichedEntity entity, EntitySourceDto s) {
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

    public EnrichedAttribute toEnrichedAttribute(EnrichedEntity entity, String attributeName, EntityAttributeDto a) {
        return EnrichedAttribute.builder()
                .entity(entity)
                .attributeName(attributeName)
                .attributeValue(a.value())
                .sourceUrl(a.sourceUrl())
                .evidenceSnippet(a.evidenceSnippet())
                .confidence(a.confidence())
                .build();
    }

    public List<EntitySourceDto> mapSourcesToDto(List<EnrichedSource> sources) {
        if (sources == null || sources.isEmpty()) {
            return List.of();
        }
        return sources.stream()
                .map(this::toSourceDto)
                .toList();
    }

    public EntitySourceDto toSourceDto(EnrichedSource s) {
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

    public Map<String, EntityAttributeDto> mapAttributesToDto(List<EnrichedAttribute> attributes) {
        Map<String, EntityAttributeDto> attributeDtos = new LinkedHashMap<>();
        if (attributes != null) {
            for (EnrichedAttribute a : attributes) {
                attributeDtos.put(a.getAttributeName(), toAttributeDto(a));
            }
        }
        return attributeDtos;
    }

    public EntityAttributeDto toAttributeDto(EnrichedAttribute a) {
        return new EntityAttributeDto(
                a.getAttributeValue(),
                a.getSourceUrl(),
                a.getEvidenceSnippet(),
                a.getConfidence()
        );
    }
}

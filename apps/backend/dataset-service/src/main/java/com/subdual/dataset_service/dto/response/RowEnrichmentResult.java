package com.subdual.dataset_service.dto.response;

import com.subdual.dataset_service.dto.common.EntityAttributeDto;
import com.subdual.dataset_service.dto.common.EntitySourceDto;
import com.subdual.dataset_service.profile.model.ObjectiveAssessment;
import com.subdual.dataset_service.profile.model.RecommendedApproach;
import com.subdual.dataset_service.profile.model.ResearchFinding;
import com.subdual.dataset_service.profile.model.ResearchProfile;

import java.util.List;
import java.util.Map;

public record RowEnrichmentResult(
        String rowId,
        int rowIndex,
        Map<String, String> originalData,
        String status,
        String displayName,
        String canonicalUrl,
        String entityType,
        Map<String, EntityAttributeDto> attributes,
        List<String> unresolvedFields,
        List<String> conflicts,
        double confidence,
        List<EntitySourceDto> sources,
        String errorMessage,
        String stage,
        String workerId,
        String message,
        Long startedAtMs,
        Long completedAtMs,
        ResearchProfile profile,
        ObjectiveAssessment assessment,
        RecommendedApproach recommendation,
        List<ResearchFinding> findings
) {
    public RowEnrichmentResult {
        if (originalData == null) {
            originalData = Map.of();
        }
        if (attributes == null) {
            attributes = Map.of();
        }
        if (unresolvedFields == null) {
            unresolvedFields = List.of();
        }
        if (conflicts == null) {
            conflicts = List.of();
        }
        if (sources == null) {
            sources = List.of();
        }
        if (findings == null) {
            findings = List.of();
        }
    }

    public RowEnrichmentResult(
            String rowId,
            int rowIndex,
            Map<String, String> originalData,
            String status,
            String displayName,
            String canonicalUrl,
            String entityType,
            Map<String, EntityAttributeDto> attributes,
            List<String> unresolvedFields,
            List<String> conflicts,
            double confidence,
            List<EntitySourceDto> sources,
            String errorMessage
    ) {
        this(rowId, rowIndex, originalData, status, displayName, canonicalUrl, entityType,
                attributes, unresolvedFields, conflicts, confidence, sources, errorMessage,
                null, null, null, null, null, null, null, null, List.of());
    }

    public RowEnrichmentResult(
            String rowId,
            int rowIndex,
            Map<String, String> originalData,
            String status,
            String displayName,
            String canonicalUrl,
            String entityType,
            Map<String, EntityAttributeDto> attributes,
            List<String> unresolvedFields,
            List<String> conflicts,
            double confidence,
            List<EntitySourceDto> sources,
            String errorMessage,
            String stage,
            String workerId,
            String message,
            Long startedAtMs,
            Long completedAtMs
    ) {
        this(rowId, rowIndex, originalData, status, displayName, canonicalUrl, entityType,
                attributes, unresolvedFields, conflicts, confidence, sources, errorMessage,
                stage, workerId, message, startedAtMs, completedAtMs, null, null, null, List.of());
    }
}

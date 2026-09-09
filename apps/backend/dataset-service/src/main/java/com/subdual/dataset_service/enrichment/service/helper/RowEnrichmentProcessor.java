package com.subdual.dataset_service.enrichment.service.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.subdual.dataset_service.common.dto.EntityAttributeDto;
import com.subdual.dataset_service.common.dto.EntitySourceDto;
import com.subdual.dataset_service.enrichment.api.dto.request.FactEvidenceDto;
import com.subdual.dataset_service.enrichment.api.dto.request.ProfileAssessmentRequest;
import com.subdual.dataset_service.enrichment.api.dto.response.ProfileAssessmentResponse;
import com.subdual.dataset_service.enrichment.api.dto.response.RowEnrichmentResult;
import com.subdual.dataset_service.enrichment.integration.AiServiceClient;
import com.subdual.dataset_service.enrichment.integration.ResearchServiceClient;
import com.subdual.dataset_service.enrichment.model.JobState;
import com.subdual.dataset_service.enrichment.model.ObjectiveAssessment;
import com.subdual.dataset_service.enrichment.model.RecommendedApproach;
import com.subdual.dataset_service.enrichment.model.ResearchFinding;
import com.subdual.dataset_service.enrichment.model.ResearchObjective;
import com.subdual.dataset_service.enrichment.model.ResearchProfile;
import com.subdual.dataset_service.entity.api.dto.request.PersistEntityRequest;
import com.subdual.dataset_service.entity.service.EntityPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class RowEnrichmentProcessor {

    private final ResearchServiceClient researchServiceClient;
    private final AiServiceClient aiServiceClient;
    private final EntityPersistenceService persistenceService;
    private final ObjectMapper objectMapper;
    private final RowIdentityResolver rowIdentityResolver;
    private final EnrichmentJobManager jobManager;

    public RowEnrichmentResult processSingleRow(
            String rowId,
            int rowIndex,
            Map<String, String> rawRow,
            Map<String, String> mapping,
            String defaultEntityType,
            String requirement,
            List<String> targetFields,
            String jobId,
            String workerId,
            long startedAtMs,
            String explicitUserId
    ) {
        RowIdentity identity = resolveRowIdentity(rawRow, mapping, defaultEntityType);
        if (!identity.isValid()) {
            return buildMissingIdentifierResult(rowId, rowIndex, rawRow, identity.entityType(), targetFields, workerId, startedAtMs);
        }

        logIdentityPipeline(rowIndex, identity);

        jobManager.emitExecutionEvent(
                jobId, rowId, rowIndex, identity.displayName(), "PROCESSING", "DISCOVERING", workerId,
                "Querying verified web sources & search indexes...", Map.of("name", identity.displayName(), "url", identity.canonicalUrl())
        );

        ResearchOutcome researchOutcome = executeResearch(identity, rawRow, targetFields, requirement, rowIndex);
        if (researchOutcome.response() == null) {
            return buildResearchFailureResult(rowId, rowIndex, rawRow, identity.displayName(), identity.canonicalUrl(),
                    identity.entityType(), targetFields, workerId, startedAtMs, researchOutcome.errorMessage());
        }

        ResearchServiceClient.ResearchCallResponse researchResp = researchOutcome.response();
        String effectiveDisplayName = resolveDisplayName(researchResp, identity.displayName());
        String effectiveUrl = resolveCanonicalUrl(researchResp, identity.canonicalUrl());

        EvidenceData evidence = extractDiscoveredEvidence(researchResp);
        emitEvidenceDiscoveryEvents(jobId, rowId, rowIndex, effectiveDisplayName, workerId, evidence, targetFields);

        SynthesizedAttributes synthesized = synthesizeAttributes(rawRow, effectiveDisplayName, identity.entityType(),
                effectiveUrl, requirement, targetFields, evidence, rowIndex);

        jobManager.emitExecutionEvent(
                jobId, rowId, rowIndex, synthesized.displayName(), "PROCESSING", "ASSESSING", workerId,
                "Evaluating profile relevance against research objective...", Map.of("objective", requirement != null ? requirement : "")
        );

        ProfileAssessmentData assessmentData = evaluateObjectiveAssessment(rawRow, synthesized.displayName(),
                effectiveUrl, identity.entityType(), requirement, evidence, synthesized.attributes(), rowIndex);

        jobManager.emitExecutionEvent(
                jobId, rowId, rowIndex, synthesized.displayName(), "PROCESSING", "PERSISTING", workerId,
                "Persisting canonical profile (" + synthesized.attributes().size() + " attributes) to catalog...",
                Map.of("attributesCount", synthesized.attributes().size())
        );

        EnrichmentStatus status = resolveEnrichmentStatus(researchResp, evidence.sourceUrls(),
                synthesized.attributes(), synthesized.unresolvedFields(), synthesized.aiSucceeded(),
                assessmentData.aiSucceeded(), requirement, assessmentData.assessment());

        persistCanonicalProfileSafe(jobId, explicitUserId, researchResp.entityId(), synthesized.displayName(),
                identity.entityType(), effectiveUrl, evidence.entitySources(), synthesized.attributes(),
                status, assessmentData);

        return buildRowResult(rowId, rowIndex, rawRow, status, synthesized, effectiveUrl, identity.entityType(),
                evidence.entitySources(), workerId, startedAtMs, assessmentData);
    }

    private record RowIdentity(
            String displayName,
            String firstName,
            String lastName,
            String fullName,
            String canonicalUrl,
            String org,
            String role,
            String email,
            String location,
            String entityType,
            boolean isValid
    ) {}

    private RowIdentity resolveRowIdentity(Map<String, String> rawRow, Map<String, String> mapping, String defaultEntityType) {
        String firstName = rowIdentityResolver.extractMappedValue(rawRow, mapping, "firstNameColumn");
        String lastName = rowIdentityResolver.extractMappedValue(rawRow, mapping, "lastNameColumn");
        String fullName = rowIdentityResolver.extractMappedValue(rawRow, mapping, "fullNameColumn");
        String name = rowIdentityResolver.extractMappedValue(rawRow, mapping, "nameColumn");
        String compositeName = RowIdentityResolver.buildCompositeName(firstName, lastName, fullName, name);

        String url = rowIdentityResolver.extractMappedValue(rawRow, mapping, "urlColumn");
        String org = rowIdentityResolver.extractMappedValue(rawRow, mapping, "organizationColumn");
        String role = rowIdentityResolver.extractMappedValue(rawRow, mapping, "roleColumn");
        String email = rowIdentityResolver.extractMappedValue(rawRow, mapping, "emailColumn");
        String location = rowIdentityResolver.extractMappedValue(rawRow, mapping, "locationColumn");

        String rawType = rowIdentityResolver.extractMappedValue(rawRow, mapping, "entityTypeColumn");
        String entityType = (rawType != null && !rawType.isBlank()) ? rawType.trim().toUpperCase(Locale.ROOT) : defaultEntityType;

        boolean isValid = (compositeName != null && !compositeName.isBlank()) || (url != null && !url.isBlank());
        String displayName = (compositeName != null && !compositeName.isBlank()) ? compositeName : "Unknown";
        String canonicalUrl = url != null ? url : "";

        return new RowIdentity(displayName, firstName, lastName, fullName, canonicalUrl, org, role, email, location, entityType, isValid);
    }

    private void logIdentityPipeline(int rowIndex, RowIdentity identity) {
        log.info("[Pipeline: IDENTITY] Row #{} Constructed identity: fullName='{}', firstName='{}', lastName='{}', profileUrl='{}', organization='{}', role='{}', email='{}', location='{}'",
                rowIndex, identity.displayName(), identity.firstName(), identity.lastName(), identity.canonicalUrl(),
                identity.org(), identity.role(), identity.email(), identity.location());
    }

    private RowEnrichmentResult buildMissingIdentifierResult(
            String rowId, int rowIndex, Map<String, String> rawRow, String entityType,
            List<String> targetFields, String workerId, long startedAtMs
    ) {
        return new RowEnrichmentResult(
                rowId,
                rowIndex,
                rawRow,
                "FAILED",
                "Missing Identifier",
                "",
                entityType,
                Map.of(),
                targetFields,
                List.of(),
                0.0,
                List.of(),
                "Row missing required name or url identifier",
                "FAILED",
                workerId,
                "Row missing required name or url identifier",
                startedAtMs,
                System.currentTimeMillis()
        );
    }

    private record ResearchOutcome(ResearchServiceClient.ResearchCallResponse response, String errorMessage) {}

    private ResearchOutcome executeResearch(
            RowIdentity identity,
            Map<String, String> rawRow,
            List<String> targetFields,
            String requirement,
            int rowIndex
    ) {
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (rawRow != null) {
                metadata.putAll(rawRow);
            }
            if (identity.firstName() != null && !identity.firstName().isBlank()) metadata.put("firstName", identity.firstName());
            if (identity.lastName() != null && !identity.lastName().isBlank()) metadata.put("lastName", identity.lastName());
            if (identity.fullName() != null && !identity.fullName().isBlank()) metadata.put("fullName", identity.fullName());
            if (identity.email() != null && !identity.email().isBlank()) metadata.put("email", identity.email());
            if (identity.location() != null && !identity.location().isBlank()) metadata.put("location", identity.location());

            ResearchServiceClient.ResearchCallResponse response = researchServiceClient.executeResearch(
                    new ResearchServiceClient.ResearchCallRequest(
                            identity.canonicalUrl(),
                            identity.entityType(),
                            identity.displayName(),
                            identity.org(),
                            identity.role(),
                            targetFields,
                            requirement,
                            metadata,
                            identity.firstName(),
                            identity.lastName(),
                            identity.fullName() != null ? identity.fullName() : identity.displayName(),
                            identity.email(),
                            identity.location()
                    )
            );
            return new ResearchOutcome(response, null);
        } catch (Exception ex) {
            log.warn("Research call failed for row {}: {}", rowIndex, ex.getMessage());
            return new ResearchOutcome(null, ex.getMessage());
        }
    }

    private RowEnrichmentResult buildResearchFailureResult(
            String rowId, int rowIndex, Map<String, String> rawRow, String displayName,
            String canonicalUrl, String entityType, List<String> targetFields, String workerId,
            long startedAtMs, String researchError
    ) {
        String errorDesc = researchError != null ? researchError : "No response";
        return new RowEnrichmentResult(
                rowId,
                rowIndex,
                rawRow,
                "FAILED",
                displayName,
                canonicalUrl,
                entityType,
                Map.of(),
                targetFields,
                List.of(),
                0.0,
                List.of(),
                "Research service error: " + errorDesc,
                "FAILED",
                workerId,
                "Research service failed: " + errorDesc,
                startedAtMs,
                System.currentTimeMillis()
        );
    }

    private String resolveDisplayName(ResearchServiceClient.ResearchCallResponse researchResp, String fallback) {
        return (researchResp.result() != null && researchResp.result().displayName() != null)
                ? researchResp.result().displayName()
                : fallback;
    }

    private String resolveCanonicalUrl(ResearchServiceClient.ResearchCallResponse researchResp, String fallback) {
        return (researchResp.result() != null && researchResp.result().canonicalUrl() != null)
                ? researchResp.result().canonicalUrl()
                : fallback;
    }

    private record EvidenceData(
            Map<String, AiServiceClient.FactEvidenceCallDto> evidenceMap,
            Map<String, FactEvidenceDto> profileEvidenceMap,
            List<String> sourceUrls,
            List<String> sourceSnippets,
            List<EntitySourceDto> entitySources
    ) {}

    private EvidenceData extractDiscoveredEvidence(ResearchServiceClient.ResearchCallResponse researchResp) {
        Map<String, AiServiceClient.FactEvidenceCallDto> evidenceMap = new LinkedHashMap<>();
        Map<String, FactEvidenceDto> profileEvidenceMap = new LinkedHashMap<>();
        List<String> sourceUrls = new ArrayList<>();
        List<String> sourceSnippets = new ArrayList<>();
        List<EntitySourceDto> entitySources = new ArrayList<>();

        if (researchResp.sources() != null) {
            for (ResearchServiceClient.SourceItemDto s : researchResp.sources()) {
                sourceUrls.add(s.url());
                if (s.snippet() != null && !s.snippet().isBlank()) {
                    sourceSnippets.add(s.snippet());
                }
                entitySources.add(new EntitySourceDto(
                        s.url(),
                        s.title(),
                        s.snippet(),
                        s.sourceType(),
                        s.domain(),
                        s.provider(),
                        s.relevance(),
                        parseInstantSafe(s.retrievedAt())
                ));
            }
        }

        if (researchResp.result() != null && researchResp.result().attributes() != null) {
            for (Map.Entry<String, ResearchServiceClient.EvidenceTupleDto> entry : researchResp.result().attributes().entrySet()) {
                ResearchServiceClient.EvidenceTupleDto tuple = entry.getValue();
                evidenceMap.put(entry.getKey(), new AiServiceClient.FactEvidenceCallDto(
                        entry.getKey(),
                        tuple.value(),
                        tuple.sourceUrl(),
                        tuple.evidenceSnippet(),
                        tuple.confidence(),
                        tuple.corroboratingSources(),
                        tuple.conflictDetected()
                ));
                profileEvidenceMap.put(entry.getKey(), new FactEvidenceDto(
                        entry.getKey(),
                        tuple.value(),
                        tuple.sourceUrl(),
                        tuple.evidenceSnippet(),
                        tuple.confidence(),
                        tuple.corroboratingSources(),
                        tuple.conflictDetected()
                ));
            }
        }

        return new EvidenceData(evidenceMap, profileEvidenceMap, sourceUrls, sourceSnippets, entitySources);
    }

    private void emitEvidenceDiscoveryEvents(
            String jobId, String rowId, int rowIndex, String displayName,
            String workerId, EvidenceData evidence, List<String> targetFields
    ) {
        jobManager.emitExecutionEvent(
                jobId, rowId, rowIndex, displayName, "PROCESSING", "COLLECTING_SOURCES", workerId,
                "Collected and deduplicated " + evidence.sourceUrls().size() + " candidate sources...",
                Map.of("sourcesCount", evidence.sourceUrls().size())
        );

        jobManager.emitExecutionEvent(
                jobId, rowId, rowIndex, displayName, "PROCESSING", "EXTRACTING_EVIDENCE", workerId,
                "Extracting grounded evidence from " + evidence.sourceUrls().size() + " discovered sources...",
                Map.of("sourcesCount", evidence.sourceUrls().size(), "evidenceCount", evidence.evidenceMap().size())
        );

        jobManager.emitExecutionEvent(
                jobId, rowId, rowIndex, displayName, "PROCESSING", "AI_ENRICHMENT", workerId,
                "Synthesizing grounded attributes with AI intelligence...",
                Map.of("evidenceCount", evidence.evidenceMap().size(), "targetFields", targetFields)
        );
    }

    private record SynthesizedAttributes(
            String displayName,
            double confidence,
            Map<String, EntityAttributeDto> attributes,
            List<String> unresolvedFields,
            List<String> conflicts,
            boolean aiSucceeded
    ) {}

    private SynthesizedAttributes synthesizeAttributes(
            Map<String, String> rawRow,
            String displayName,
            String entityType,
            String canonicalUrl,
            String requirement,
            List<String> targetFields,
            EvidenceData evidence,
            int rowIndex
    ) {
        AiServiceClient.SynthesisCallResponse aiResp = null;
        try {
            aiResp = aiServiceClient.synthesizeEnrichment(new AiServiceClient.SynthesisCallRequest(
                    rawRow,
                    displayName,
                    entityType,
                    canonicalUrl,
                    requirement,
                    targetFields,
                    evidence.evidenceMap(),
                    evidence.sourceUrls()
            ));
        } catch (Exception ex) {
            log.warn("AI synthesis call failed for row {}: {}", rowIndex, ex.getMessage());
        }

        Map<String, EntityAttributeDto> finalAttributes = new LinkedHashMap<>();
        List<String> unresolvedFields = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        double confidence = 0.85;
        String resolvedName = displayName;
        boolean aiSucceeded = false;

        if (aiResp != null && aiResp.attributes() != null && !aiResp.attributes().isEmpty()) {
            aiSucceeded = true;
            resolvedName = aiResp.displayName();
            confidence = aiResp.overallConfidence();
            unresolvedFields.addAll(aiResp.unresolvedFields());
            conflicts.addAll(aiResp.conflicts());

            for (Map.Entry<String, AiServiceClient.AttributeResultCallDto> entry : aiResp.attributes().entrySet()) {
                AiServiceClient.AttributeResultCallDto attr = entry.getValue();
                finalAttributes.put(entry.getKey(), new EntityAttributeDto(
                        attr.value(),
                        (attr.sources() != null && !attr.sources().isEmpty()) ? attr.sources().get(0) : null,
                        attr.evidence(),
                        attr.confidence()
                ));
            }
        } else {
            for (Map.Entry<String, AiServiceClient.FactEvidenceCallDto> entry : evidence.evidenceMap().entrySet()) {
                AiServiceClient.FactEvidenceCallDto fact = entry.getValue();
                finalAttributes.put(entry.getKey(), new EntityAttributeDto(
                        fact.value(),
                        fact.sourceUrl(),
                        fact.evidenceSnippet(),
                        fact.confidence()
                ));
                if ("UNKNOWN".equalsIgnoreCase(fact.value())) {
                    unresolvedFields.add(entry.getKey());
                }
            }
        }

        return new SynthesizedAttributes(resolvedName, confidence, finalAttributes, unresolvedFields, conflicts, aiSucceeded);
    }

    private record ProfileAssessmentData(
            ResearchProfile profile,
            ObjectiveAssessment assessment,
            RecommendedApproach recommendation,
            List<ResearchFinding> findings,
            boolean aiSucceeded
    ) {}

    private ProfileAssessmentData evaluateObjectiveAssessment(
            Map<String, String> rawRow,
            String displayName,
            String canonicalUrl,
            String entityType,
            String requirement,
            EvidenceData evidence,
            Map<String, EntityAttributeDto> finalAttributes,
            int rowIndex
    ) {
        ResearchObjective objectiveObj = ResearchObjective.from(requirement);
        ProfileAssessmentResponse profileAssessment = null;
        try {
            profileAssessment = aiServiceClient.assessProfile(new ProfileAssessmentRequest(
                    rawRow,
                    displayName,
                    canonicalUrl,
                    entityType,
                    objectiveObj,
                    evidence.profileEvidenceMap(),
                    evidence.sourceUrls(),
                    evidence.sourceSnippets()
            ));
        } catch (Exception ex) {
            log.warn("AI profile assessment call failed for row {}: {}", rowIndex, ex.getMessage());
        }

        if (profileAssessment != null) {
            ResearchProfile profile = profileAssessment.profile();
            ObjectiveAssessment assessment = profileAssessment.assessment();
            RecommendedApproach recommendation = profileAssessment.recommendation();
            List<ResearchFinding> findings = profileAssessment.findings() != null ? profileAssessment.findings() : List.of();
            enrichAttributesFromProfile(profile, canonicalUrl, finalAttributes);
            return new ProfileAssessmentData(profile, assessment, recommendation, findings, true);
        }

        return buildFallbackAssessmentData(displayName, objectiveObj, finalAttributes);
    }

    private void enrichAttributesFromProfile(ResearchProfile profile, String canonicalUrl, Map<String, EntityAttributeDto> finalAttributes) {
        if (profile != null) {
            if (!"UNKNOWN".equals(profile.currentRole()) && !finalAttributes.containsKey("currentRole")) {
                finalAttributes.put("currentRole", new EntityAttributeDto(profile.currentRole(), canonicalUrl, "Extracted from profile", "HIGH"));
            }
            if (!"UNKNOWN".equals(profile.currentOrganization()) && !finalAttributes.containsKey("currentOrganization")) {
                finalAttributes.put("currentOrganization", new EntityAttributeDto(profile.currentOrganization(), canonicalUrl, "Extracted from profile", "HIGH"));
            }
            if (!"UNKNOWN".equals(profile.location()) && !finalAttributes.containsKey("location")) {
                finalAttributes.put("location", new EntityAttributeDto(profile.location(), canonicalUrl, "Extracted from profile", "HIGH"));
            }
        }
    }

    private ProfileAssessmentData buildFallbackAssessmentData(
            String displayName, ResearchObjective objectiveObj, Map<String, EntityAttributeDto> finalAttributes
    ) {
        boolean hasObj = !objectiveObj.isBlank();
        int score = hasObj ? 50 : 0;
        ObjectiveAssessment.PriorityTier tier = hasObj ? ObjectiveAssessment.PriorityTier.MEDIUM : ObjectiveAssessment.PriorityTier.NONE;
        String whyRel = hasObj ? "Profile identified during enrichment matching basic criteria." : "General profile research completed (no specific objective specified).";

        ObjectiveAssessment assessment = new ObjectiveAssessment(score, tier, whyRel, Map.of(), List.of(), List.of());
        RecommendedApproach recommendation = new RecommendedApproach(
                RecommendedApproach.ApproachType.NETWORKING_CONVERSATION,
                "Professional networking outreach",
                "Ground outreach in verified background.",
                List.of("Connect referencing current professional role.")
        );
        ResearchProfile profile = new ResearchProfile(
                finalAttributes.containsKey("currentRole") ? finalAttributes.get("currentRole").value() : "UNKNOWN",
                finalAttributes.containsKey("currentOrganization") ? finalAttributes.get("currentOrganization").value() : "UNKNOWN",
                finalAttributes.containsKey("location") ? finalAttributes.get("location").value() : "UNKNOWN",
                displayName + " is a professional identified during enrichment.",
                "Identified from public professional sources.",
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        return new ProfileAssessmentData(profile, assessment, recommendation, List.of(), false);
    }

    private record EnrichmentStatus(String status, String statusMessage) {}

    private EnrichmentStatus resolveEnrichmentStatus(
            ResearchServiceClient.ResearchCallResponse researchResp,
            List<String> sourceUrls,
            Map<String, EntityAttributeDto> finalAttributes,
            List<String> unresolvedFields,
            boolean aiSucceeded,
            boolean assessmentAiSucceeded,
            String requirement,
            ObjectiveAssessment assessment
    ) {
        boolean insufficientEvidence = "INSUFFICIENT_EVIDENCE".equalsIgnoreCase(researchResp.status())
                || "NO_SOURCES".equalsIgnoreCase(researchResp.status())
                || "NO_RESULTS".equalsIgnoreCase(researchResp.status())
                || (sourceUrls.isEmpty() && finalAttributes.isEmpty() && !"COMPLETED".equalsIgnoreCase(researchResp.status()));

        boolean aiDegraded = (!aiSucceeded && !assessmentAiSucceeded && !finalAttributes.isEmpty() && (requirement != null && !requirement.isBlank()));

        if ("FAILED".equalsIgnoreCase(researchResp.status())) {
            return new EnrichmentStatus("FAILED", "Research service failed to locate or resolve entity");
        }
        if (insufficientEvidence) {
            return new EnrichmentStatus("INSUFFICIENT_EVIDENCE", "Sources returned insufficient grounded evidence");
        }
        if (aiDegraded) {
            return new EnrichmentStatus("AI_DEGRADED", "Deterministic fallback used; AI intelligence service unavailable or degraded");
        }
        if ("PARTIAL".equalsIgnoreCase(researchResp.status()) || !unresolvedFields.isEmpty()) {
            return new EnrichmentStatus("PARTIAL", "Enrichment partially completed (" + unresolvedFields.size() + " unresolved fields)");
        }
        return new EnrichmentStatus("COMPLETED", "Enrichment completed (" + finalAttributes.size() + " attributes, tier: " + assessment.priorityTier() + ")");
    }

    private void persistCanonicalProfileSafe(
            String jobId,
            String explicitUserId,
            String entityId,
            String displayName,
            String entityType,
            String canonicalUrl,
            List<EntitySourceDto> entitySources,
            Map<String, EntityAttributeDto> finalAttributes,
            EnrichmentStatus status,
            ProfileAssessmentData assessmentData
    ) {
        String effectiveEntityId = (entityId != null) ? entityId : UUID.randomUUID().toString();
        String profileJson = serializeToJsonSafe(assessmentData.profile(), effectiveEntityId);
        String assessmentJson = serializeToJsonSafe(assessmentData.assessment(), effectiveEntityId);
        String recommendationJson = serializeToJsonSafe(assessmentData.recommendation(), effectiveEntityId);
        String findingsJson = serializeToJsonSafe(assessmentData.findings(), effectiveEntityId);

        try {
            JobState jobState = jobId != null ? jobManager.getJobState(jobId) : null;
            String rowUserId = (explicitUserId != null && !explicitUserId.isBlank())
                    ? explicitUserId
                    : (jobState != null ? jobState.userId : null);

            persistenceService.persistOrUpdate(new PersistEntityRequest(
                    effectiveEntityId,
                    displayName,
                    entityType,
                    canonicalUrl,
                    entitySources,
                    finalAttributes,
                    status.status(),
                    status.statusMessage(),
                    assessmentData.assessment().priorityTier() != null ? assessmentData.assessment().priorityTier().name() : "NONE",
                    assessmentData.assessment().overallScore(),
                    profileJson,
                    assessmentJson,
                    recommendationJson,
                    findingsJson
            ), rowUserId);
        } catch (Exception ex) {
            log.warn("Failed persisting entity {} in database: {}", effectiveEntityId, ex.getMessage());
        }
    }

    private String serializeToJsonSafe(Object value, String entityId) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            log.warn("Failed serializing rich profile JSON for entity {}: {}", entityId, ex.getMessage());
            return null;
        }
    }

    private RowEnrichmentResult buildRowResult(
            String rowId,
            int rowIndex,
            Map<String, String> rawRow,
            EnrichmentStatus status,
            SynthesizedAttributes synthesized,
            String canonicalUrl,
            String entityType,
            List<EntitySourceDto> entitySources,
            String workerId,
            long startedAtMs,
            ProfileAssessmentData assessmentData
    ) {
        return new RowEnrichmentResult(
                rowId,
                rowIndex,
                rawRow,
                status.status(),
                synthesized.displayName(),
                canonicalUrl,
                entityType,
                synthesized.attributes(),
                synthesized.unresolvedFields(),
                synthesized.conflicts(),
                synthesized.confidence(),
                entitySources,
                null,
                status.status(),
                workerId,
                status.statusMessage(),
                startedAtMs,
                System.currentTimeMillis(),
                assessmentData.profile(),
                assessmentData.assessment(),
                assessmentData.recommendation(),
                assessmentData.findings()
        );
    }

    private Instant parseInstantSafe(String raw) {
        if (raw == null || raw.isBlank()) return Instant.now();
        try {
            return Instant.parse(raw);
        } catch (Exception e) {
            return Instant.now();
        }
    }
}

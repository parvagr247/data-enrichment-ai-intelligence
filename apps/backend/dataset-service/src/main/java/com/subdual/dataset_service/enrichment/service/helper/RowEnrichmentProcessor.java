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

        if ((compositeName == null || compositeName.isBlank()) && (url == null || url.isBlank())) {
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

        String displayName = (compositeName != null && !compositeName.isBlank()) ? compositeName : "Unknown";
        String canonicalUrl = url != null ? url : "";

        log.info("[Pipeline: IDENTITY] Row #{} Constructed identity: fullName='{}', firstName='{}', lastName='{}', profileUrl='{}', organization='{}', role='{}', email='{}', location='{}'",
                rowIndex, displayName, firstName, lastName, canonicalUrl, org, role, email, location);

        // 1. Discover Sources via Research Service
        jobManager.emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "DISCOVERING",
                workerId,
                "Querying verified web sources & search indexes...",
                Map.of("name", displayName, "url", canonicalUrl)
        );

        ResearchServiceClient.ResearchCallResponse researchResp = null;
        String researchError = null;
        try {
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (rawRow != null) {
                metadata.putAll(rawRow);
            }
            if (firstName != null && !firstName.isBlank()) metadata.put("firstName", firstName);
            if (lastName != null && !lastName.isBlank()) metadata.put("lastName", lastName);
            if (fullName != null && !fullName.isBlank()) metadata.put("fullName", fullName);
            if (email != null && !email.isBlank()) metadata.put("email", email);
            if (location != null && !location.isBlank()) metadata.put("location", location);

            researchResp = researchServiceClient.executeResearch(new ResearchServiceClient.ResearchCallRequest(
                    url,
                    entityType,
                    displayName,
                    org,
                    role,
                    targetFields,
                    requirement,
                    metadata,
                    firstName,
                    lastName,
                    fullName != null ? fullName : displayName,
                    email,
                    location
            ));
        } catch (Exception ex) {
            log.warn("Research call failed for row {}: {}", rowIndex, ex.getMessage());
            researchError = ex.getMessage();
        }

        if (researchResp == null) {
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
                    "Research service error: " + (researchError != null ? researchError : "No response"),
                    "FAILED",
                    workerId,
                    "Research service failed: " + (researchError != null ? researchError : "No response"),
                    startedAtMs,
                    System.currentTimeMillis()
            );
        }

        if (researchResp.result() != null && researchResp.result().displayName() != null) {
            displayName = researchResp.result().displayName();
        }
        if (researchResp.result() != null && researchResp.result().canonicalUrl() != null) {
            canonicalUrl = researchResp.result().canonicalUrl();
        }

        // 2. Extract Evidence & Sources from Research
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

        jobManager.emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "COLLECTING_SOURCES",
                workerId,
                "Collected and deduplicated " + sourceUrls.size() + " candidate sources...",
                Map.of("sourcesCount", sourceUrls.size())
        );

        jobManager.emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "EXTRACTING_EVIDENCE",
                workerId,
                "Extracting grounded evidence from " + sourceUrls.size() + " discovered sources...",
                Map.of("sourcesCount", sourceUrls.size(), "evidenceCount", evidenceMap.size())
        );

        // 3. AI Enrichment
        jobManager.emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "AI_ENRICHMENT",
                workerId,
                "Synthesizing grounded attributes with AI intelligence...",
                Map.of("evidenceCount", evidenceMap.size(), "targetFields", targetFields)
        );

        AiServiceClient.SynthesisCallResponse aiResp = null;
        try {
            aiResp = aiServiceClient.synthesizeEnrichment(new AiServiceClient.SynthesisCallRequest(
                    rawRow,
                    displayName,
                    entityType,
                    canonicalUrl,
                    requirement,
                    targetFields,
                    evidenceMap,
                    sourceUrls
            ));
        } catch (Exception ex) {
            log.warn("AI synthesis call failed for row {}: {}", rowIndex, ex.getMessage());
        }

        // Assemble final attributes
        Map<String, EntityAttributeDto> finalAttributes = new LinkedHashMap<>();
        List<String> unresolvedFields = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        double confidence = 0.85;

        if (aiResp != null && aiResp.attributes() != null && !aiResp.attributes().isEmpty()) {
            displayName = aiResp.displayName();
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
            // Fallback directly from research evidence
            for (Map.Entry<String, AiServiceClient.FactEvidenceCallDto> entry : evidenceMap.entrySet()) {
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

        // 4. Assess Objective
        jobManager.emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "ASSESSING",
                workerId,
                "Evaluating profile relevance against research objective...",
                Map.of("objective", requirement != null ? requirement : "")
        );

        ResearchObjective objectiveObj = ResearchObjective.from(requirement);
        ProfileAssessmentResponse profileAssessment = null;
        try {
            profileAssessment = aiServiceClient.assessProfile(new ProfileAssessmentRequest(
                    rawRow,
                    displayName,
                    canonicalUrl,
                    entityType,
                    objectiveObj,
                    profileEvidenceMap,
                    sourceUrls,
                    sourceSnippets
            ));
        } catch (Exception ex) {
            log.warn("AI profile assessment call failed for row {}: {}", rowIndex, ex.getMessage());
        }

        ResearchProfile profile;
        ObjectiveAssessment assessment;
        RecommendedApproach recommendation;
        List<ResearchFinding> findings;

        if (profileAssessment != null) {
            profile = profileAssessment.profile();
            assessment = profileAssessment.assessment();
            recommendation = profileAssessment.recommendation();
            findings = profileAssessment.findings() != null ? profileAssessment.findings() : List.of();
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
        } else {
            // Deterministic local fallback if AI service assessment endpoint is unreachable
            boolean hasObj = !objectiveObj.isBlank();
            int score = hasObj ? 50 : 0;
            ObjectiveAssessment.PriorityTier tier = hasObj ? ObjectiveAssessment.PriorityTier.MEDIUM : ObjectiveAssessment.PriorityTier.NONE;
            String whyRel = hasObj ? "Profile identified during enrichment matching basic criteria." : "General profile research completed (no specific objective specified).";

            assessment = new ObjectiveAssessment(score, tier, whyRel, Map.of(), List.of(), List.of());
            recommendation = new RecommendedApproach(
                    RecommendedApproach.ApproachType.NETWORKING_CONVERSATION,
                    "Professional networking outreach",
                    "Ground outreach in verified background.",
                    List.of("Connect referencing current professional role.")
            );
            profile = new ResearchProfile(
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
            findings = List.of();
        }

        // 5. Persist canonical entity
        String entityId = (researchResp.entityId() != null)
                ? researchResp.entityId()
                : UUID.randomUUID().toString();

        jobManager.emitExecutionEvent(
                jobId,
                rowId,
                rowIndex,
                displayName,
                "PROCESSING",
                "PERSISTING",
                workerId,
                "Persisting canonical profile (" + finalAttributes.size() + " attributes) to catalog...",
                Map.of("attributesCount", finalAttributes.size())
        );

        boolean insufficientEvidence = "INSUFFICIENT_EVIDENCE".equalsIgnoreCase(researchResp.status())
                || "NO_SOURCES".equalsIgnoreCase(researchResp.status())
                || "NO_RESULTS".equalsIgnoreCase(researchResp.status())
                || (sourceUrls.isEmpty() && finalAttributes.isEmpty() && !"COMPLETED".equalsIgnoreCase(researchResp.status()));

        boolean aiDegraded = (aiResp == null && profileAssessment == null && !evidenceMap.isEmpty() && (requirement != null && !requirement.isBlank()));

        String status;
        String statusMessage;
        if ("FAILED".equalsIgnoreCase(researchResp.status())) {
            status = "FAILED";
            statusMessage = "Research service failed to locate or resolve entity";
        } else if (insufficientEvidence) {
            status = "INSUFFICIENT_EVIDENCE";
            statusMessage = "Sources returned insufficient grounded evidence";
        } else if (aiDegraded) {
            status = "AI_DEGRADED";
            statusMessage = "Deterministic fallback used; AI intelligence service unavailable or degraded";
        } else if ("PARTIAL".equalsIgnoreCase(researchResp.status()) || !unresolvedFields.isEmpty()) {
            status = "PARTIAL";
            statusMessage = "Enrichment partially completed (" + unresolvedFields.size() + " unresolved fields)";
        } else {
            status = "COMPLETED";
            statusMessage = "Enrichment completed (" + finalAttributes.size() + " attributes, tier: " + assessment.priorityTier() + ")";
        }

        String profileJson = null;
        String assessmentJson = null;
        String recommendationJson = null;
        String findingsJson = null;
        try {
            if (profile != null) profileJson = objectMapper.writeValueAsString(profile);
            if (assessment != null) assessmentJson = objectMapper.writeValueAsString(assessment);
            if (recommendation != null) recommendationJson = objectMapper.writeValueAsString(recommendation);
            if (findings != null) findingsJson = objectMapper.writeValueAsString(findings);
        } catch (Exception ex) {
            log.warn("Failed serializing rich profile JSON for entity {}: {}", entityId, ex.getMessage());
        }

        try {
            JobState jobState = jobId != null ? jobManager.getJobState(jobId) : null;
            String rowUserId = (explicitUserId != null && !explicitUserId.isBlank())
                    ? explicitUserId
                    : (jobState != null ? jobState.userId : null);
            persistenceService.persistOrUpdate(new PersistEntityRequest(
                    entityId,
                    displayName,
                    entityType,
                    canonicalUrl,
                    entitySources,
                    finalAttributes,
                    status,
                    statusMessage,
                    assessment.priorityTier() != null ? assessment.priorityTier().name() : "NONE",
                    assessment.overallScore(),
                    profileJson,
                    assessmentJson,
                    recommendationJson,
                    findingsJson
            ), rowUserId);
        } catch (Exception ex) {
            log.warn("Failed persisting entity {} in database: {}", entityId, ex.getMessage());
        }

        long completedAtMs = System.currentTimeMillis();

        return new RowEnrichmentResult(
                rowId,
                rowIndex,
                rawRow,
                status,
                displayName,
                canonicalUrl,
                entityType,
                finalAttributes,
                unresolvedFields,
                conflicts,
                confidence,
                entitySources,
                null,
                status,
                workerId,
                statusMessage,
                startedAtMs,
                completedAtMs,
                profile,
                assessment,
                recommendation,
                findings
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

package com.subdual.dataset_service.enrichment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.subdual.dataset_service.shared.dto.EntityAttributeDto;
import com.subdual.dataset_service.shared.dto.EntitySourceDto;
import com.subdual.dataset_service.enrichment.dto.FactEvidenceDto;
import com.subdual.dataset_service.enrichment.dto.ProfileAssessmentRequest;
import com.subdual.dataset_service.enrichment.dto.ProfileAssessmentResponse;
import com.subdual.dataset_service.enrichment.dto.RowEnrichmentResult;
import com.subdual.dataset_service.enrichment.EnrichmentEngineFacade;
import com.subdual.dataset_service.enrichment.integration.AiServiceClient;
import com.subdual.dataset_service.enrichment.integration.ResearchServiceClient;
import com.subdual.dataset_service.enrichment.model.JobState;
import com.subdual.dataset_service.enrichment.model.ObjectiveAssessment;
import com.subdual.dataset_service.enrichment.model.RecommendedApproach;
import com.subdual.dataset_service.enrichment.model.ResearchFinding;
import com.subdual.dataset_service.enrichment.model.ResearchObjective;
import com.subdual.dataset_service.enrichment.model.ResearchProfile;
import com.subdual.dataset_service.entity.dto.PersistEntityRequest;

/**
 * Clean processor for single row research, evidence synthesis, profile assessment, and persistence.
 * Uses EnrichmentEngineFacade to interact with downstream research, AI, and database persistence subsystems.
 */
@Component
@Slf4j
public class RowEnrichmentProcessor {

    private final EnrichmentEngineFacade engineFacade;
    private final ObjectMapper objectMapper;
    private final RowIdentityResolver rowIdentityResolver;
    private final EnrichmentJobManager jobManager;

    @Autowired
    public RowEnrichmentProcessor(
            EnrichmentEngineFacade engineFacade,
            ObjectMapper objectMapper,
            RowIdentityResolver rowIdentityResolver,
            EnrichmentJobManager jobManager
    ) {
        this.engineFacade = engineFacade;
        this.objectMapper = objectMapper;
        this.rowIdentityResolver = rowIdentityResolver;
        this.jobManager = jobManager;
    }

    public RowEnrichmentProcessor(EnrichmentEngineFacade engineFacade) {
        this(engineFacade, new ObjectMapper(), new RowIdentityResolver(), new EnrichmentJobManager());
    }

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
        // 1. Identity Resolution
        RowIdentity identity = resolveIdentity(rawRow, mapping, defaultEntityType);
        if (!identity.isValid()) {
            return buildFailureResult(rowId, rowIndex, rawRow, "Missing Identifier", "", identity.entityType(),
                    targetFields, workerId, startedAtMs, "Row missing required name or url identifier");
        }

        emitEvent(jobId, rowId, rowIndex, identity.displayName(), "PROCESSING", "DISCOVERING", workerId,
                "Querying verified web sources & search indexes...", Map.of("name", identity.displayName(), "url", identity.canonicalUrl()));

        // 2. Web Research via Facade
        ResearchServiceClient.ResearchCallResponse researchResp = callResearch(identity, rawRow, targetFields, requirement, rowIndex);
        if (researchResp == null) {
            return buildFailureResult(rowId, rowIndex, rawRow, identity.displayName(), identity.canonicalUrl(),
                    identity.entityType(), targetFields, workerId, startedAtMs, "Research service failed to return data");
        }

        String displayName = (researchResp.result() != null && researchResp.result().displayName() != null)
                ? researchResp.result().displayName() : identity.displayName();
        String canonicalUrl = (researchResp.result() != null && researchResp.result().canonicalUrl() != null)
                ? researchResp.result().canonicalUrl() : identity.canonicalUrl();

        // 3. Extract Discovered Evidence
        EvidenceBundle evidence = extractEvidence(researchResp);
        emitEvent(jobId, rowId, rowIndex, displayName, "PROCESSING", "AI_ENRICHMENT", workerId,
                "Synthesizing grounded attributes with AI intelligence...", Map.of("evidenceCount", evidence.evidenceMap().size()));

        // 4. Attribute Synthesis via Facade
        SynthesisBundle synthesis = synthesizeAttributes(rawRow, displayName, identity.entityType(), canonicalUrl, requirement, targetFields, evidence);

        emitEvent(jobId, rowId, rowIndex, synthesis.displayName(), "PROCESSING", "ASSESSING", workerId,
                "Evaluating profile relevance against research objective...", Map.of("objective", requirement != null ? requirement : ""));

        // 5. Objective Profile Assessment via Facade
        AssessmentBundle assessment = assessProfile(rawRow, synthesis.displayName(), canonicalUrl, identity.entityType(), requirement, evidence, synthesis.attributes());

        emitEvent(jobId, rowId, rowIndex, synthesis.displayName(), "PROCESSING", "PERSISTING", workerId,
                "Persisting canonical profile (" + synthesis.attributes().size() + " attributes) to catalog...", Map.of("attributesCount", synthesis.attributes().size()));

        // 6. Status Determination
        StatusBundle status = resolveStatus(researchResp, evidence.sourceUrls(), synthesis.attributes(),
                synthesis.unresolvedFields(), synthesis.aiSucceeded(), assessment.aiSucceeded(), requirement, assessment.assessment());

        // 7. Relational Persistence via Facade
        persistSafe(jobId, explicitUserId, researchResp.entityId(), synthesis.displayName(), identity.entityType(),
                canonicalUrl, evidence.entitySources(), synthesis.attributes(), status, assessment);

        // 8. Result Construction
        return new RowEnrichmentResult(
                rowId, rowIndex, rawRow, status.status(), synthesis.displayName(), canonicalUrl, identity.entityType(),
                synthesis.attributes(), synthesis.unresolvedFields(), synthesis.conflicts(), synthesis.confidence(),
                evidence.entitySources(), null, status.status(), workerId, status.message(),
                startedAtMs, System.currentTimeMillis(),
                assessment.profile(), assessment.assessment(), assessment.recommendation(), assessment.findings()
        );
    }

    private record RowIdentity(String displayName, String firstName, String lastName, String fullName,
                               String canonicalUrl, String org, String role, String email, String location,
                               String entityType, boolean isValid) {}

    private RowIdentity resolveIdentity(Map<String, String> rawRow, Map<String, String> mapping, String defaultEntityType) {
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
        return new RowIdentity(displayName, firstName, lastName, fullName, url != null ? url : "", org, role, email, location, entityType, isValid);
    }

    private ResearchServiceClient.ResearchCallResponse callResearch(
            RowIdentity id, Map<String, String> rawRow, List<String> targetFields, String req, int rowIndex
    ) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (rawRow != null) meta.putAll(rawRow);
        if (id.firstName() != null) meta.put("firstName", id.firstName());
        if (id.lastName() != null) meta.put("lastName", id.lastName());
        if (id.email() != null) meta.put("email", id.email());
        if (id.location() != null) meta.put("location", id.location());

        return engineFacade.executeResearch(new ResearchServiceClient.ResearchCallRequest(
                id.canonicalUrl(), id.entityType(), id.displayName(), id.org(), id.role(),
                targetFields, req, meta, id.firstName(), id.lastName(),
                id.fullName() != null ? id.fullName() : id.displayName(), id.email(), id.location()
        ));
    }

    private record EvidenceBundle(Map<String, AiServiceClient.FactEvidenceCallDto> evidenceMap,
                                  Map<String, FactEvidenceDto> profileEvidenceMap,
                                  List<String> sourceUrls, List<String> sourceSnippets,
                                  List<EntitySourceDto> entitySources) {}

    private EvidenceBundle extractEvidence(ResearchServiceClient.ResearchCallResponse resp) {
        Map<String, AiServiceClient.FactEvidenceCallDto> evMap = new LinkedHashMap<>();
        Map<String, FactEvidenceDto> profMap = new LinkedHashMap<>();
        List<String> urls = new ArrayList<>();
        List<String> snippets = new ArrayList<>();
        List<EntitySourceDto> sources = new ArrayList<>();

        if (resp.sources() != null) {
            for (var s : resp.sources()) {
                urls.add(s.url());
                if (s.snippet() != null && !s.snippet().isBlank()) snippets.add(s.snippet());
                Instant retrieved = Instant.now();
                try { if (s.retrievedAt() != null) retrieved = Instant.parse(s.retrievedAt()); } catch (Exception ignored) {}
                sources.add(new EntitySourceDto(s.url(), s.title(), s.snippet(), s.sourceType(), s.domain(), s.provider(), s.relevance(), retrieved));
            }
        }
        if (resp.result() != null && resp.result().attributes() != null) {
            resp.result().attributes().forEach((k, t) -> {
                evMap.put(k, new AiServiceClient.FactEvidenceCallDto(k, t.value(), t.sourceUrl(), t.evidenceSnippet(), t.confidence(), t.corroboratingSources(), t.conflictDetected()));
                profMap.put(k, new FactEvidenceDto(k, t.value(), t.sourceUrl(), t.evidenceSnippet(), t.confidence(), t.corroboratingSources(), t.conflictDetected()));
            });
        }
        return new EvidenceBundle(evMap, profMap, urls, snippets, sources);
    }

    private record SynthesisBundle(String displayName, double confidence, Map<String, EntityAttributeDto> attributes,
                                   List<String> unresolvedFields, List<String> conflicts, boolean aiSucceeded) {}

    private SynthesisBundle synthesizeAttributes(
            Map<String, String> rawRow, String displayName, String entityType, String canonicalUrl,
            String req, List<String> targetFields, EvidenceBundle evidence
    ) {
        AiServiceClient.SynthesisCallResponse aiResp = null;
        try {
            aiResp = engineFacade.synthesizeEnrichment(new AiServiceClient.SynthesisCallRequest(
                    rawRow, displayName, entityType, canonicalUrl, req, targetFields, evidence.evidenceMap(), evidence.sourceUrls()
            ));
        } catch (Exception ex) {
            log.warn("AI synthesis failed: {}", ex.getMessage());
        }

        Map<String, EntityAttributeDto> attrs = new LinkedHashMap<>();
        List<String> unresolved = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();

        if (aiResp != null && aiResp.attributes() != null && !aiResp.attributes().isEmpty()) {
            unresolved.addAll(aiResp.unresolvedFields());
            conflicts.addAll(aiResp.conflicts());
            aiResp.attributes().forEach((k, a) -> attrs.put(k, new EntityAttributeDto(
                    a.value(), (a.sources() != null && !a.sources().isEmpty()) ? a.sources().get(0) : null, a.evidence(), a.confidence()
            )));
            return new SynthesisBundle(aiResp.displayName(), aiResp.overallConfidence(), attrs, unresolved, conflicts, true);
        }

        evidence.evidenceMap().forEach((k, f) -> {
            attrs.put(k, new EntityAttributeDto(f.value(), f.sourceUrl(), f.evidenceSnippet(), f.confidence()));
            if ("UNKNOWN".equalsIgnoreCase(f.value())) unresolved.add(k);
        });
        return new SynthesisBundle(displayName, 0.85, attrs, unresolved, conflicts, false);
    }

    private record AssessmentBundle(ResearchProfile profile, ObjectiveAssessment assessment,
                                    RecommendedApproach recommendation, List<ResearchFinding> findings, boolean aiSucceeded) {}

    private AssessmentBundle assessProfile(
            Map<String, String> rawRow, String displayName, String canonicalUrl, String entityType,
            String req, EvidenceBundle evidence, Map<String, EntityAttributeDto> attrs
    ) {
        ResearchObjective objective = ResearchObjective.from(req);
        try {
            ProfileAssessmentResponse resp = engineFacade.assessProfile(new ProfileAssessmentRequest(
                    rawRow, displayName, canonicalUrl, entityType, objective, evidence.profileEvidenceMap(), evidence.sourceUrls(), evidence.sourceSnippets()
            ));
            if (resp != null) {
                syncProfileAttributes(resp.profile(), canonicalUrl, attrs);
                return new AssessmentBundle(resp.profile(), resp.assessment(), resp.recommendation(), resp.findings() != null ? resp.findings() : List.of(), true);
            }
        } catch (Exception ex) {
            log.warn("AI profile assessment failed: {}", ex.getMessage());
        }

        boolean hasObj = !objective.isBlank();
        ObjectiveAssessment assess = new ObjectiveAssessment(hasObj ? 50 : 0, hasObj ? ObjectiveAssessment.PriorityTier.MEDIUM : ObjectiveAssessment.PriorityTier.NONE,
                hasObj ? "Matched basic objective criteria." : "General profile research completed.", Map.of(), List.of(), List.of());
        RecommendedApproach rec = new RecommendedApproach(RecommendedApproach.ApproachType.NETWORKING_CONVERSATION, "Professional networking outreach", "Ground outreach in verified background.", List.of("Connect referencing current role."));
        ResearchProfile prof = new ResearchProfile(
                attrs.containsKey("currentRole") ? attrs.get("currentRole").value() : "UNKNOWN",
                attrs.containsKey("currentOrganization") ? attrs.get("currentOrganization").value() : "UNKNOWN",
                attrs.containsKey("location") ? attrs.get("location").value() : "UNKNOWN",
                displayName + " is an identified professional.", "Identified from public sources.", List.of(), List.of(), List.of(), List.of()
        );
        return new AssessmentBundle(prof, assess, rec, List.of(), false);
    }

    private void syncProfileAttributes(ResearchProfile profile, String url, Map<String, EntityAttributeDto> attrs) {
        if (profile == null) return;
        if (!"UNKNOWN".equals(profile.currentRole()) && !attrs.containsKey("currentRole")) {
            attrs.put("currentRole", new EntityAttributeDto(profile.currentRole(), url, "Extracted from profile", "HIGH"));
        }
        if (!"UNKNOWN".equals(profile.currentOrganization()) && !attrs.containsKey("currentOrganization")) {
            attrs.put("currentOrganization", new EntityAttributeDto(profile.currentOrganization(), url, "Extracted from profile", "HIGH"));
        }
        if (!"UNKNOWN".equals(profile.location()) && !attrs.containsKey("location")) {
            attrs.put("location", new EntityAttributeDto(profile.location(), url, "Extracted from profile", "HIGH"));
        }
    }

    private record StatusBundle(String status, String message) {}

    private StatusBundle resolveStatus(
            ResearchServiceClient.ResearchCallResponse researchResp, List<String> urls,
            Map<String, EntityAttributeDto> attrs, List<String> unresolved, boolean aiOk,
            boolean assessOk, String req, ObjectiveAssessment assess
    ) {
        if ("FAILED".equalsIgnoreCase(researchResp.status())) {
            return new StatusBundle("FAILED", "Research service failed to locate or resolve entity");
        }
        boolean insufficient = "INSUFFICIENT_EVIDENCE".equalsIgnoreCase(researchResp.status())
                || "NO_SOURCES".equalsIgnoreCase(researchResp.status())
                || (urls.isEmpty() && attrs.isEmpty() && !"COMPLETED".equalsIgnoreCase(researchResp.status()));
        if (insufficient) {
            return new StatusBundle("INSUFFICIENT_EVIDENCE", "Sources returned insufficient grounded evidence");
        }
        if (!aiOk && !assessOk && !attrs.isEmpty() && req != null && !req.isBlank()) {
            return new StatusBundle("AI_DEGRADED", "Deterministic fallback used; AI service degraded");
        }
        if ("PARTIAL".equalsIgnoreCase(researchResp.status()) || !unresolved.isEmpty()) {
            return new StatusBundle("PARTIAL", "Enrichment partially completed (" + unresolved.size() + " unresolved fields)");
        }
        return new StatusBundle("COMPLETED", "Enrichment completed (" + attrs.size() + " attributes, tier: " + assess.priorityTier() + ")");
    }

    private void persistSafe(
            String jobId, String explicitUserId, String entityId, String displayName, String entityType,
            String canonicalUrl, List<EntitySourceDto> sources, Map<String, EntityAttributeDto> attrs,
            StatusBundle status, AssessmentBundle assess
    ) {
        String effId = entityId != null ? entityId : UUID.randomUUID().toString();
        try {
            JobState jobState = jobId != null ? jobManager.getJobState(jobId) : null;
            String userId = (explicitUserId != null && !explicitUserId.isBlank()) ? explicitUserId : (jobState != null ? jobState.userId : null);

            engineFacade.persistOrUpdateEntity(new PersistEntityRequest(
                    effId, displayName, entityType, canonicalUrl, sources, attrs,
                    status.status(), status.message(),
                    assess.assessment().priorityTier() != null ? assess.assessment().priorityTier().name() : "NONE",
                    assess.assessment().overallScore(),
                    toJson(assess.profile()), toJson(assess.assessment()), toJson(assess.recommendation()), toJson(assess.findings())
            ), userId);
        } catch (Exception ex) {
            log.warn("Failed persisting entity {} in catalog: {}", effId, ex.getMessage());
        }
    }

    private String toJson(Object obj) {
        if (obj == null) return null;
        try { return objectMapper.writeValueAsString(obj); } catch (Exception e) { return null; }
    }

    private void emitEvent(String jobId, String rowId, int rowIndex, String entity, String status, String stage,
                           String workerId, String msg, Map<String, Object> meta) {
        jobManager.emitExecutionEvent(jobId, rowId, rowIndex, entity, status, stage, workerId, msg, meta);
    }

    private RowEnrichmentResult buildFailureResult(
            String rowId, int rowIndex, Map<String, String> rawRow, String name, String url,
            String entityType, List<String> targetFields, String workerId, long startedAtMs, String error
    ) {
        return new RowEnrichmentResult(
                rowId, rowIndex, rawRow, "FAILED", name, url, entityType, Map.of(), targetFields, List.of(), 0.0,
                List.of(), error, "FAILED", workerId, error, startedAtMs, System.currentTimeMillis()
        );
    }
}

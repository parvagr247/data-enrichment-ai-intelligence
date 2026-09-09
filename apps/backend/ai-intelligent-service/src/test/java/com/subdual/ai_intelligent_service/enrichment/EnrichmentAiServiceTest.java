package com.subdual.ai_intelligent_service.enrichment;

import com.subdual.ai_intelligent_service.configuration.AiProperties;
import com.subdual.ai_intelligent_service.enrichment.api.dto.common.FactEvidenceDto;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.EnrichmentSynthesisRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.request.RequirementInterpretationRequest;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.AIEnrichmentResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.EnrichedAttributeResult;
import com.subdual.ai_intelligent_service.enrichment.api.dto.response.RequirementInterpretationResponse;
import com.subdual.ai_intelligent_service.enrichment.service.helper.DeterministicEnrichmentHelper;
import com.subdual.ai_intelligent_service.enrichment.service.impl.SpringAiEnrichmentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class EnrichmentAiServiceTest {

    private SpringAiEnrichmentService enrichmentService;

    @BeforeEach
    void setUp() {
        AiProperties properties = new AiProperties("gemini-2.5-flash", 0.1, true);
        DeterministicEnrichmentHelper helper = new DeterministicEnrichmentHelper();
        enrichmentService = new SpringAiEnrichmentService(Optional.empty(), properties, "mock-key", helper);
    }

    @Test
    @DisplayName("Should return sensible default scope when requirement is blank")
    void shouldReturnDefaultScopeForBlankRequirement() {
        RequirementInterpretationRequest request = new RequirementInterpretationRequest(
                "",
                "PERSON",
                Map.of("First Name", "Jane", "Company", "Auria")
        );

        RequirementInterpretationResponse response = enrichmentService.interpretRequirement(request);

        assertNotNull(response);
        assertTrue(response.isDefaultScope());
        assertTrue(response.requestedFields().contains("currentOrganization"));
        assertTrue(response.requestedFields().contains("currentRole"));
        assertTrue(response.requestedFields().contains("education"));
    }

    @Test
    @DisplayName("Should extract requested fields from natural language requirement")
    void shouldExtractRequestedFieldsFromRequirement() {
        RequirementInterpretationRequest request = new RequirementInterpretationRequest(
                "Find current company, role, education, technical skills and location",
                "PERSON",
                Map.of("name", "Alice")
        );

        RequirementInterpretationResponse response = enrichmentService.interpretRequirement(request);

        assertNotNull(response);
        assertFalse(response.isDefaultScope());
        assertTrue(response.requestedFields().contains("currentOrganization"));
        assertTrue(response.requestedFields().contains("currentRole"));
        assertTrue(response.requestedFields().contains("education"));
        assertTrue(response.requestedFields().contains("skills"));
        assertTrue(response.requestedFields().contains("location"));
    }

    @Test
    @DisplayName("Should synthesize enrichment preserving evidence and flagging unresolved fields")
    void shouldSynthesizeEnrichmentWithUnresolvedAndPreservedEvidence() {
        Map<String, FactEvidenceDto> evidence = Map.of(
                "currentOrganization", new FactEvidenceDto(
                        "currentOrganization",
                        "Auria Inc",
                        "https://example.com/profile",
                        "Works at Auria Inc as Lead Architect",
                        "HIGH",
                        List.of("https://example.com/profile"),
                        false
                )
        );

        EnrichmentSynthesisRequest request = new EnrichmentSynthesisRequest(
                Map.of("Company", "auria"),
                "Jane Doe",
                "PERSON",
                "https://example.com/profile",
                "Find current company and skills",
                List.of("currentOrganization", "skills"),
                evidence,
                List.of("https://example.com/profile")
        );

        AIEnrichmentResult result = enrichmentService.synthesizeEnrichment(request);

        assertNotNull(result);
        assertEquals("Jane Doe", result.displayName());

        // Verified attribute
        EnrichedAttributeResult orgAttr = result.attributes().get("currentOrganization");
        assertNotNull(orgAttr);
        assertEquals("Auria Inc", orgAttr.value());
        assertEquals("auria", orgAttr.originalValue());
        assertEquals("VERIFIED", orgAttr.status());

        // Unresolved attribute
        EnrichedAttributeResult skillsAttr = result.attributes().get("skills");
        assertNotNull(skillsAttr);
        assertEquals("UNKNOWN", skillsAttr.value());
        assertEquals("UNRESOLVED", skillsAttr.status());
        assertTrue(result.unresolvedFields().contains("skills"));
    }

    @Test
    @DisplayName("Should detect and flag conflicting evidence across research sources")
    void shouldDetectConflictingEvidence() {
        Map<String, FactEvidenceDto> evidence = Map.of(
                "currentOrganization", new FactEvidenceDto(
                        "currentOrganization",
                        "Beta Corp",
                        "https://source2.com",
                        "Recently joined Beta Corp",
                        "MEDIUM",
                        List.of("https://source1.com", "https://source2.com"),
                        true // Conflict detected!
                )
        );

        EnrichmentSynthesisRequest request = new EnrichmentSynthesisRequest(
                Map.of(),
                "Bob Smith",
                "PERSON",
                "https://source1.com",
                null,
                List.of("currentOrganization"),
                evidence,
                List.of("https://source1.com", "https://source2.com")
        );

        AIEnrichmentResult result = enrichmentService.synthesizeEnrichment(request);

        assertNotNull(result);
        assertEquals("CONFLICT", result.attributes().get("currentOrganization").status());
        assertFalse(result.conflicts().isEmpty());
    }
}

package com.subdual.dataset_service.service;

import com.subdual.dataset_service.dto.request.EnrichmentJobRequest;
import com.subdual.dataset_service.dto.request.ProfileAssessmentRequest;
import com.subdual.dataset_service.dto.request.SingleEnrichmentRequest;
import com.subdual.dataset_service.dto.response.EnrichmentJobResponse;
import com.subdual.dataset_service.dto.response.ProfileAssessmentResponse;
import com.subdual.dataset_service.dto.response.RowEnrichmentResult;
import com.subdual.dataset_service.integration.client.AiServiceClient;
import com.subdual.dataset_service.integration.client.ResearchServiceClient;
import com.subdual.dataset_service.profile.model.ObjectiveAssessment;
import com.subdual.dataset_service.profile.model.RecommendedApproach;
import com.subdual.dataset_service.profile.model.ResearchFinding;
import com.subdual.dataset_service.profile.model.ResearchProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DatasetEnrichmentServiceTest {

    @Mock
    private ResearchServiceClient researchServiceClient;

    @Mock
    private AiServiceClient aiServiceClient;

    @Mock
    private EntityPersistenceService persistenceService;

    private DefaultDatasetEnrichmentService enrichmentService;

    private com.subdual.dataset_service.service.executor.EnrichmentTaskExecutor taskExecutor;

    @BeforeEach
    void setUp() {
        taskExecutor = new com.subdual.dataset_service.service.executor.EnrichmentTaskExecutor(
                new com.subdual.dataset_service.config.EnrichmentProperties(
                        new com.subdual.dataset_service.config.EnrichmentProperties.Execution(3, 100),
                        new com.subdual.dataset_service.config.EnrichmentProperties.Research(5),
                        new com.subdual.dataset_service.config.EnrichmentProperties.Timeout(60, 30),
                        new com.subdual.dataset_service.config.EnrichmentProperties.Ai(true)
                )
        );
        enrichmentService = new DefaultDatasetEnrichmentService(
                researchServiceClient,
                aiServiceClient,
                persistenceService,
                Executors.newSingleThreadExecutor(),
                taskExecutor
        );
    }

    @Test
    @DisplayName("Should handle single row enrichment with non-blank requirement")
    void shouldHandleSingleRowEnrichmentWithRequirement() {
        when(aiServiceClient.interpretRequirement(anyString(), anyString(), any()))
                .thenReturn(new AiServiceClient.RequirementCallResponse(
                        List.of("currentOrganization", "skills"),
                        "User specified skills and company",
                        false
                ));

        when(researchServiceClient.executeResearch(any()))
                .thenReturn(new ResearchServiceClient.ResearchCallResponse(
                        "COMPLETED",
                        "ent-123",
                        new ResearchServiceClient.ResearchCallResult(
                                "Jane Doe",
                                "PERSON",
                                "https://example.com/jane",
                                Map.of("currentOrganization", new ResearchServiceClient.EvidenceTupleDto(
                                        "Auria",
                                        "https://example.com/jane",
                                        "Jane works at Auria",
                                        "HIGH",
                                        List.of("https://example.com/jane"),
                                        false
                                ))
                        ),
                        List.of(),
                        100,
                        List.of()
                ));

        when(aiServiceClient.synthesizeEnrichment(any()))
                .thenReturn(new AiServiceClient.SynthesisCallResponse(
                        "Jane Doe",
                        "PERSON",
                        "https://example.com/jane",
                        Map.of(
                                "currentOrganization", new AiServiceClient.AttributeResultCallDto(
                                        "currentOrganization",
                                        "Auria",
                                        "auria",
                                        "HIGH",
                                        "VERIFIED",
                                        List.of("https://example.com/jane"),
                                        "Jane works at Auria",
                                        "Verified"
                                ),
                                "skills", new AiServiceClient.AttributeResultCallDto(
                                        "skills",
                                        "UNKNOWN",
                                        null,
                                        "UNKNOWN",
                                        "UNRESOLVED",
                                        List.of(),
                                        "",
                                        "No evidence found"
                                )
                        ),
                        List.of("skills"),
                        List.of(),
                        0.80,
                        "mock-model",
                        50
                ));

        SingleEnrichmentRequest request = new SingleEnrichmentRequest(
                Map.of("Name", "Jane Doe", "Company", "auria"),
                Map.of("nameColumn", "Name", "organizationColumn", "Company"),
                "PERSON",
                "Find current company and skills"
        );

        RowEnrichmentResult result = enrichmentService.enrichSingle(request);

        assertNotNull(result);
        assertEquals("Jane Doe", result.displayName());
        assertEquals("PARTIAL", result.status()); // Has unresolved 'skills'
        assertTrue(result.unresolvedFields().contains("skills"));
        assertEquals("Auria", result.attributes().get("currentOrganization").value());
    }

    @Test
    @DisplayName("Should isolate row failure without destroying the job")
    void shouldIsolateRowFailureInBatch() throws InterruptedException {
        when(aiServiceClient.interpretRequirement(any(), any(), any()))
                .thenReturn(new AiServiceClient.RequirementCallResponse(
                        List.of("currentOrganization"),
                        "Default scope",
                        true
                ));

        // Deterministic mock based on row entity name
        when(researchServiceClient.executeResearch(argThat(r -> r != null && "Alice Smith".equals(r.name()))))
                .thenReturn(new ResearchServiceClient.ResearchCallResponse(
                        "COMPLETED",
                        "ent-good",
                        new ResearchServiceClient.ResearchCallResult(
                                "Alice Smith",
                                "PERSON",
                                "https://example.com/alice",
                                Map.of()
                        ),
                        List.of(),
                        50,
                        List.of()
                ));

        when(researchServiceClient.executeResearch(argThat(r -> r != null && "Bad Row Data".equals(r.name()))))
                .thenThrow(new RuntimeException("Simulated upstream network error"));

        EnrichmentJobRequest request = new EnrichmentJobRequest(
                "test-dataset.csv",
                null,
                "PERSON",
                Map.of("nameColumn", "Name"),
                List.of(
                        Map.of("Name", "Alice Smith"),
                        Map.of("Name", "Bad Row Data")
                )
        );

        EnrichmentJobResponse submitted = enrichmentService.createAndSubmitJob(request);
        assertNotNull(submitted);
        assertNotNull(submitted.jobId());

        // Wait for executor to complete 2 rows
        long deadline = System.currentTimeMillis() + 3000;
        EnrichmentJobResponse completedJob = enrichmentService.getJob(submitted.jobId()).orElseThrow();
        while (completedJob.completedRows() + completedJob.failedRows() < 2 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            completedJob = enrichmentService.getJob(submitted.jobId()).orElseThrow();
        }

        assertEquals(2, completedJob.totalRows());
        assertEquals(1, completedJob.completedRows());
        assertEquals(1, completedJob.failedRows());
        assertEquals(2, completedJob.rowResults().size());

        RowEnrichmentResult row1 = completedJob.rowResults().get(0);
        RowEnrichmentResult row2 = completedJob.rowResults().get(1);

        assertEquals("COMPLETED", row1.status());
        assertEquals("FAILED", row2.status());
        assertTrue(row2.errorMessage().contains("Simulated upstream network error"));
    }

    @Test
    @DisplayName("Should execute multiple rows in bounded parallel fashion")
    void shouldExecuteRowsConcurrently() throws InterruptedException {
        when(aiServiceClient.interpretRequirement(any(), any(), any()))
                .thenReturn(new AiServiceClient.RequirementCallResponse(List.of("currentRole"), "Role scope", true));

        java.util.concurrent.atomic.AtomicInteger concurrentCalls = new java.util.concurrent.atomic.AtomicInteger(0);
        java.util.concurrent.atomic.AtomicInteger maxConcurrentSeen = new java.util.concurrent.atomic.AtomicInteger(0);

        when(researchServiceClient.executeResearch(any())).thenAnswer(invocation -> {
            int current = concurrentCalls.incrementAndGet();
            maxConcurrentSeen.updateAndGet(prev -> Math.max(prev, current));
            Thread.sleep(100);
            concurrentCalls.decrementAndGet();
            return new ResearchServiceClient.ResearchCallResponse(
                    "COMPLETED",
                    "ent-test",
                    new ResearchServiceClient.ResearchCallResult("Test User", "PERSON", "https://example.com/user", Map.of()),
                    List.of(),
                    100,
                    List.of()
            );
        });

        EnrichmentJobRequest request = new EnrichmentJobRequest(
                "parallel-dataset.csv",
                null,
                "PERSON",
                Map.of("nameColumn", "Name"),
                List.of(
                        Map.of("Name", "User 1"),
                        Map.of("Name", "User 2"),
                        Map.of("Name", "User 3"),
                        Map.of("Name", "User 4")
                )
        );

        EnrichmentJobResponse submitted = enrichmentService.createAndSubmitJob(request);
        assertNotNull(submitted);

        // Wait for workers to complete 4 rows
        long deadline = System.currentTimeMillis() + 4000;
        EnrichmentJobResponse completedJob = enrichmentService.getJob(submitted.jobId()).orElseThrow();
        while (completedJob.completedRows() + completedJob.failedRows() < 4 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
            completedJob = enrichmentService.getJob(submitted.jobId()).orElseThrow();
        }

        assertEquals(4, completedJob.totalRows());
        assertEquals(4, completedJob.completedRows());
        assertEquals(0, completedJob.failedRows());
        assertEquals(4, completedJob.rowResults().size());
        assertTrue(maxConcurrentSeen.get() >= 2, "Expected at least 2 threads executing concurrently, saw: " + maxConcurrentSeen.get());
        assertTrue(maxConcurrentSeen.get() <= 3, "Expected at most bounded concurrency (3) threads executing, saw: " + maxConcurrentSeen.get());
    }

    @Test
    @DisplayName("Should support cancelling an in-flight job")
    void shouldCancelInFlightJob() {
        EnrichmentJobRequest request = new EnrichmentJobRequest(
                "cancel-test.csv",
                null,
                "PERSON",
                Map.of("nameColumn", "Name"),
                List.of(Map.of("Name", "User A"))
        );

        EnrichmentJobResponse submitted = enrichmentService.createAndSubmitJob(request);
        boolean cancelled = enrichmentService.cancelJob(submitted.jobId());
        assertTrue(cancelled);

        EnrichmentJobResponse job = enrichmentService.getJob(submitted.jobId()).orElseThrow();
        assertEquals("CANCELLED", job.status());
    }

    @Test
    @DisplayName("Should subscribe to job events and expose configured concurrency")
    void shouldSubscribeToJobEventsAndExposeConcurrency() {
        EnrichmentJobRequest request = new EnrichmentJobRequest(
                "sse-test.csv",
                null,
                "PERSON",
                Map.of("nameColumn", "Name"),
                List.of(Map.of("Name", "Event User"))
        );

        EnrichmentJobResponse submitted = enrichmentService.createAndSubmitJob(request);
        assertNotNull(submitted);
        assertEquals(3, submitted.concurrency());

        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter =
                enrichmentService.subscribeJobEvents(submitted.jobId());
        assertNotNull(emitter);
    }

    @Test
    @DisplayName("Should enrich single row with deep research profile and objective assessment")
    void shouldEnrichWithDeepResearchProfileAndObjectiveAssessment() {
        when(aiServiceClient.interpretRequirement(anyString(), anyString(), any()))
                .thenReturn(new AiServiceClient.RequirementCallResponse(
                        List.of("currentRole", "currentOrganization"),
                        "Identify recruiters or leaders for Java internship",
                        false
                ));

        when(researchServiceClient.executeResearch(any()))
                .thenReturn(new ResearchServiceClient.ResearchCallResponse(
                        "COMPLETED",
                        "ent-profile-1",
                        new ResearchServiceClient.ResearchCallResult(
                                "Alex Rivera",
                                "PERSON",
                                "https://linkedin.com/in/alex-rivera-tech-recruiter",
                                Map.of("currentRole", new ResearchServiceClient.EvidenceTupleDto(
                                        "Senior Technical Recruiter",
                                        "https://linkedin.com/in/alex-rivera-tech-recruiter",
                                        "Alex is a Senior Technical Recruiter at NexaCorp",
                                        "HIGH",
                                        List.of(),
                                        false
                                ))
                        ),
                        List.of(),
                        80,
                        List.of()
                ));

        when(aiServiceClient.assessProfile(any(ProfileAssessmentRequest.class)))
                .thenReturn(new ProfileAssessmentResponse(
                        "Alex Rivera",
                        "https://linkedin.com/in/alex-rivera-tech-recruiter",
                        "PERSON",
                        new ResearchProfile(
                                "Senior Technical Recruiter",
                                "NexaCorp",
                                "Austin, TX",
                                "Alex Rivera is a Senior Technical Recruiter specializing in backend engineering and university hiring.",
                                "Extensive 7-year background in technology recruiting.",
                                List.of("Java", "Spring Boot", "Technical Recruiting", "Talent Sourcing"),
                                List.of(new ResearchProfile.ExperienceItem("Senior Technical Recruiter", "NexaCorp", "2021 - Present", "Leads backend engineering recruiting.", "Recruits for Java/Spring engineers.")),
                                List.of(),
                                List.of(new ResearchProfile.ActivityItem("Hiring Spring Boot Interns for Fall 2026", "AUTHORED", "Looking for talented backend engineers.", "HIRING_ANNOUNCEMENT", "https://linkedin.com/posts/alex-rivera-1"))
                        ),
                        new ObjectiveAssessment(
                                94,
                                ObjectiveAssessment.PriorityTier.HIGH,
                                "Direct decision maker actively hiring Java and backend engineering interns.",
                                Map.of("hiringRelevance", new ObjectiveAssessment.DimensionalScore(95, "Actively posted hiring announcement for Java roles.")),
                                List.of("Active hiring posts", "Direct recruiter for target stack"),
                                List.of()
                        ),
                        new RecommendedApproach(
                                RecommendedApproach.ApproachType.RECRUITER_OUTREACH,
                                "Direct recruiter outreach referencing recent post",
                                "Alex explicitly invited candidates to reach out regarding Spring Boot roles.",
                                List.of("Reference recent post regarding Fall 2026 backend interns", "Highlight Java and Spring Boot experience")
                        ),
                        List.of(new ResearchFinding("Active Technical Recruiter at NexaCorp", ResearchFinding.FindingType.FACT_SOURCE_DERIVED, "HIGH", "https://linkedin.com/in/alex-rivera-tech-recruiter", "Alex is a Senior Technical Recruiter at NexaCorp", "LinkedIn")),
                        Map.of()
                ));

        SingleEnrichmentRequest request = new SingleEnrichmentRequest(
                Map.of("Name", "Alex Rivera", "URL", "https://linkedin.com/in/alex-rivera-tech-recruiter"),
                Map.of("nameColumn", "Name", "urlColumn", "URL"),
                "PERSON",
                "Identify recruiters or leaders for Java internship"
        );

        RowEnrichmentResult result = enrichmentService.enrichSingle(request);

        assertNotNull(result);
        assertEquals("Alex Rivera", result.displayName());
        assertEquals("COMPLETED", result.status());

        // Verify Deep Research Profile
        assertNotNull(result.profile());
        assertEquals("Senior Technical Recruiter", result.profile().currentRole());
        assertEquals("NexaCorp", result.profile().currentOrganization());
        assertTrue(result.profile().technicalExpertise().contains("Java"));
        assertEquals(1, result.profile().publicActivity().size());

        // Verify Objective Assessment
        assertNotNull(result.assessment());
        assertEquals(94, result.assessment().overallScore());
        assertEquals(ObjectiveAssessment.PriorityTier.HIGH, result.assessment().priorityTier());
        assertTrue(result.assessment().whyRelevant().contains("Direct decision maker"));

        // Verify Recommended Approach
        assertNotNull(result.recommendation());
        assertEquals(RecommendedApproach.ApproachType.RECRUITER_OUTREACH, result.recommendation().approachType());
        assertEquals(2, result.recommendation().suggestedTalkingPoints().size());

        // Verify Grounded Findings
        assertNotNull(result.findings());
        assertEquals(1, result.findings().size());
        assertEquals(ResearchFinding.FindingType.FACT_SOURCE_DERIVED, result.findings().get(0).findingType());
    }

    @Test
    @DisplayName("Should provide neutral assessment when objective is blank")
    void shouldProvideNeutralAssessmentWhenObjectiveIsBlank() {
        when(aiServiceClient.interpretRequirement(any(), any(), any()))
                .thenReturn(new AiServiceClient.RequirementCallResponse(
                        List.of("currentRole"),
                        "Default scope",
                        true
                ));

        when(researchServiceClient.executeResearch(any()))
                .thenReturn(new ResearchServiceClient.ResearchCallResponse(
                        "COMPLETED",
                        "ent-blank-1",
                        new ResearchServiceClient.ResearchCallResult(
                                "John Candidate",
                                "PERSON",
                                "https://example.com/john",
                                Map.of()
                        ),
                        List.of(),
                        50,
                        List.of()
                ));

        when(aiServiceClient.assessProfile(any(ProfileAssessmentRequest.class)))
                .thenReturn(new ProfileAssessmentResponse(
                        "John Candidate",
                        "https://example.com/john",
                        "PERSON",
                        new ResearchProfile("UNKNOWN", "UNKNOWN", "UNKNOWN", "General profile.", "General background.", List.of(), List.of(), List.of(), List.of()),
                        ObjectiveAssessment.neutral("No objective specified"),
                        new RecommendedApproach(RecommendedApproach.ApproachType.NETWORKING_CONVERSATION, "Networking", "Default", List.of()),
                        List.of(),
                        Map.of()
                ));

        SingleEnrichmentRequest request = new SingleEnrichmentRequest(
                Map.of("Name", "John Candidate"),
                Map.of("nameColumn", "Name"),
                "PERSON",
                ""
        );

        RowEnrichmentResult result = enrichmentService.enrichSingle(request);

        assertNotNull(result);
        assertNotNull(result.assessment());
        assertEquals(0, result.assessment().overallScore());
        assertEquals(ObjectiveAssessment.PriorityTier.NONE, result.assessment().priorityTier());
    }

    @Test
    @DisplayName("Should report AI_DEGRADED when AI service fails with evidence")
    void shouldReportAiDegradedWhenAiServiceFailsWithEvidence() {
        when(aiServiceClient.interpretRequirement(anyString(), anyString(), any()))
                .thenReturn(new AiServiceClient.RequirementCallResponse(
                        List.of("currentRole"),
                        "Target role",
                        false
                ));

        when(researchServiceClient.executeResearch(any()))
                .thenReturn(new ResearchServiceClient.ResearchCallResponse(
                        "COMPLETED",
                        "ent-degraded-1",
                        new ResearchServiceClient.ResearchCallResult(
                                "Bob Miller",
                                "PERSON",
                                "https://example.com/bob",
                                Map.of("currentRole", new ResearchServiceClient.EvidenceTupleDto(
                                        "Software Engineer",
                                        "https://example.com/bob",
                                        "Bob is a Software Engineer",
                                        "HIGH",
                                        List.of("https://example.com/bob"),
                                        false
                                ))
                        ),
                        List.of(new ResearchServiceClient.SourceItemDto("https://example.com/bob", "Bob Profile", "Bob is a Software Engineer", "PROFILE", "example.com", "tavily", 0.9, "2026-09-06T00:00:00Z")),
                        100,
                        List.of()
                ));

        when(aiServiceClient.synthesizeEnrichment(any())).thenReturn(null);
        when(aiServiceClient.assessProfile(any())).thenReturn(null);

        SingleEnrichmentRequest request = new SingleEnrichmentRequest(
                Map.of("Name", "Bob Miller"),
                Map.of("nameColumn", "Name"),
                "PERSON",
                "Identify Java Engineers"
        );

        RowEnrichmentResult result = enrichmentService.enrichSingle(request);

        assertNotNull(result);
        assertEquals("AI_DEGRADED", result.status());
        assertTrue(result.message().contains("Deterministic fallback used"));
        assertEquals("Software Engineer", result.attributes().get("currentRole").value());
    }

    @Test
    @DisplayName("Should report INSUFFICIENT_EVIDENCE when research yields insufficient evidence")
    void shouldReportInsufficientEvidenceWhenResearchYieldsInsufficientEvidence() {
        when(aiServiceClient.interpretRequirement(anyString(), anyString(), any()))
                .thenReturn(new AiServiceClient.RequirementCallResponse(
                        List.of("currentRole"),
                        "Target role",
                        false
                ));

        when(researchServiceClient.executeResearch(any()))
                .thenReturn(new ResearchServiceClient.ResearchCallResponse(
                        "INSUFFICIENT_EVIDENCE",
                        "ent-insufficient-1",
                        new ResearchServiceClient.ResearchCallResult(
                                "Unknown Ghost",
                                "PERSON",
                                "",
                                Map.of()
                        ),
                        List.of(),
                        50,
                        List.of()
                ));

        SingleEnrichmentRequest request = new SingleEnrichmentRequest(
                Map.of("Name", "Unknown Ghost"),
                Map.of("nameColumn", "Name"),
                "PERSON",
                "Identify Java Engineers"
        );

        RowEnrichmentResult result = enrichmentService.enrichSingle(request);

        assertNotNull(result);
        assertEquals("INSUFFICIENT_EVIDENCE", result.status());
        assertTrue(result.message().contains("insufficient grounded evidence"));
    }

    @Test
    @DisplayName("Should correctly build composite name with sanitization")
    void shouldBuildCompositeNameWithSanitization() {
        // Case A: First + Last
        assertEquals("Vardhan Bhati", DefaultDatasetEnrichmentService.buildCompositeName("Vardhan", "Bhati", null, null));

        // Case B: Extra whitespace & casing
        assertEquals("Krati Mittal", DefaultDatasetEnrichmentService.buildCompositeName("  Krati  ", " Mittal ", null, null));

        // Case C: Full name present takes precedence
        assertEquals("Vardhan Bhati", DefaultDatasetEnrichmentService.buildCompositeName("Vardhan", "Bhati", "Vardhan Bhati", null));

        // Case D: First Name only
        assertEquals("Vardhan", DefaultDatasetEnrichmentService.buildCompositeName("Vardhan", null, null, null));

        // Case E: Last Name only
        assertEquals("Bhati", DefaultDatasetEnrichmentService.buildCompositeName(null, "Bhati", null, null));

        // Case F: "null" / "undefined" string sanitization
        assertEquals("Vardhan", DefaultDatasetEnrichmentService.buildCompositeName("Vardhan", "null", null, null));
        assertEquals("Bhati", DefaultDatasetEnrichmentService.buildCompositeName("undefined", "Bhati", null, null));
        assertNull(DefaultDatasetEnrichmentService.buildCompositeName("null", "undefined", null, null));

        // Case G: Legacy fallback
        assertEquals("Legacy Name", DefaultDatasetEnrichmentService.buildCompositeName(null, null, null, "Legacy Name"));
    }

    @Test
    @DisplayName("Should extract composite identity anchors and pass complete context to research client")
    void shouldExtractCompositeIdentityAndPassToResearchClient() {
        when(aiServiceClient.interpretRequirement(anyString(), anyString(), any()))
                .thenReturn(new AiServiceClient.RequirementCallResponse(
                        List.of("currentRole", "skills"),
                        "Target role and skills",
                        false
                ));

        org.mockito.ArgumentCaptor<ResearchServiceClient.ResearchCallRequest> captor =
                org.mockito.ArgumentCaptor.forClass(ResearchServiceClient.ResearchCallRequest.class);

        when(researchServiceClient.executeResearch(captor.capture()))
                .thenReturn(new ResearchServiceClient.ResearchCallResponse(
                        "COMPLETED",
                        "ent-vardhan-1",
                        new ResearchServiceClient.ResearchCallResult(
                                "Vardhan Bhati",
                                "PERSON",
                                "https://www.linkedin.com/in/vardhan-bhati-33b537326",
                                Map.of("currentRole", new ResearchServiceClient.EvidenceTupleDto(
                                        "Lead",
                                        "https://www.linkedin.com/in/vardhan-bhati-33b537326",
                                        "Lead at EDC MNIT",
                                        "HIGH",
                                        List.of("https://www.linkedin.com/in/vardhan-bhati-33b537326"),
                                        false
                                ))
                        ),
                        List.of(),
                        100,
                        List.of()
                ));

        when(aiServiceClient.synthesizeEnrichment(any()))
                .thenReturn(new AiServiceClient.SynthesisCallResponse(
                        "Vardhan Bhati",
                        "PERSON",
                        "https://www.linkedin.com/in/vardhan-bhati-33b537326",
                        Map.of(
                                "currentOrganization", new AiServiceClient.AttributeResultCallDto(
                                        "currentOrganization",
                                        "MNIT Jaipur",
                                        "mnit jaipur",
                                        "HIGH",
                                        "VERIFIED",
                                        List.of("https://www.linkedin.com/in/vardhan-bhati-33b537326"),
                                        "Works at MNIT Jaipur",
                                        "Verified"
                                )
                        ),
                        List.of(),
                        List.of(),
                        0.95,
                        "mock-model",
                        50
                ));

        SingleEnrichmentRequest request = new SingleEnrichmentRequest(
                Map.of(
                        "First Name", "Vardhan",
                        "Last Name", "Bhati",
                        "URL Link", "https://www.linkedin.com/in/vardhan-bhati-33b537326",
                        "Company Name", "MNIT Jaipur / Entrepreneurship Development Cell",
                        "Position", "Lead",
                        "Email", "vardhan@example.com",
                        "Location", "Jaipur, India"
                ),
                Map.of(
                        "firstNameColumn", "First Name",
                        "lastNameColumn", "Last Name",
                        "urlColumn", "URL Link",
                        "organizationColumn", "Company Name",
                        "roleColumn", "Position",
                        "emailColumn", "Email",
                        "locationColumn", "Location"
                ),
                "PERSON",
                "Identify Engineering Leaders"
        );

        RowEnrichmentResult result = enrichmentService.enrichSingle(request);

        assertNotNull(result);
        assertEquals("COMPLETED", result.status());
        assertEquals("Vardhan Bhati", result.displayName());

        ResearchServiceClient.ResearchCallRequest capturedRequest = captor.getValue();
        assertNotNull(capturedRequest);
        assertEquals("Vardhan Bhati", capturedRequest.name());
        assertEquals("Vardhan", capturedRequest.firstName());
        assertEquals("Bhati", capturedRequest.lastName());
        assertEquals("Vardhan Bhati", capturedRequest.fullName());
        assertEquals("https://www.linkedin.com/in/vardhan-bhati-33b537326", capturedRequest.url());
        assertEquals("MNIT Jaipur / Entrepreneurship Development Cell", capturedRequest.organization());
        assertEquals("Lead", capturedRequest.role());
        assertEquals("vardhan@example.com", capturedRequest.email());
        assertEquals("Jaipur, India", capturedRequest.location());
    }
}

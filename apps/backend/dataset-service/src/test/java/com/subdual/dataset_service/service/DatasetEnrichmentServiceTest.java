package com.subdual.dataset_service.service;

import com.subdual.dataset_service.dto.EnrichmentJobRequest;
import com.subdual.dataset_service.dto.EnrichmentJobResponse;
import com.subdual.dataset_service.dto.RowEnrichmentResult;
import com.subdual.dataset_service.dto.SingleEnrichmentRequest;
import com.subdual.dataset_service.integration.client.AiServiceClient;
import com.subdual.dataset_service.integration.client.ResearchServiceClient;
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

    @BeforeEach
    void setUp() {
        enrichmentService = new DefaultDatasetEnrichmentService(
                researchServiceClient,
                aiServiceClient,
                persistenceService,
                Executors.newSingleThreadExecutor()
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

        // First row succeeds
        when(researchServiceClient.executeResearch(any()))
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
                ))
                // Second row throws exception
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

        // Wait brief moment for singleThreadExecutor to complete 2 rows
        Thread.sleep(600);

        EnrichmentJobResponse completedJob = enrichmentService.getJob(submitted.jobId()).orElseThrow();
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
}
